package http

import (
	"net/http"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/advice"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/labstack/echo/v4"
)

// RegisterAdviceRoutes は Go 版 advice API の空実装を登録する。
func RegisterAdviceRoutes(e *echo.Echo, tokenService jwtClaimsParser, adviceService *advice.Service) {
	adviceGroup := e.Group("/v1/management")
	adviceGroup.Use(jwtMiddleware(tokenService))

	adviceGroup.POST("/advice", func(c echo.Context) error {
		request := api.AdviceRequest{}
		if err := c.Bind(&request); err != nil {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}

		if strings.TrimSpace(request.MemberContext.Situation) == "" || strings.TrimSpace(request.MemberContext.TargetGoal) == "" {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "memberContext.situation and memberContext.targetGoal are required",
				"details": []string{},
			})
		}

		response, err := adviceService.GenerateAdvice(c.Request().Context(), request)
		if err != nil {
			return c.JSON(http.StatusBadGateway, map[string]any{
				"status":  http.StatusBadGateway,
				"message": err.Error(),
				"details": []string{},
			})
		}

		return c.JSON(http.StatusOK, response)
	})
}
