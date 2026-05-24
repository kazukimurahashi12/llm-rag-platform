package http

import (
	"errors"
	"net/http"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/advice"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
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

		claims := c.Get("jwtClaims").(*auth.Claims)
		response, err := adviceService.GenerateAdvice(c.Request().Context(), advice.Actor{
			Username: claims.Subject,
			Roles:    claims.Roles,
		}, request)
		if err != nil {
			statusCode := http.StatusBadGateway
			details := []string{}

			var openAIError *openai.Error
			if errors.As(err, &openAIError) {
				details = append(details, openAIError.Error())

				switch openAIError.Kind {
				case openai.ErrorKindConfig:
					statusCode = http.StatusInternalServerError
				case openai.ErrorKindTimeout:
					statusCode = http.StatusGatewayTimeout
				case openai.ErrorKindTransport, openai.ErrorKindUpstream:
					statusCode = http.StatusServiceUnavailable
				}
			} else {
				details = append(details, err.Error())
			}

			return c.JSON(statusCode, map[string]any{
				"status":  statusCode,
				"message": "failed to generate advice in backend-go",
				"details": details,
			})
		}

		return c.JSON(http.StatusOK, response)
	})
}
