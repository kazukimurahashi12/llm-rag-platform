package prompt

import (
	"fmt"
	"os"
	"strings"
)

// TemplateLoader は prompt テンプレートを読み込む。
type TemplateLoader struct {
	content string
}

// NewTemplateLoader は指定パスの prompt ファイルを読み込む。
func NewTemplateLoader(path string) (*TemplateLoader, error) {
	body, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to load prompt template: %w", err)
	}

	return &TemplateLoader{
		content: string(body),
	}, nil
}

// Render は簡易なプレースホルダ置換で prompt を生成する。
func (l *TemplateLoader) Render(values map[string]string) string {
	rendered := l.content
	for key, value := range values {
		rendered = strings.ReplaceAll(rendered, "{{"+key+"}}", value)
	}

	return rendered
}
