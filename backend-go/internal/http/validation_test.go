package http

import (
	"testing"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
)

func TestValidateAdviceRequest(t *testing.T) {
	details := validateAdviceRequest(api.AdviceRequest{})

	assertContainsDetail(t, details, "memberContext.situation: must not be blank")
	assertContainsDetail(t, details, "memberContext.targetGoal: must not be blank")
}

func TestValidateKnowledgeCreateRequest(t *testing.T) {
	blankUsername := " "
	allowedUsernames := []string{blankUsername}
	details := validateKnowledgeCreateRequest(api.KnowledgeDocumentCreateRequest{
		AllowedUsernames: &allowedUsernames,
	})

	assertContainsDetail(t, details, "title: must not be blank")
	assertContainsDetail(t, details, "content: must not be blank")
	assertContainsDetail(t, details, "aceCategory: must be one of [ABILITY, CULTURE, EXPECTATION]")
	assertContainsDetail(t, details, "allowedUsernames[0]: must not be blank")
}

func assertContainsDetail(t *testing.T, details []string, want string) {
	t.Helper()
	for _, detail := range details {
		if detail == want {
			return
		}
	}
	t.Fatalf("details %v did not contain %q", details, want)
}
