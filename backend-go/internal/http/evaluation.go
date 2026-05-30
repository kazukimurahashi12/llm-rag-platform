package http

import (
	"net/http"
	"strconv"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/evaluation"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/labstack/echo/v4"
)

// RegisterEvaluationRoutes は管理者向け評価 API を登録する。
func RegisterEvaluationRoutes(
	e *echo.Echo,
	tokenService jwtClaimsParser,
	retrievalEvaluationService *evaluation.RetrievalEvaluationService,
	promptInjectionEvaluationService *evaluation.PromptInjectionEvaluationService,
	groundednessCaseEvaluationService *evaluation.GroundednessCaseEvaluationService,
) {
	retrievalGroup := e.Group("/v1/retrieval-evaluations")
	retrievalGroup.Use(jwtMiddleware(tokenService))
	retrievalGroup.Use(adminMiddleware())

	retrievalGroup.GET("/default", func(c echo.Context) error {
		var topK *int
		if value := c.QueryParam("topK"); value != "" {
			parsed, err := strconv.Atoi(value)
			if err != nil {
				return c.JSON(http.StatusBadRequest, map[string]any{
					"status":  http.StatusBadRequest,
					"message": "invalid topK",
					"details": []string{err.Error()},
				})
			}
			topK = &parsed
		}
		response, err := retrievalEvaluationService.EvaluateDefaultCases(c.Request().Context(), topK)
		if err != nil {
			return c.JSON(http.StatusInternalServerError, map[string]any{
				"status":  http.StatusInternalServerError,
				"message": "failed to load default retrieval cases",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, response)
	})

	retrievalGroup.POST("", func(c echo.Context) error {
		request := api.RetrievalEvaluationRequest{}
		if err := c.Bind(&request); err != nil {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, retrievalEvaluationService.Evaluate(c.Request().Context(), request, knowledge.RetrievalOptions{}))
	})

	retrievalGroup.POST("/comparisons", func(c echo.Context) error {
		request := api.RetrievalEvaluationComparisonRequest{}
		if err := c.Bind(&request); err != nil {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}
		response, err := retrievalEvaluationService.CompareDefaultCases(c.Request().Context(), request)
		if err != nil {
			return c.JSON(http.StatusInternalServerError, map[string]any{
				"status":  http.StatusInternalServerError,
				"message": "failed to compare retrieval evaluations",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, response)
	})

	evaluationGroup := e.Group("/v1/prompt-injection-evaluations")
	evaluationGroup.Use(jwtMiddleware(tokenService))
	evaluationGroup.Use(adminMiddleware())

	evaluationGroup.GET("/default", func(c echo.Context) error {
		response, err := promptInjectionEvaluationService.EvaluateDefaultCases()
		if err != nil {
			return c.JSON(http.StatusInternalServerError, map[string]any{
				"status":  http.StatusInternalServerError,
				"message": "failed to load default prompt injection cases",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, response)
	})

	evaluationGroup.POST("", func(c echo.Context) error {
		request := api.PromptInjectionEvaluationRequest{}
		if err := c.Bind(&request); err != nil {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, promptInjectionEvaluationService.Evaluate(request))
	})

	groundednessGroup := e.Group("/v1/groundedness-evaluations")
	groundednessGroup.Use(jwtMiddleware(tokenService))
	groundednessGroup.Use(adminMiddleware())

	groundednessGroup.GET("/default", func(c echo.Context) error {
		response, err := groundednessCaseEvaluationService.EvaluateDefaultCases(c.Request().Context())
		if err != nil {
			return c.JSON(http.StatusInternalServerError, map[string]any{
				"status":  http.StatusInternalServerError,
				"message": "failed to load default groundedness cases",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, response)
	})

	groundednessGroup.POST("", func(c echo.Context) error {
		request := api.GroundednessCaseEvaluationRequest{}
		if err := c.Bind(&request); err != nil {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, groundednessCaseEvaluationService.Evaluate(c.Request().Context(), request))
	})
}
