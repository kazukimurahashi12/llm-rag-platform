package openai

import (
	"context"
	"math/rand"
	"sync"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

type breakerState string

const (
	breakerClosed   breakerState = "CLOSED"
	breakerOpen     breakerState = "OPEN"
	breakerHalfOpen breakerState = "HALF_OPEN"
)

type resiliencePolicy struct {
	retryMaxAttempts               int
	retryInitialBackoff            time.Duration
	retryMaxBackoff                time.Duration
	circuitBreakerFailureThreshold int
	circuitBreakerMinimumCalls     int
	circuitBreakerWindowSize       int
	circuitBreakerOpenDuration     time.Duration
	circuitBreakerHalfOpenMaxCalls int
}

type circuitBreaker struct {
	mu                 sync.Mutex
	state              breakerState
	openUntil          time.Time
	window             []bool
	halfOpenCalls      int
	halfOpenSuccesses  int
	halfOpenMaxCalls   int
	minimumCalls       int
	windowSize         int
	failurePercentOpen int
	openDuration       time.Duration
}

func newResiliencePolicy(cfg config.OpenAIConfig) resiliencePolicy {
	return resiliencePolicy{
		retryMaxAttempts:               int(maxInt64(cfg.RetryMaxAttempts, 1)),
		retryInitialBackoff:            time.Duration(maxInt64(cfg.RetryInitialBackoffMillis, 1)) * time.Millisecond,
		retryMaxBackoff:                time.Duration(maxInt64(cfg.RetryMaxBackoffMillis, 1)) * time.Millisecond,
		circuitBreakerFailureThreshold: int(maxInt64(cfg.CircuitBreakerFailureThresholdPercent, 1)),
		circuitBreakerMinimumCalls:     int(maxInt64(cfg.CircuitBreakerMinimumCalls, 1)),
		circuitBreakerWindowSize:       int(maxInt64(cfg.CircuitBreakerWindowSize, 1)),
		circuitBreakerOpenDuration:     time.Duration(maxInt64(cfg.CircuitBreakerOpenSeconds, 1)) * time.Second,
		circuitBreakerHalfOpenMaxCalls: int(maxInt64(cfg.CircuitBreakerHalfOpenMaxCalls, 1)),
	}
}

func newCircuitBreaker(policy resiliencePolicy) *circuitBreaker {
	return &circuitBreaker{
		state:              breakerClosed,
		window:             make([]bool, 0, policy.circuitBreakerWindowSize),
		halfOpenMaxCalls:   policy.circuitBreakerHalfOpenMaxCalls,
		minimumCalls:       policy.circuitBreakerMinimumCalls,
		windowSize:         policy.circuitBreakerWindowSize,
		failurePercentOpen: policy.circuitBreakerFailureThreshold,
		openDuration:       policy.circuitBreakerOpenDuration,
	}
}

func (b *circuitBreaker) beforeRequest(now time.Time) error {
	b.mu.Lock()
	defer b.mu.Unlock()

	switch b.state {
	case breakerOpen:
		if now.Before(b.openUntil) {
			return &Error{
				Kind:    ErrorKindCircuit,
				Message: "openai circuit breaker is open",
			}
		}
		b.state = breakerHalfOpen
		b.halfOpenCalls = 0
		b.halfOpenSuccesses = 0
	case breakerHalfOpen:
		if b.halfOpenCalls >= b.halfOpenMaxCalls {
			return &Error{
				Kind:    ErrorKindCircuit,
				Message: "openai circuit breaker is half-open and trial calls are exhausted",
			}
		}
	}

	if b.state == breakerHalfOpen {
		b.halfOpenCalls++
	}

	return nil
}

func (b *circuitBreaker) stateString() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return string(b.state)
}

func (b *circuitBreaker) recordSuccess(now time.Time) {
	b.mu.Lock()
	defer b.mu.Unlock()

	switch b.state {
	case breakerHalfOpen:
		b.halfOpenSuccesses++
		if b.halfOpenSuccesses >= b.halfOpenMaxCalls {
			b.state = breakerClosed
			b.window = b.window[:0]
			b.halfOpenCalls = 0
			b.halfOpenSuccesses = 0
		}
	default:
		b.recordWindow(true)
	}
	_ = now
}

func (b *circuitBreaker) recordFailure(now time.Time) bool {
	b.mu.Lock()
	defer b.mu.Unlock()

	switch b.state {
	case breakerHalfOpen:
		b.state = breakerOpen
		b.openUntil = now.Add(b.openDuration)
		b.halfOpenCalls = 0
		b.halfOpenSuccesses = 0
		return true
	default:
		b.recordWindow(false)
		if len(b.window) >= b.minimumCalls && failureRatePercent(b.window) >= b.failurePercentOpen {
			b.state = breakerOpen
			b.openUntil = now.Add(b.openDuration)
			return true
		}
	}
	return false
}

func (b *circuitBreaker) recordWindow(success bool) {
	b.window = append(b.window, success)
	if len(b.window) > b.windowSize {
		b.window = b.window[len(b.window)-b.windowSize:]
	}
}

func failureRatePercent(window []bool) int {
	if len(window) == 0 {
		return 0
	}
	failures := 0
	for _, success := range window {
		if !success {
			failures++
		}
	}
	return failures * 100 / len(window)
}

func withResilience[T any](ctx context.Context, c *Client, operation string, fn func(context.Context) (T, error)) (T, error) {
	var zero T
	if err := c.breaker.beforeRequest(time.Now()); err != nil {
		c.metrics.failFastCount.Add(1)
		return zero, err
	}

	attempts := c.policy.retryMaxAttempts
	if attempts <= 0 {
		attempts = 1
	}

	var lastErr error
	for attempt := 1; attempt <= attempts; attempt++ {
		result, err := fn(ctx)
		if err == nil {
			c.breaker.recordSuccess(time.Now())
			return result, nil
		}
		lastErr = err
		if openAIError, ok := err.(*Error); ok && openAIError.Kind == ErrorKindTimeout {
			c.metrics.timeouts.Add(1)
		}
		if !isRetryableError(err) {
			if c.breaker.recordFailure(time.Now()) {
				c.metrics.circuitOpenTransitions.Add(1)
			}
			return zero, err
		}
		if attempt == attempts {
			if c.breaker.recordFailure(time.Now()) {
				c.metrics.circuitOpenTransitions.Add(1)
			}
			return zero, err
		}
		c.metrics.retryAttempts.Add(1)
		if err := sleepWithBackoff(ctx, c.policy, attempt, operation); err != nil {
			if c.breaker.recordFailure(time.Now()) {
				c.metrics.circuitOpenTransitions.Add(1)
			}
			return zero, err
		}
	}

	if c.breaker.recordFailure(time.Now()) {
		c.metrics.circuitOpenTransitions.Add(1)
	}
	return zero, lastErr
}

func isRetryableError(err error) bool {
	openAIError, ok := err.(*Error)
	if !ok {
		return false
	}

	switch openAIError.Kind {
	case ErrorKindTimeout, ErrorKindTransport:
		return true
	case ErrorKindUpstream:
		switch openAIError.StatusCode {
		case 429, 502, 503, 504:
			return true
		}
	}
	return false
}

func sleepWithBackoff(ctx context.Context, policy resiliencePolicy, attempt int, _ string) error {
	backoff := policy.retryInitialBackoff
	for i := 1; i < attempt; i++ {
		backoff *= 2
		if backoff >= policy.retryMaxBackoff {
			backoff = policy.retryMaxBackoff
			break
		}
	}
	if backoff > policy.retryMaxBackoff {
		backoff = policy.retryMaxBackoff
	}
	if backoff <= 0 {
		backoff = time.Millisecond
	}

	jitter := time.Duration(rand.Int63n(int64(backoff / 2)))
	wait := backoff/2 + jitter

	timer := time.NewTimer(wait)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return &Error{
			Kind:    ErrorKindTimeout,
			Message: "openai retry wait cancelled",
			Cause:   ctx.Err(),
		}
	case <-timer.C:
		return nil
	}
}

func maxInt64(value int64, fallback int64) int64 {
	if value <= 0 {
		return fallback
	}
	return value
}
