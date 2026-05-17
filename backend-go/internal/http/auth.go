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
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}

		response, err := authService.IssueToken(request.Username, request.Password)
		if err != nil {
			return c.JSON(http.StatusUnauthorized, map[string]any{
				"status":  http.StatusUnauthorized,
				"message": err.Error(),
				"details": []string{},
			})
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
				return c.JSON(http.StatusUnauthorized, map[string]any{
					"status":  http.StatusUnauthorized,
					"message": "missing bearer token",
					"details": []string{},
				})
			}

			token := strings.TrimSpace(strings.TrimPrefix(authorization, "Bearer "))
			claims, err := tokenService.ParseAndValidate(token)
			if err != nil {
				return c.JSON(http.StatusUnauthorized, map[string]any{
					"status":  http.StatusUnauthorized,
					"message": "invalid bearer token",
					"details": []string{err.Error()},
				})
			}

			c.Set("jwtClaims", claims)
			return next(c)
		}
	}
}
