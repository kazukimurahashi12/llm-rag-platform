package agent

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

// Client は OpenAI Agents SDK sidecar へ task を委譲する。
type Client struct {
	baseURL    string
	maxTurns   int64
	httpClient *http.Client
}

type runtimeTaskRequest struct {
	Input         string `json:"input"`
	Authorization string `json:"authorization"`
	MaxTurns      *int   `json:"maxTurns,omitempty"`
}

// NewClient は agent runtime client を生成する。
func NewClient(cfg config.AgentRuntimeConfig) *Client {
	return &Client{
		baseURL:  strings.TrimRight(cfg.BaseURL, "/"),
		maxTurns: cfg.MaxTurns,
		httpClient: &http.Client{
			Timeout: time.Duration(cfg.TimeoutSeconds) * time.Second,
		},
	}
}

// RunTask は sidecar の /agent/tasks を呼び出す。
func (c *Client) RunTask(ctx context.Context, request api.AgentTaskRequest, authorization string) (*api.AgentTaskResponse, error) {
	maxTurns := request.MaxTurns
	if maxTurns == nil && c.maxTurns > 0 {
		value := int(c.maxTurns)
		maxTurns = &value
	}

	payload, err := json.Marshal(runtimeTaskRequest{
		Input:         request.Input,
		Authorization: authorization,
		MaxTurns:      maxTurns,
	})
	if err != nil {
		return nil, err
	}

	httpRequest, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/agent/tasks", bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	httpRequest.Header.Set("Content-Type", "application/json")
	httpRequest.Header.Set("Accept", "application/json")

	response, err := c.httpClient.Do(httpRequest)
	if err != nil {
		return nil, fmt.Errorf("failed to reach agent runtime: %w", err)
	}
	defer response.Body.Close()

	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read agent runtime response: %w", err)
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, fmt.Errorf("agent runtime returned %d: %s", response.StatusCode, string(body))
	}

	result := api.AgentTaskResponse{}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("failed to decode agent runtime response: %w", err)
	}
	return &result, nil
}
