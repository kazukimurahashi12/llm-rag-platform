package http

import (
	"net/http"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/evaluation"
	"github.com/labstack/echo/v4"
)

// RegisterEvaluationRoutes は管理者向け評価 API を登録する。
func RegisterEvaluationRoutes(
	e *echo.Echo,
	tokenService jwtClaimsParser,
	promptInjectionEvaluationService *evaluation.PromptInjectionEvaluationService,
	groundednessCaseEvaluationService *evaluation.GroundednessCaseEvaluationService,
) {
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
