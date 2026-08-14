package openai

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

// Client は OpenAI Responses API の最小クライアント。
type Client struct {
	baseURL      string
	apiKey       string
	defaultModel string
	httpClient   *http.Client
	policy       resiliencePolicy
	breaker      *circuitBreaker
	metrics      *metrics
}

type chatRequest struct {
	Model        string `json:"model"`
	Instructions string `json:"instructions"`
	Input        []struct {
		Role    string `json:"role"`
		Content []struct {
			Type string `json:"type"`
			Text string `json:"text"`
		} `json:"content"`
	} `json:"input"`
}

type chatResponse struct {
	Output []struct {
		Content []struct {
			Text string `json:"text"`
		} `json:"content"`
	} `json:"output"`
	OutputText string `json:"output_text"`
	Usage      struct {
		InputTokens  int `json:"input_tokens"`
		OutputTokens int `json:"output_tokens"`
	} `json:"usage"`
}

type embeddingRequest struct {
	Model string `json:"model"`
	Input string `json:"input"`
}

type embeddingResponse struct {
	Data []struct {
		Embedding []float64 `json:"embedding"`
	} `json:"data"`
}

// ChatResult は advice 生成に必要な最小結果を保持する。
type ChatResult struct {
	Content          string
	Model            string
	PromptTokens     int
	CompletionTokens int
}

// NewClient は OpenAI 接続設定からクライアントを生成する。
func NewClient(cfg config.OpenAIConfig) *Client {
	policy := newResiliencePolicy(cfg)
	transport := &http.Transport{
		DialContext: (&net.Dialer{
			Timeout: time.Duration(cfg.ConnectTimeoutSeconds) * time.Second,
		}).DialContext,
		ResponseHeaderTimeout: time.Duration(cfg.ReadTimeoutSeconds) * time.Second,
		TLSHandshakeTimeout:   time.Duration(cfg.ConnectTimeoutSeconds) * time.Second,
	}
	return &Client{
		baseURL:      strings.TrimRight(cfg.BaseURL, "/"),
		apiKey:       cfg.APIKey,
		defaultModel: cfg.DefaultModel,
		httpClient: &http.Client{
			Timeout:   time.Duration(cfg.TimeoutSeconds) * time.Second,
			Transport: transport,
		},
		policy:  policy,
		breaker: newCircuitBreaker(policy),
		metrics: &metrics{},
	}
}

// MetricsSnapshot は OpenAI resilience 指標の現在値を返す。
func (c *Client) MetricsSnapshot() MetricsSnapshot {
	return c.metrics.snapshot(c.breaker.stateString())
}

// Chat は Responses API を呼び出してテキスト応答を返す。
func (c *Client) Chat(ctx context.Context, model string, instructions string, userMessage string) (*ChatResult, error) {
	return withResilience(ctx, c, "chat", func(callCtx context.Context) (*ChatResult, error) {
		return c.doChat(callCtx, model, instructions, userMessage)
	})
}

func (c *Client) doChat(ctx context.Context, model string, instructions string, userMessage string) (*ChatResult, error) {
	if strings.TrimSpace(c.apiKey) == "" {
		return nil, &Error{
			Kind:    ErrorKindConfig,
			Message: "openai api key is not configured",
		}
	}

	selectedModel := model
	if strings.TrimSpace(selectedModel) == "" {
		selectedModel = c.defaultModel
	}

	requestPayload := chatRequest{
		Model:        selectedModel,
		Instructions: instructions,
	}
	requestPayload.Input = []struct {
		Role    string `json:"role"`
		Content []struct {
			Type string `json:"type"`
			Text string `json:"text"`
		} `json:"content"`
	}{
		{
			Role: "user",
			Content: []struct {
				Type string `json:"type"`
				Text string `json:"text"`
			}{
				{
					Type: "input_text",
					Text: userMessage,
				},
			},
		},
	}

	body, err := json.Marshal(requestPayload)
	if err != nil {
		return nil, err
	}

	request, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/responses", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Authorization", "Bearer "+c.apiKey)
	request.Header.Set("Content-Type", "application/json")

	response, err := c.httpClient.Do(request)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			return nil, &Error{
				Kind:    ErrorKindTimeout,
				Message: "openai request timed out",
				Cause:   err,
			}
		}

		var netErr net.Error
		if errors.As(err, &netErr) && netErr.Timeout() {
			return nil, &Error{
				Kind:    ErrorKindTimeout,
				Message: "openai request timed out",
				Cause:   err,
			}
		}

		return nil, &Error{
			Kind:    ErrorKindTransport,
			Message: "failed to reach openai",
			Cause:   err,
		}
	}
	defer func() {
		_ = response.Body.Close()
	}()

	responseBody, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, &Error{
			Kind:    ErrorKindDecode,
			Message: "failed to read openai response body",
			Cause:   err,
		}
	}

	if response.StatusCode >= 400 {
		return nil, &Error{
			Kind:       ErrorKindUpstream,
			StatusCode: response.StatusCode,
			Message:    string(responseBody),
		}
	}

	parsed := chatResponse{}
	if err := json.Unmarshal(responseBody, &parsed); err != nil {
		return nil, &Error{
			Kind:    ErrorKindDecode,
			Message: "failed to decode openai response body",
			Cause:   err,
		}
	}

	content := extractContent(parsed)
	if strings.TrimSpace(content) == "" {
		return nil, &Error{
			Kind:    ErrorKindResponse,
			Message: "openai response did not contain advice text",
		}
	}

	return &ChatResult{
		Content:          content,
		Model:            selectedModel,
		PromptTokens:     parsed.Usage.InputTokens,
		CompletionTokens: parsed.Usage.OutputTokens,
	}, nil
}

func extractContent(response chatResponse) string {
	for _, output := range response.Output {
		for _, content := range output.Content {
			if strings.TrimSpace(content.Text) != "" {
				return content.Text
			}
		}
	}

	return response.OutputText
}

// Embed は Embeddings API を呼び出して query embedding を返す。
func (c *Client) Embed(ctx context.Context, model string, input string, expectedDimensions int64) ([]float64, error) {
	return withResilience(ctx, c, "embed", func(callCtx context.Context) ([]float64, error) {
		return c.doEmbed(callCtx, model, input, expectedDimensions)
	})
}

func (c *Client) doEmbed(ctx context.Context, model string, input string, expectedDimensions int64) ([]float64, error) {
	if strings.TrimSpace(c.apiKey) == "" {
		return nil, &Error{
			Kind:    ErrorKindConfig,
			Message: "openai api key is not configured",
		}
	}

	selectedModel := model
	if strings.TrimSpace(selectedModel) == "" {
		selectedModel = "text-embedding-3-small"
	}

	body, err := json.Marshal(embeddingRequest{
		Model: selectedModel,
		Input: input,
	})
	if err != nil {
		return nil, err
	}

	request, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/embeddings", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Authorization", "Bearer "+c.apiKey)
	request.Header.Set("Content-Type", "application/json")

	response, err := c.httpClient.Do(request)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			return nil, &Error{Kind: ErrorKindTimeout, Message: "openai embedding request timed out", Cause: err}
		}
		var netErr net.Error
		if errors.As(err, &netErr) && netErr.Timeout() {
			return nil, &Error{Kind: ErrorKindTimeout, Message: "openai embedding request timed out", Cause: err}
		}
		return nil, &Error{Kind: ErrorKindTransport, Message: "failed to reach openai embeddings api", Cause: err}
	}
	defer func() {
		_ = response.Body.Close()
	}()

	responseBody, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, &Error{Kind: ErrorKindDecode, Message: "failed to read openai embedding response body", Cause: err}
	}

	if response.StatusCode >= 400 {
		return nil, &Error{
			Kind:       ErrorKindUpstream,
			StatusCode: response.StatusCode,
			Message:    string(responseBody),
		}
	}

	parsed := embeddingResponse{}
	if err := json.Unmarshal(responseBody, &parsed); err != nil {
		return nil, &Error{Kind: ErrorKindDecode, Message: "failed to decode openai embedding response body", Cause: err}
	}

	if len(parsed.Data) == 0 || len(parsed.Data[0].Embedding) == 0 {
		return nil, &Error{Kind: ErrorKindResponse, Message: "openai embedding response did not contain embedding vector"}
	}

	if expectedDimensions > 0 && int64(len(parsed.Data[0].Embedding)) != expectedDimensions {
		return nil, &Error{
			Kind:    ErrorKindResponse,
			Message: fmt.Sprintf("unexpected embedding dimension: expected %d but got %d", expectedDimensions, len(parsed.Data[0].Embedding)),
		}
	}

	return parsed.Data[0].Embedding, nil
}
