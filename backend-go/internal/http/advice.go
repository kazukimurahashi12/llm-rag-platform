package http

import (
	"net/http"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/advice"
	"github.com/labstack/echo/v4"
)

// RegisterAdviceRoutes は Go 版 advice API の空実装を登録する。
func RegisterAdviceRoutes(e *echo.Echo, tokenService jwtClaimsParser) {
	adviceGroup := e.Group("/v1/management")
	adviceGroup.Use(jwtMiddleware(tokenService))

	adviceGroup.POST("/advice", func(c echo.Context) error {
		request := advice.Request{}
		if err := c.Bind(&request); err != nil {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "invalid request body",
				"details": []string{err.Error()},
			})
		}

		if strings.TrimSpace(request.MemberContext.Situation) == "" || strings.TrimSpace(request.MemberContext.TargetGoal) == "" {
			return c.JSON(http.StatusBadRequest, map[string]any{
				"status":  http.StatusBadRequest,
				"message": "memberContext.situation and memberContext.targetGoal are required",
				"details": []string{},
			})
		}

		response := advice.Response{
			Advice: "Go 版 backend の空実装です。今後ここに advice 生成、RAG、groundedness を段階移植します。現時点では、まず状況確認と期待値の明確化から始める前提でダミー応答を返しています。",
			AceAnalysis: advice.AceAnalysis{
				PrimaryCategory: "EXPECTATION",
				Reason:          "現時点の Go 版では簡易実装として EXPECTATION を返しています。",
			},
			GroundednessEvaluation: advice.GroundednessEvaluation{
				GroundednessScore: 0.0,
				Reason:            "Go 版では groundedness judge は未移植のため、暫定値です。",
				Status:            "NO_EVIDENCE",
				FallbackApplied:   false,
			},
			Usage: advice.UsageInfo{
				Model:            "stub",
				PromptTokens:     0,
				CompletionTokens: 0,
				TotalTokens:      0,
				EstimatedCostJpy: 0,
			},
			RetrievedDocuments: []advice.RetrievedDocument{},
		}

		return c.JSON(http.StatusOK, response)
	})
}
