package openai

import "sync/atomic"

// MetricsSnapshot は OpenAI resilience の現時点スナップショットを表す。
type MetricsSnapshot struct {
	RetryAttempts          float64
	Timeouts               float64
	FailFastCount          float64
	CircuitOpenTransitions float64
	CircuitState           string
}

type metrics struct {
	retryAttempts          atomic.Int64
	timeouts               atomic.Int64
	failFastCount          atomic.Int64
	circuitOpenTransitions atomic.Int64
}

func (m *metrics) snapshot(state string) MetricsSnapshot {
	return MetricsSnapshot{
		RetryAttempts:          float64(m.retryAttempts.Load()),
		Timeouts:               float64(m.timeouts.Load()),
		FailFastCount:          float64(m.failFastCount.Load()),
		CircuitOpenTransitions: float64(m.circuitOpenTransitions.Load()),
		CircuitState:           state,
	}
}
