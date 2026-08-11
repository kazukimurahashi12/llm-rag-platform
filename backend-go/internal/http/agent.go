package http

import (
	"net/http"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/agent"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/labstack/echo/v4"
)

// RegisterAgentRoutes は OpenAI Agents SDK sidecar への bridge API を登録する。
func RegisterAgentRoutes(e *echo.Echo, tokenService jwtClaimsParser, agentClient *agent.Client) {
	agentGroup := e.Group("/v1/agent")
	agentGroup.Use(jwtMiddleware(tokenService))
	agentGroup.Use(roleMiddleware("ADMIN", "OPERATOR"))

	agentGroup.POST("/tasks", func(c echo.Context) error {
		request := api.AgentTaskRequest{}
		if err := c.Bind(&request); err != nil {
			return writeInvalidRequestBody(c, err)
		}
		if details := validateAgentTaskRequest(request); len(details) > 0 {
			return writeValidationError(c, details)
		}

		response, err := agentClient.RunTask(c.Request().Context(), request, c.Request().Header.Get("Authorization"))
		if err != nil {
			return writeError(c, http.StatusBadGateway, "failed to run agent task", []string{err.Error()})
		}
		return c.JSON(http.StatusOK, response)
	})
}

func validateAgentTaskRequest(request api.AgentTaskRequest) []string {
	details := []string{}
	if strings.TrimSpace(request.Input) == "" {
		details = append(details, "input is required")
	}
	if request.MaxTurns != nil && (*request.MaxTurns < 1 || *request.MaxTurns > 10) {
		details = append(details, "maxTurns must be between 1 and 10")
	}
	return details
}
