package http

import (
	"net/http"

	"github.com/labstack/echo/v4"
)

func writeError(c echo.Context, status int, message string, details []string) error {
	if details == nil {
		details = []string{}
	}
	return c.JSON(status, map[string]any{
		"status":  status,
		"message": message,
		"details": details,
	})
}

func writeValidationError(c echo.Context, details []string) error {
	if len(details) == 0 {
		details = []string{"request validation failed"}
	}
	return writeError(c, http.StatusBadRequest, "Validation error", details)
}

func writeInvalidRequestBody(c echo.Context, err error) error {
	return writeError(c, http.StatusBadRequest, "invalid request body", []string{err.Error()})
}
