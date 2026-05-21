package config

import (
	"fmt"
	"os"
)

// Config は Go 版 backend の起動設定を保持する。
type Config struct {
	AppEnv       string
	Port         string
	JWTSecret    string
	JWTIssuer    string
	JWTExpiresIn int64
	OpenAI       OpenAIConfig
	Prompt       PromptConfig
	AdminUser    UserCredential
	OperatorUser UserCredential
}

// OpenAIConfig は最小 advice 実装で使う OpenAI 接続設定を保持する。
type OpenAIConfig struct {
	APIKey         string
	BaseURL        string
	DefaultModel   string
	TimeoutSeconds int64
}

// PromptConfig は prompt テンプレート設定を保持する。
type PromptConfig struct {
	AdviceTemplatePath string
}

// UserCredential は最小認証で使う固定ユーザー設定を保持する。
type UserCredential struct {
	Username string
	Password string
	Roles    []string
}

// Load は環境変数から起動設定を読み込む。
func Load() Config {
	return Config{
		AppEnv:       getEnv("APP_ENV", "local"),
		Port:         getEnv("PORT", "8081"),
		JWTSecret:    getEnv("APP_JWT_SECRET", "change-this-jwt-secret-in-production-at-least-32-bytes"),
		JWTIssuer:    getEnv("APP_JWT_ISSUER", "llm-rag-platform"),
		JWTExpiresIn: getEnvInt64("APP_JWT_EXPIRATION_SECONDS", 3600),
		OpenAI: OpenAIConfig{
			APIKey:         getEnv("OPENAI_API_KEY", ""),
			BaseURL:        getEnv("OPENAI_BASE_URL", "https://api.openai.com/v1"),
			DefaultModel:   getEnv("OPENAI_DEFAULT_MODEL", "gpt-4o-mini"),
			TimeoutSeconds: getEnvInt64("OPENAI_TIMEOUT_SECONDS", 20),
		},
		Prompt: PromptConfig{
			AdviceTemplatePath: getEnv("ADVICE_PROMPT_TEMPLATE_PATH", "prompts/management-coach-v1.0.txt"),
		},
		AdminUser: UserCredential{
			Username: getEnv("AUDIT_ADMIN_USERNAME", "admin"),
			Password: getEnv("AUDIT_ADMIN_PASSWORD", "change-me"),
			Roles:    []string{"ADMIN"},
		},
		OperatorUser: UserCredential{
			Username: getEnv("AUDIT_OPERATOR_USERNAME", "operator"),
			Password: getEnv("AUDIT_OPERATOR_PASSWORD", "change-operator"),
			Roles:    []string{"OPERATOR"},
		},
	}
}

func getEnv(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func getEnvInt64(key string, fallback int64) int64 {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	var parsed int64
	_, err := fmt.Sscan(value, &parsed)
	if err != nil {
		return fallback
	}

	return parsed
}
