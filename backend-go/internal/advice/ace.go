package advice

import (
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
)

// AceAnalysisResult は Go 版の簡易 ACE 分析結果を保持する。
type AceAnalysisResult struct {
	PrimaryCategory api.AceAnalysisPrimaryCategory
	Reason          string
}

// analyzeACE は situation と targetGoal をまとめて ACE 観点で分類する。
func analyzeACE(situation string, targetGoal string) AceAnalysisResult {
	combinedText := situation + "\n" + targetGoal

	abilityScore := scoreOf(combinedText, abilityKeywords)
	cultureScore := scoreOf(combinedText, cultureKeywords)
	expectationScore := scoreOf(combinedText, expectationKeywords)

	type scoredCategory struct {
		category api.AceAnalysisPrimaryCategory
		score    float64
	}

	primary := []scoredCategory{
		{category: api.AceAnalysisPrimaryCategoryEXPECTATION, score: expectationScore},
		{category: api.AceAnalysisPrimaryCategoryCULTURE, score: cultureScore},
		{category: api.AceAnalysisPrimaryCategoryABILITY, score: abilityScore},
	}[0]

	for _, candidate := range []scoredCategory{
		{category: api.AceAnalysisPrimaryCategoryEXPECTATION, score: expectationScore},
		{category: api.AceAnalysisPrimaryCategoryCULTURE, score: cultureScore},
		{category: api.AceAnalysisPrimaryCategoryABILITY, score: abilityScore},
	} {
		if candidate.score > primary.score {
			primary = candidate
		}
	}

	reason := map[api.AceAnalysisPrimaryCategory]string{
		api.AceAnalysisPrimaryCategoryABILITY:     "スキル習得、知識不足、業務手順の理解に関する表現が多く、技術学習の支援が中心課題と判断しました。",
		api.AceAnalysisPrimaryCategoryCULTURE:     "報連相、心理的安全性、チーム内コミュニケーション、組織の作法に関する表現が多く、文化適応が中心課題と判断しました。",
		api.AceAnalysisPrimaryCategoryEXPECTATION: "役割、期待値、目標、評価、責任範囲に関する表現が多く、役割期待のすり合わせが中心課題と判断しました。",
	}[primary.category]

	return AceAnalysisResult{
		PrimaryCategory: primary.category,
		Reason:          reason,
	}
}

func scoreOf(text string, keywords []string) float64 {
	normalizedText := strings.ToLower(text)
	score := 1.0
	for _, keyword := range keywords {
		if strings.Contains(normalizedText, keyword) {
			score += 1.0
		}
	}

	return score
}

var abilityKeywords = []string{
	"スキル", "知識", "学習", "技術", "手順", "業務理解", "キャッチアップ",
	"習得", "理解できていない", "できない", "不慣れ", "オンボード", "トレーニング",
	"週報", "提出", "業務", "実務",
}

var cultureKeywords = []string{
	"文化", "価値観", "報連相", "コミュニケーション", "心理的安全性", "雰囲気", "なじめない",
	"相談しづらい", "暗黙知", "チーム", "振る舞い", "作法", "関係性", "1on1", "フィードバック", "slack",
}

var expectationKeywords = []string{
	"期待", "期待値", "役割", "目標", "成果", "責任", "評価", "評価面談", "すり合わせ",
	"求める", "ミッション", "優先順位", "任せたい", "ゴール", "重要性", "振り返り",
}
