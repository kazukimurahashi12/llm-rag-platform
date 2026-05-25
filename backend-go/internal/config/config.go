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
	Database     DatabaseConfig
	OpenAI       OpenAIConfig
	RAG          RAGConfig
	Prompt       PromptConfig
	AdminUser    UserCredential
	OperatorUser UserCredential
}

// DatabaseConfig は PostgreSQL 接続設定を保持する。
type DatabaseConfig struct {
	Host     string
	Port     int64
	Name     string
	Username string
	Password string
	SSLMode  string
}

// OpenAIConfig は最小 advice 実装で使う OpenAI 接続設定を保持する。
type OpenAIConfig struct {
	APIKey         string
	BaseURL        string
	DefaultModel   string
	TimeoutSeconds int64
}

// RAGConfig は Go 版 retrieval の最小設定を保持する。
type RAGConfig struct {
	TopK                               int64
	VectorSearchEnabled                bool
	EmbeddingModel                     string
	EmbeddingDimensions                int64
	MinSimilarityScore                 float64
	GroundednessThreshold              float64
	GroundednessFallbackEnabled        bool
	GroundednessFallbackScoreThreshold float64
}

// PromptConfig は prompt テンプレート設定を保持する。
type PromptConfig struct {
	AdviceTemplatePath       string
	GroundednessTemplatePath string
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
		Database: DatabaseConfig{
			Host:     getEnv("DB_HOST", "localhost"),
			Port:     getEnvInt64("DB_PORT", 5432),
			Name:     getEnv("DB_NAME", "rag_db"),
			Username: getEnv("DB_USERNAME", "postgres"),
			Password: getEnv("DB_PASSWORD", "postgres"),
			SSLMode:  getEnv("DB_SSLMODE", "disable"),
		},
		OpenAI: OpenAIConfig{
			APIKey:         getEnv("OPENAI_API_KEY", ""),
			BaseURL:        getEnv("OPENAI_BASE_URL", "https://api.openai.com/v1"),
			DefaultModel:   getEnv("OPENAI_DEFAULT_MODEL", "gpt-4o-mini"),
			TimeoutSeconds: getEnvInt64("OPENAI_TIMEOUT_SECONDS", 20),
		},
		RAG: RAGConfig{
			TopK:                               getEnvInt64("RAG_TOP_K", 3),
			VectorSearchEnabled:                getEnvBool("RAG_VECTOR_SEARCH_ENABLED", true),
			EmbeddingModel:                     getEnv("RAG_EMBEDDING_MODEL", "text-embedding-3-small"),
			EmbeddingDimensions:                getEnvInt64("RAG_EMBEDDING_DIMENSIONS", 1536),
			MinSimilarityScore:                 getEnvFloat64("RAG_MIN_SIMILARITY_SCORE", 0.4),
			GroundednessThreshold:              getEnvFloat64("RAG_GROUNDEDNESS_THRESHOLD", 0.7),
			GroundednessFallbackEnabled:        getEnvBool("RAG_GROUNDEDNESS_FALLBACK_ENABLED", true),
			GroundednessFallbackScoreThreshold: getEnvFloat64("RAG_GROUNDEDNESS_FALLBACK_SCORE_THRESHOLD", 0.3),
		},
		Prompt: PromptConfig{
			AdviceTemplatePath:       getEnv("ADVICE_PROMPT_TEMPLATE_PATH", "prompts/management-coach-v1.0.txt"),
			GroundednessTemplatePath: getEnv("GROUNDEDNESS_PROMPT_TEMPLATE_PATH", "prompts/groundedness-judge-v1.0.txt"),
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

func getEnvBool(key string, fallback bool) bool {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	if value == "true" || value == "TRUE" || value == "1" {
		return true
	}
	if value == "false" || value == "FALSE" || value == "0" {
		return false
	}

	return fallback
}

func getEnvFloat64(key string, fallback float64) float64 {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	var parsed float64
	_, err := fmt.Sscan(value, &parsed)
	if err != nil {
		return fallback
	}

	return parsed
}
