package openai

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
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

// ChatResult は advice 生成に必要な最小結果を保持する。
type ChatResult struct {
	Content          string
	Model            string
	PromptTokens     int
	CompletionTokens int
}

// NewClient は OpenAI 接続設定からクライアントを生成する。
func NewClient(cfg config.OpenAIConfig) *Client {
	return &Client{
		baseURL:      strings.TrimRight(cfg.BaseURL, "/"),
		apiKey:       cfg.APIKey,
		defaultModel: cfg.DefaultModel,
		httpClient: &http.Client{
			Timeout: time.Duration(cfg.TimeoutSeconds) * time.Second,
		},
	}
}

// Chat は Responses API を呼び出してテキスト応答を返す。
func (c *Client) Chat(ctx context.Context, model string, instructions string, userMessage string) (*ChatResult, error) {
	if strings.TrimSpace(c.apiKey) == "" {
		return nil, fmt.Errorf("openai api key is not configured")
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
		return nil, err
	}
	defer response.Body.Close()

	responseBody, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}

	if response.StatusCode >= 400 {
		return nil, fmt.Errorf("openai responses api returned status %d: %s", response.StatusCode, string(responseBody))
	}

	parsed := chatResponse{}
	if err := json.Unmarshal(responseBody, &parsed); err != nil {
		return nil, err
	}

	content := extractContent(parsed)
	if strings.TrimSpace(content) == "" {
		return nil, fmt.Errorf("openai response did not contain advice text")
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
