package http

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/advice"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/db"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/prompt"
	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
)

const version = "0.1.0"

// NewServer は Go 版 backend の Echo サーバーを組み立てる。
func NewServer(cfg config.Config) *echo.Echo {
	e := echo.New()
	tokenService := auth.NewTokenService(cfg)
	authService := auth.NewService(cfg, tokenService)
	openAIClient := openai.NewClient(cfg.OpenAI)
	postgres, err := db.NewPostgres(cfg.Database)
	if err != nil {
		panic(err)
	}
	knowledgeRepository := knowledge.NewRepository(postgres.SQLDB())
	retrievalService := knowledge.NewRetrievalService(knowledgeRepository, cfg.RAG, openAIClient)
	promptLoader, err := prompt.NewTemplateLoader(cfg.Prompt.AdviceTemplatePath)
	if err != nil {
		panic(err)
	}
	adviceService := advice.NewService(cfg, retrievalService, openAIClient, promptLoader)

	e.HideBanner = true
	e.Use(middleware.RequestID())
	e.Use(middleware.Recover())
	e.Use(middleware.Logger())

	registerRoutes(e, cfg, postgres)
	RegisterAuthRoutes(e, authService, tokenService)
	RegisterAdviceRoutes(e, tokenService, adviceService)

	return e
}

func registerRoutes(e *echo.Echo, cfg config.Config, postgres *db.Postgres) {
	e.GET("/health", func(c echo.Context) error {
		healthStatus := "UP"
		dbStatus := "UP"

		pingCtx, cancel := context.WithTimeout(c.Request().Context(), 2*time.Second)
		defer cancel()

		if err := postgres.Ping(pingCtx); err != nil {
			healthStatus = "DEGRADED"
			dbStatus = fmt.Sprintf("DOWN: %s", err.Error())
		}

		return c.JSON(http.StatusOK, map[string]string{
			"status": healthStatus,
			"env":    cfg.AppEnv,
			"db":     dbStatus,
		})
	})

	e.GET("/version", func(c echo.Context) error {
		return c.JSON(http.StatusOK, map[string]string{
			"name":    "onboard-guide-api-go",
			"version": version,
		})
	})
}
