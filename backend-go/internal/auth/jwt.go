package auth

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

// TokenService は Go 版 backend の JWT 発行と検証を担当する。
type TokenService struct {
	cfg config.Config
}

// Claims は JWT に保持する最小クレームを表す。
type Claims struct {
	Roles []string `json:"roles"`
	jwt.RegisteredClaims
}

// NewTokenService は JWT 用のサービスを生成する。
func NewTokenService(cfg config.Config) *TokenService {
	return &TokenService{cfg: cfg}
}

// IssueAccessToken はユーザー名とロールから署名済み JWT を発行する。
func (s *TokenService) IssueAccessToken(username string, roles []string) (string, time.Time, error) {
	now := time.Now().UTC()
	expiresAt := now.Add(time.Duration(s.cfg.JWTExpiresIn) * time.Second)

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, Claims{
		Roles: roles,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   username,
			Issuer:    s.cfg.JWTIssuer,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
		},
	})

	signed, err := token.SignedString([]byte(s.cfg.JWTSecret))
	if err != nil {
		return "", time.Time{}, err
	}

	return signed, expiresAt, nil
}

// ParseAndValidate は Bearer token を検証し、claims を返す。
func (s *TokenService) ParseAndValidate(token string) (*Claims, error) {
	parsed, err := jwt.ParseWithClaims(token, &Claims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected jwt signing method")
		}
		return []byte(s.cfg.JWTSecret), nil
	}, jwt.WithIssuer(s.cfg.JWTIssuer))
	if err != nil {
		return nil, err
	}

	claims, ok := parsed.Claims.(*Claims)
	if !ok || !parsed.Valid {
		return nil, errors.New("invalid jwt claims")
	}

	return claims, nil
}
