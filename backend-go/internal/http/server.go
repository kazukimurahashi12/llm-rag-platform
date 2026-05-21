package http

import (
	"net/http"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/advice"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
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
	promptLoader, err := prompt.NewTemplateLoader(cfg.Prompt.AdviceTemplatePath)
	if err != nil {
		panic(err)
	}
	adviceService := advice.NewService(cfg, openAIClient, promptLoader)

	e.HideBanner = true
	e.Use(middleware.RequestID())
	e.Use(middleware.Recover())
	e.Use(middleware.Logger())

	registerRoutes(e, cfg)
	RegisterAuthRoutes(e, authService, tokenService)
	RegisterAdviceRoutes(e, tokenService, adviceService)

	return e
}

func registerRoutes(e *echo.Echo, cfg config.Config) {
	e.GET("/health", func(c echo.Context) error {
		return c.JSON(http.StatusOK, map[string]string{
			"status": "UP",
			"env":    cfg.AppEnv,
		})
	})

	e.GET("/version", func(c echo.Context) error {
		return c.JSON(http.StatusOK, map[string]string{
			"name":    "onboard-guide-api-go",
			"version": version,
		})
	})
}
