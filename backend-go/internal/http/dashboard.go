package http

import (
	"net/http"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/dashboard"
	"github.com/labstack/echo/v4"
)

// RegisterDashboardRoutes は dashboard summary API を登録する。
func RegisterDashboardRoutes(e *echo.Echo, tokenService jwtClaimsParser, dashboardService *dashboard.Service) {
	dashboardGroup := e.Group("/v1/dashboard")
	dashboardGroup.Use(jwtMiddleware(tokenService))
	dashboardGroup.Use(roleMiddleware("ADMIN", "OPERATOR"))

	dashboardGroup.GET("/summary", func(c echo.Context) error {
		response, err := dashboardService.GetSummary(c.Request().Context())
		if err != nil {
			return writeError(c, http.StatusInternalServerError, "failed to build dashboard summary", []string{err.Error()})
		}
		return c.JSON(http.StatusOK, response)
	})
}
