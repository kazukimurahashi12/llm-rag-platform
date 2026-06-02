package http

import (
	"net/http"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/labstack/echo/v4"
)

type jwtClaimsParser interface {
	ParseAndValidate(token string) (*auth.Claims, error)
}

// RegisterAuthRoutes は JWT 認証の最小 endpoint を登録する。
func RegisterAuthRoutes(e *echo.Echo, authService *auth.Service, tokenService *auth.TokenService) {
	e.POST("/v1/auth/token", func(c echo.Context) error {
		request := api.AuthTokenRequest{}
		if err := c.Bind(&request); err != nil {
			return writeInvalidRequestBody(c, err)
		}
		if details := validateAuthTokenRequest(request); len(details) > 0 {
			return writeValidationError(c, details)
		}

		response, err := authService.IssueToken(request.Username, request.Password)
		if err != nil {
			return writeError(c, http.StatusUnauthorized, err.Error(), []string{})
		}

		return c.JSON(http.StatusOK, response)
	})

	authGroup := e.Group("/v1/auth")
	authGroup.Use(jwtMiddleware(tokenService))

	authGroup.GET("/me", func(c echo.Context) error {
		claims := c.Get("jwtClaims").(*auth.Claims)
		return c.JSON(http.StatusOK, map[string]any{
			"username":  claims.Subject,
			"roles":     claims.Roles,
			"issuer":    claims.Issuer,
			"expiresAt": claims.ExpiresAt,
		})
	})
}

func jwtMiddleware(tokenService jwtClaimsParser) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			authorization := c.Request().Header.Get("Authorization")
			if !strings.HasPrefix(authorization, "Bearer ") {
				return writeError(c, http.StatusUnauthorized, "missing bearer token", []string{})
			}

			token := strings.TrimSpace(strings.TrimPrefix(authorization, "Bearer "))
			claims, err := tokenService.ParseAndValidate(token)
			if err != nil {
				return writeError(c, http.StatusUnauthorized, "invalid bearer token", []string{err.Error()})
			}

			c.Set("jwtClaims", claims)
			return next(c)
		}
	}
}

func adminMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			claims, ok := c.Get("jwtClaims").(*auth.Claims)
			if !ok || claims == nil {
				return writeError(c, http.StatusUnauthorized, "missing authenticated user", []string{})
			}
			if !hasRole(claims.Roles, "ADMIN") {
				return writeError(c, http.StatusForbidden, "admin role is required", []string{})
			}
			return next(c)
		}
	}
}

func hasRole(roles []string, target string) bool {
	for _, role := range roles {
		if role == target {
			return true
		}
	}
	return false
}
