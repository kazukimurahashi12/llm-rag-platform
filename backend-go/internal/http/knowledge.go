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
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}
		response, err := managementService.CreateDocument(c.Request().Context(), request)
		if err != nil {
			return c.JSON(http.StatusInternalServerError, map[string]any{
				"status":  http.StatusInternalServerError,
				"message": "failed to create knowledge document",
				"details": []string{err.Error()},
			})
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
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}

		response, err := managementService.UpdateDocument(c.Request().Context(), documentID, request)
		if err != nil {
			if errors.Is(err, knowledge.ErrKnowledgeDocumentNotFound) {
				return c.JSON(http.StatusNotFound, map[string]any{
					"status":  http.StatusNotFound,
					"message": "knowledge document not found",
					"details": []string{},
				})
			}
			return c.JSON(http.StatusInternalServerError, map[string]any{
				"status":  http.StatusInternalServerError,
				"message": "failed to update knowledge document",
				"details": []string{err.Error()},
			})
		}
		return c.JSON(http.StatusOK, response)
	})
}

func roleMiddleware(roles ...string) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			claims, ok := c.Get("jwtClaims").(*auth.Claims)
			if !ok || claims == nil {
				return c.JSON(http.StatusUnauthorized, map[string]any{
					"status":  http.StatusUnauthorized,
					"message": "missing authenticated user",
					"details": []string{},
				})
			}
			for _, role := range roles {
				if hasRole(claims.Roles, role) {
					return next(c)
				}
			}
			return c.JSON(http.StatusForbidden, map[string]any{
				"status":  http.StatusForbidden,
				"message": "required role is missing",
				"details": []string{},
			})
		}
	}
}

func ensureAdmin(c echo.Context) error {
	claims, ok := c.Get("jwtClaims").(*auth.Claims)
	if !ok || claims == nil {
		return c.JSON(http.StatusUnauthorized, map[string]any{
			"status":  http.StatusUnauthorized,
			"message": "missing authenticated user",
			"details": []string{},
		})
	}
	if !hasRole(claims.Roles, "ADMIN") {
		return c.JSON(http.StatusForbidden, map[string]any{
			"status":  http.StatusForbidden,
			"message": "admin role is required",
			"details": []string{},
		})
	}
	return nil
}
