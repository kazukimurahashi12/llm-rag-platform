package http

import (
	"fmt"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
)

func validateAuthTokenRequest(request api.AuthTokenRequest) []string {
	details := make([]string, 0)
	if strings.TrimSpace(request.Username) == "" {
		details = append(details, "username: must not be null")
	}
	if strings.TrimSpace(request.Password) == "" {
		details = append(details, "password: must not be null")
	}
	return details
}

func validateAdviceRequest(request api.AdviceRequest) []string {
	details := make([]string, 0)
	if strings.TrimSpace(request.MemberContext.Situation) == "" {
		details = append(details, "memberContext.situation: must not be blank")
	}
	if strings.TrimSpace(request.MemberContext.TargetGoal) == "" {
		details = append(details, "memberContext.targetGoal: must not be blank")
	}
	return details
}

func validateKnowledgeCreateRequest(request api.KnowledgeDocumentCreateRequest) []string {
	return validateKnowledgeFields(
		strings.TrimSpace(request.Title),
		request.Content,
		string(request.AceCategory),
		request.AllowedUsernames,
		"create",
	)
}

func validateKnowledgeUpdateRequest(request api.KnowledgeDocumentUpdateRequest) []string {
	return validateKnowledgeFields(
		strings.TrimSpace(request.Title),
		request.Content,
		string(request.AceCategory),
		request.AllowedUsernames,
		"update",
	)
}

func validateKnowledgeFields(title string, content string, aceCategory string, allowedUsernames *[]string, _ string) []string {
	details := make([]string, 0)
	if title == "" {
		details = append(details, "title: must not be blank")
	}
	if strings.TrimSpace(content) == "" {
		details = append(details, "content: must not be blank")
	}
	switch aceCategory {
	case "ABILITY", "CULTURE", "EXPECTATION":
	default:
		details = append(details, fmt.Sprintf("aceCategory: must be one of [ABILITY, CULTURE, EXPECTATION]"))
	}
	if allowedUsernames != nil {
		for index, username := range *allowedUsernames {
			if strings.TrimSpace(username) == "" {
				details = append(details, fmt.Sprintf("allowedUsernames[%d]: must not be blank", index))
			}
		}
	}
	return details
}
