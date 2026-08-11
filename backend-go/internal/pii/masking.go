package pii

import "regexp"

// MaskingService は監査ログ保存前に PII をマスクする。
type MaskingService struct {
	rules []maskingRule
}

type maskingRule struct {
	pattern     *regexp.Regexp
	replacement string
}

// NewMaskingService は監査ログ向けの最小マスキングルールを持つ service を返す。
func NewMaskingService() *MaskingService {
	return &MaskingService{
		rules: []maskingRule{
			{
				pattern:     regexp.MustCompile(`[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}`),
				replacement: "[MASKED_EMAIL]",
			},
			{
				pattern:     regexp.MustCompile(`(?:\+81[- ]?)?(?:0\d{1,4}[- ]?\d{1,4}[- ]?\d{4})`),
				replacement: "[MASKED_PHONE]",
			},
			{
				pattern:     regexp.MustCompile(`社員番号[:：]?\s*[A-Za-z0-9-]+`),
				replacement: "社員番号: [MASKED_EMPLOYEE_ID]",
			},
		},
	}
}

// MaskText は定義済みルールで PII を置換する。
func (s *MaskingService) MaskText(text string) string {
	masked := text
	for _, rule := range s.rules {
		masked = rule.pattern.ReplaceAllString(masked, rule.replacement)
	}
	return masked
}
