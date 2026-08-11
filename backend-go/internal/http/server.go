package http

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/advice"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/agent"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/audit"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/dashboard"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/db"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/evaluation"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/guard"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/pii"
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
	agentClient := agent.NewClient(cfg.AgentRuntime)
	openAIClient := openai.NewClient(cfg.OpenAI)
	postgres, err := db.NewPostgres(cfg.Database)
	if err != nil {
		panic(err)
	}
	if err := postgres.Migrate(context.Background()); err != nil {
		panic(err)
	}
	knowledgeRepository := knowledge.NewRepository(postgres.SQLDB())
	retrievalMetrics := knowledge.NewRetrievalMetrics()
	auditRepository := audit.NewRepository(postgres.SQLDB())
	piiMaskingService := pii.NewMaskingService()
	auditService := audit.NewService(auditRepository, piiMaskingService)
	retrievalService := knowledge.NewRetrievalService(knowledgeRepository, cfg.RAG, openAIClient, retrievalMetrics)
	knowledgeManagementService := knowledge.NewManagementService(knowledgeRepository, cfg.RAG, openAIClient)
	reindexJobService := knowledge.NewReindexJobService(knowledgeManagementService, postgres.SQLDB(), cfg.ReindexJobs)
	dashboardService := dashboard.NewService(postgres.SQLDB(), auditRepository, reindexJobService, retrievalService, openAIClient)
	promptInjectionGuardService := guard.NewPromptInjectionGuardService()
	promptLoader, err := prompt.NewTemplateLoader(cfg.Prompt.AdviceTemplatePath)
	if err != nil {
		panic(err)
	}
	groundednessPromptLoader, err := prompt.NewTemplateLoader(cfg.Prompt.GroundednessTemplatePath)
	if err != nil {
		panic(err)
	}
	adviceService := advice.NewService(cfg, retrievalService, openAIClient, promptLoader, groundednessPromptLoader, auditService)
	retrievalEvaluationService := evaluation.NewRetrievalEvaluationService(retrievalService)
	promptInjectionEvaluationService := evaluation.NewPromptInjectionEvaluationService(promptInjectionGuardService)
	groundednessCaseEvaluationService := evaluation.NewGroundednessCaseEvaluationService(
		cfg.RAG,
		openAIClient,
		groundednessPromptLoader,
		cfg.OpenAI.DefaultModel,
	)
	reindexJobService.StartBackgroundMaintenance(context.Background())

	e.HideBanner = true
	e.Use(middleware.RequestID())
	e.Use(middleware.Recover())
	e.Use(middleware.Logger())
	e.Use(middleware.CORSWithConfig(middleware.CORSConfig{
		AllowOrigins: []string{
			"http://localhost:5173",
		},
		AllowMethods: []string{
			http.MethodGet,
			http.MethodPost,
			http.MethodPut,
			http.MethodDelete,
			http.MethodOptions,
		},
		AllowHeaders: []string{
			echo.HeaderOrigin,
			echo.HeaderContentType,
			echo.HeaderAccept,
			echo.HeaderAuthorization,
		},
	}))

	registerRoutes(e, cfg, postgres)
	RegisterAuthRoutes(e, authService, tokenService)
	RegisterAgentRoutes(e, tokenService, agentClient)
	RegisterAdviceRoutes(e, tokenService, adviceService, promptInjectionGuardService)
	RegisterKnowledgeRoutes(e, tokenService, knowledgeManagementService)
	RegisterReindexRoutes(e, tokenService, reindexJobService)
	RegisterAuditRoutes(e, tokenService, auditService)
	RegisterDashboardRoutes(e, tokenService, dashboardService)
	RegisterEvaluationRoutes(e, tokenService, retrievalEvaluationService, promptInjectionEvaluationService, groundednessCaseEvaluationService)
	RegisterMetricsRoutes(e, openAIClient, retrievalService, reindexJobService)

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
