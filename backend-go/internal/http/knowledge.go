package http

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/labstack/echo/v4"
)

// RegisterKnowledgeRoutes は knowledge read/create/update API を登録する。
func RegisterKnowledgeRoutes(
	e *echo.Echo,
	tokenService jwtClaimsParser,
	managementService *knowledge.ManagementService,
) {
	knowledgeGroup := e.Group("/v1/knowledge-documents")
	knowledgeGroup.Use(jwtMiddleware(tokenService))
	knowledgeGroup.Use(roleMiddleware("ADMIN", "OPERATOR"))

	knowledgeGroup.GET("", func(c echo.Context) error {
		claims := c.Get("jwtClaims").(*auth.Claims)
		limit, _ := strconv.Atoi(strings.TrimSpace(c.QueryParam("limit")))
		offset, _ := strconv.Atoi(strings.TrimSpace(c.QueryParam("offset")))
		response, err := managementService.GetDocuments(c.Request().Context(), limit, offset, claims.Subject, hasRole(claims.Roles, "ADMIN"))
		if err != nil {
			return c.JSON(http.StatusInternalServerError, map[string]any{
				"status":  http.StatusInternalServerError,
				"message": "failed to list knowledge documents",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, response)
	})

	knowledgeGroup.POST("", func(c echo.Context) error {
		if err := ensureAdmin(c); err != nil {
			return err
		}
		request := api.KnowledgeDocumentCreateRequest{}
		if err := c.Bind(&request); err != nil {
			return writeInvalidRequestBody(c, err)
		}
		if details := validateKnowledgeCreateRequest(request); len(details) > 0 {
			return writeValidationError(c, details)
		}
		response, err := managementService.CreateDocument(c.Request().Context(), request)
		if err != nil {
			return writeError(c, http.StatusInternalServerError, "failed to create knowledge document", []string{err.Error()})
		}
		return c.JSON(http.StatusCreated, response)
	})

	knowledgeGroup.PUT("/:knowledgeDocumentId", func(c echo.Context) error {
		if err := ensureAdmin(c); err != nil {
			return err
		}
		documentID, err := strconv.ParseInt(c.Param("knowledgeDocumentId"), 10, 64)
		if err != nil {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid knowledgeDocumentId",
				"details": []string{err.Error()},
			})
		}

		request := api.KnowledgeDocumentUpdateRequest{}
		if err := c.Bind(&request); err != nil {
			return writeInvalidRequestBody(c, err)
		}
		if details := validateKnowledgeUpdateRequest(request); len(details) > 0 {
			return writeValidationError(c, details)
		}

		response, err := managementService.UpdateDocument(c.Request().Context(), documentID, request)
		if err != nil {
			if errors.Is(err, knowledge.ErrKnowledgeDocumentNotFound) {
				return writeError(c, http.StatusNotFound, "knowledge document not found", []string{})
			}
			return writeError(c, http.StatusInternalServerError, "failed to update knowledge document", []string{err.Error()})
		}
		return c.JSON(http.StatusOK, response)
	})
}

func roleMiddleware(roles ...string) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			claims, ok := c.Get("jwtClaims").(*auth.Claims)
			if !ok || claims == nil {
				return writeError(c, http.StatusUnauthorized, "missing authenticated user", []string{})
			}
			for _, role := range roles {
				if hasRole(claims.Roles, role) {
					return next(c)
				}
			}
			return writeError(c, http.StatusForbidden, "required role is missing", []string{})
		}
	}
}

func ensureAdmin(c echo.Context) error {
	claims, ok := c.Get("jwtClaims").(*auth.Claims)
	if !ok || claims == nil {
		return writeError(c, http.StatusUnauthorized, "missing authenticated user", []string{})
	}
	if !hasRole(claims.Roles, "ADMIN") {
		return writeError(c, http.StatusForbidden, "admin role is required", []string{})
	}
	return nil
}
