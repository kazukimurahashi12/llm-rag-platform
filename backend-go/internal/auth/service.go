package auth

import (
	"errors"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

// Service は固定ユーザー認証と JWT 発行を担当する。
type Service struct {
	cfg          config.Config
	tokenService *TokenService
}

// NewService は認証サービスを生成する。
func NewService(cfg config.Config, tokenService *TokenService) *Service {
	return &Service{
		cfg:          cfg,
		tokenService: tokenService,
	}
}

// IssueToken は固定ユーザー情報を使って JWT を発行する。
func (s *Service) IssueToken(username string, password string) (*api.AuthTokenResponse, error) {
	user, err := s.findUser(username, password)
	if err != nil {
		return nil, err
	}

	token, expiresAt, err := s.tokenService.IssueAccessToken(user.Username, user.Roles)
	if err != nil {
		return nil, err
	}

	return &api.AuthTokenResponse{
		AccessToken: token,
		TokenType:   "Bearer",
		ExpiresAt:   expiresAt,
		Username:    user.Username,
		Roles:       user.Roles,
	}, nil
}

func (s *Service) findUser(username string, password string) (config.UserCredential, error) {
	candidates := []config.UserCredential{s.cfg.AdminUser, s.cfg.OperatorUser}
	for _, user := range candidates {
		if user.Username == username && user.Password == password {
			return user, nil
		}
	}

	return config.UserCredential{}, errors.New("invalid username or password")
}
