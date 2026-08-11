package http

import (
	"encoding/json"
	"errors"
	stdhttp "net/http"
	"net/http/httptest"
	"testing"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/auth"
	"github.com/labstack/echo/v4"
)

func TestWriteErrorResponseShape(t *testing.T) {
	e := echo.New()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(stdhttp.MethodGet, "/", nil)
	ctx := e.NewContext(req, rec)

	if err := writeError(ctx, stdhttp.StatusForbidden, "admin role is required", nil); err != nil {
		t.Fatalf("writeError returned error: %v", err)
	}

	assertErrorBody(t, rec, stdhttp.StatusForbidden, "admin role is required", []string{})
}

func TestWriteInvalidRequestBody(t *testing.T) {
	e := echo.New()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(stdhttp.MethodPost, "/", nil)
	ctx := e.NewContext(req, rec)

	if err := writeInvalidRequestBody(ctx, errors.New("bad json")); err != nil {
		t.Fatalf("writeInvalidRequestBody returned error: %v", err)
	}

	assertErrorBody(t, rec, stdhttp.StatusBadRequest, "invalid request body", []string{"bad json"})
}

func TestJWTMiddlewareRequiresBearerToken(t *testing.T) {
	e := echo.New()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(stdhttp.MethodGet, "/", nil)
	ctx := e.NewContext(req, rec)

	err := jwtMiddleware(fakeClaimsParser{})(func(c echo.Context) error {
		return c.NoContent(stdhttp.StatusNoContent)
	})(ctx)
	if err != nil {
		t.Fatalf("jwtMiddleware returned error: %v", err)
	}

	assertErrorBody(t, rec, stdhttp.StatusUnauthorized, "missing bearer token", []string{})
}

func TestJWTMiddlewareRejectsInvalidToken(t *testing.T) {
	e := echo.New()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(stdhttp.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer invalid")
	ctx := e.NewContext(req, rec)

	err := jwtMiddleware(fakeClaimsParser{err: errors.New("bad signature")})(func(c echo.Context) error {
		return c.NoContent(stdhttp.StatusNoContent)
	})(ctx)
	if err != nil {
		t.Fatalf("jwtMiddleware returned error: %v", err)
	}

	assertErrorBody(t, rec, stdhttp.StatusUnauthorized, "invalid bearer token", []string{"bad signature"})
}

func TestRoleMiddlewareRequiresAllowedRole(t *testing.T) {
	e := echo.New()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(stdhttp.MethodGet, "/", nil)
	ctx := e.NewContext(req, rec)
	ctx.Set("jwtClaims", &auth.Claims{Roles: []string{"VIEWER"}})

	err := roleMiddleware("ADMIN", "OPERATOR")(func(c echo.Context) error {
		return c.NoContent(stdhttp.StatusNoContent)
	})(ctx)
	if err != nil {
		t.Fatalf("roleMiddleware returned error: %v", err)
	}

	assertErrorBody(t, rec, stdhttp.StatusForbidden, "required role is missing", []string{})
}

func TestAdminMiddlewareRequiresAdminRole(t *testing.T) {
	e := echo.New()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(stdhttp.MethodGet, "/", nil)
	ctx := e.NewContext(req, rec)
	ctx.Set("jwtClaims", &auth.Claims{Roles: []string{"OPERATOR"}})

	err := adminMiddleware()(func(c echo.Context) error {
		return c.NoContent(stdhttp.StatusNoContent)
	})(ctx)
	if err != nil {
		t.Fatalf("adminMiddleware returned error: %v", err)
	}

	assertErrorBody(t, rec, stdhttp.StatusForbidden, "admin role is required", []string{})
}

type fakeClaimsParser struct {
	claims *auth.Claims
	err    error
}

func (p fakeClaimsParser) ParseAndValidate(string) (*auth.Claims, error) {
	if p.err != nil {
		return nil, p.err
	}
	if p.claims != nil {
		return p.claims, nil
	}
	return &auth.Claims{Roles: []string{"ADMIN"}}, nil
}

func assertErrorBody(t *testing.T, rec *httptest.ResponseRecorder, wantStatus int, wantMessage string, wantDetails []string) {
	t.Helper()
	if rec.Code != wantStatus {
		t.Fatalf("status = %d, want %d", rec.Code, wantStatus)
	}

	var body struct {
		Status  int      `json:"status"`
		Message string   `json:"message"`
		Details []string `json:"details"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("failed to decode response body: %v", err)
	}
	if body.Status != wantStatus {
		t.Fatalf("body.status = %d, want %d", body.Status, wantStatus)
	}
	if body.Message != wantMessage {
		t.Fatalf("body.message = %q, want %q", body.Message, wantMessage)
	}
	if len(body.Details) != len(wantDetails) {
		t.Fatalf("body.details = %v, want %v", body.Details, wantDetails)
	}
	for i := range wantDetails {
		if body.Details[i] != wantDetails[i] {
			t.Fatalf("body.details = %v, want %v", body.Details, wantDetails)
		}
	}
}
