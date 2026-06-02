package http

import (
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/audit"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/labstack/echo/v4"
)

// RegisterAuditRoutes は audit log API を登録する。
func RegisterAuditRoutes(e *echo.Echo, tokenService jwtClaimsParser, auditService *audit.Service) {
	auditGroup := e.Group("/v1/audit-logs")
	auditGroup.Use(jwtMiddleware(tokenService))
	auditGroup.Use(roleMiddleware("ADMIN", "OPERATOR"))

	auditGroup.GET("", func(c echo.Context) error {
		limit, _ := strconv.Atoi(c.QueryParam("limit"))
		offset, _ := strconv.Atoi(c.QueryParam("offset"))
		model := c.QueryParam("model")
		from, err := parseOptionalTime(c.QueryParam("from"))
		if err != nil {
			return writeTimeParseError(c, "from", err)
		}
		to, err := parseOptionalTime(c.QueryParam("to"))
		if err != nil {
			return writeTimeParseError(c, "to", err)
		}
		response, err := auditService.GetLogs(c.Request().Context(), limit, offset, model, from, to)
		if err != nil {
			return writeError(c, http.StatusInternalServerError, "failed to list audit logs", []string{err.Error()})
		}
		return c.JSON(http.StatusOK, response)
	})

	auditGroup.GET("/:auditLogId", func(c echo.Context) error {
		id, err := strconv.ParseInt(c.Param("auditLogId"), 10, 64)
		if err != nil {
			return writeError(c, http.StatusBadRequest, "invalid auditLogId", []string{err.Error()})
		}
		claims := c.Get("jwtClaims").(*auth.Claims)
		response, err := auditService.GetLogDetail(c.Request().Context(), id, hasRole(claims.Roles, "ADMIN"))
		if err != nil {
			if errors.Is(err, audit.ErrAuditLogNotFound) {
				return writeError(c, http.StatusNotFound, "audit log not found", []string{})
			}
			return writeError(c, http.StatusInternalServerError, "failed to get audit log detail", []string{err.Error()})
		}
		return c.JSON(http.StatusOK, response)
	})
}

func parseOptionalTime(value string) (*time.Time, error) {
	if value == "" {
		return nil, nil
	}
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil {
		return nil, err
	}
	return &parsed, nil
}

func writeTimeParseError(c echo.Context, field string, err error) error {
	return writeError(c, http.StatusBadRequest, "invalid "+field, []string{err.Error()})
}
