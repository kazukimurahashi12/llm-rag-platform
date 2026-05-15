package config

import "os"

// Config は Go 版 backend の起動設定を保持する。
type Config struct {
	AppEnv string
	Port   string
}

// Load は環境変数から起動設定を読み込む。
func Load() Config {
	return Config{
		AppEnv: getEnv("APP_ENV", "local"),
		Port:   getEnv("PORT", "8081"),
	}
}

func getEnv(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}
