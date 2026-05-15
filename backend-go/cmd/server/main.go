package main

import (
	"log"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	apphttp "github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/http"
)

func main() {
	cfg := config.Load()
	server := apphttp.NewServer(cfg)

	log.Printf("starting backend-go on :%s (env=%s)", cfg.Port, cfg.AppEnv)
	if err := server.Start(":" + cfg.Port); err != nil {
		log.Fatal(err)
	}
}
