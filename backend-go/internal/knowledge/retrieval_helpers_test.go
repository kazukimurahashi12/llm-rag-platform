package knowledge

import (
	"testing"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

func TestCanAccessAllowsAdminAndExplicitUser(t *testing.T) {
	chunk := ChunkRecord{
		AccessScope:      "ADMIN_ONLY",
		AllowedUsernames: []string{"operator"},
	}

	if !canAccess(chunk, "admin", true) {
		t.Fatal("admin should access any chunk")
	}
	if !canAccess(chunk, "operator", false) {
		t.Fatal("explicitly allowed user should access chunk")
	}
	if canAccess(chunk, "other", false) {
		t.Fatal("unlisted user should not access admin-only chunk")
	}
}

func TestCanAccessAllowsSharedDocumentWithoutAllowList(t *testing.T) {
	chunk := ChunkRecord{
		AccessScope:      "SHARED",
		AllowedUsernames: []string{},
	}

	if !canAccess(chunk, "operator", false) {
		t.Fatal("shared chunk without allow list should be accessible")
	}
}

func TestExtractKeywordsKeepsWordsAndJapaneseBigrams(t *testing.T) {
	keywords := extractKeywords("報連相の遅れと onboarding delay")

	assertContainsKeyword(t, keywords, "報連相の遅れと")
	assertContainsKeyword(t, keywords, "onboarding")
	assertContainsKeyword(t, keywords, "delay")
	assertContainsKeyword(t, keywords, "報連")
}

func TestAceCategoryBoost(t *testing.T) {
	got := aceCategoryBoost("CULTURE", api.AceAnalysisPrimaryCategoryCULTURE)
	if got <= 0 {
		t.Fatalf("aceCategoryBoost() = %f, want positive boost", got)
	}
	if aceCategoryBoost("ABILITY", api.AceAnalysisPrimaryCategoryCULTURE) != 0 {
		t.Fatal("different category should not be boosted")
	}
}

func TestCandidateLimitUsesRerankMultiplier(t *testing.T) {
	service := NewRetrievalService(nil, config.RAGConfig{
		RerankEnabled:             true,
		RerankCandidateMultiplier: 3,
	}, nil, nil)

	if got := service.candidateLimit(4, RetrievalOptions{}); got != 12 {
		t.Fatalf("candidateLimit() = %d, want 12", got)
	}

	disabled := false
	if got := service.candidateLimit(4, RetrievalOptions{RerankEnabled: &disabled}); got != 4 {
		t.Fatalf("candidateLimit() with disabled rerank = %d, want 4", got)
	}
}

func TestToVectorLiteralUsesFloat32PrecisionWithoutFixedRounding(t *testing.T) {
	got := toVectorLiteral([]float64{0.123456789, -1.25, 0}, 3)
	want := "[0.12345679,-1.25,0]"
	if got != want {
		t.Fatalf("toVectorLiteral() = %q, want %q", got, want)
	}
}

func assertContainsKeyword(t *testing.T, keywords []string, want string) {
	t.Helper()
	for _, keyword := range keywords {
		if keyword == want {
			return
		}
	}
	t.Fatalf("keywords %v did not contain %q", keywords, want)
}
