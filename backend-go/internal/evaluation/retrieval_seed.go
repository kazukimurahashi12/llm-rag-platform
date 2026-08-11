package evaluation

import (
	"context"
	"database/sql"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/lib/pq"
)

type retrievalSeedDocument struct {
	Title       string
	Content     string
	AceCategory string
}

var defaultRetrievalSeedDocuments = []retrievalSeedDocument{
	{
		Title:       "週報提出遅延が続くメンバーへの対応",
		AceCategory: string(api.AceAnalysisPrimaryCategoryCULTURE),
		Content:     "週報の提出が遅れているメンバーには、まず1on1で背景を確認する。報告が滞っている理由を責めず、共有漏れがチームに与える影響を説明し、次回からの提出タイミングとリマインド方法を合意する。",
	},
	{
		Title:       "1on1での改善指摘の基本方針",
		AceCategory: string(api.AceAnalysisPrimaryCategoryCULTURE),
		Content:     "1on1で改善点を伝えるときは、相手の人格ではなく行動に焦点を当てる。前回のアクションを振り返り、期待する行動変容を具体化し、心理的安全性を保ちながら次の一歩を合意する。",
	},
	{
		Title:       "報連相が弱いメンバーへの会話例",
		AceCategory: string(api.AceAnalysisPrimaryCategoryCULTURE),
		Content:     "報連相の質を上げたい場合は、単に注意するだけでなく、いつ、誰に、どの粒度で相談や共有をするかを会話例で確認する。Slackで迷ったときは早めに短く状況を共有する。",
	},
	{
		Title:       "心理的安全性を損なわないフィードバック",
		AceCategory: string(api.AceAnalysisPrimaryCategoryCULTURE),
		Content:     "改善フィードバックでは相手を萎縮させない。観察した事実、影響、期待する行動を分けて伝える。評価面談や1on1では、責める言い方を避け、支援姿勢を示す。",
	},
	{
		Title:       "評価面談で避けるべき伝え方",
		AceCategory: string(api.AceAnalysisPrimaryCategoryEXPECTATION),
		Content:     "評価面談では期待値を揃え、モチベーションを下げない伝え方を選ぶ。抽象的な否定や突然の低評価を避け、役割期待、目標、改善行動を具体的に確認する。",
	},
	{
		Title:       "新任メンバーのオンボーディングで確認すべき3要素",
		AceCategory: string(api.AceAnalysisPrimaryCategoryABILITY),
		Content:     "新任メンバーのオンボーディングでは、業務知識のキャッチアップ、組織文化への適応、役割期待の理解を確認する。Slackでの相談方法や暗黙知も早期に説明する。",
	},
	{
		Title:       "入社30日で期待する状態",
		AceCategory: string(api.AceAnalysisPrimaryCategoryEXPECTATION),
		Content:     "入社30日では、自分に何を期待されているか、担当領域の優先順位、業務知識の基本を理解している状態を目指す。期待値が曖昧な場合はマネージャーが明文化する。",
	},
	{
		Title:       "Slackコミュニケーションの作法",
		AceCategory: string(api.AceAnalysisPrimaryCategoryCULTURE),
		Content:     "Slackコミュニケーションでは、相談の背景、困っている点、期限を短く共有する。組織文化や暗黙知に馴染めない新任メンバーには、チャンネルの使い分けを説明する。",
	},
	{
		Title:       "機微情報を含む相談の取り扱い",
		AceCategory: string(api.AceAnalysisPrimaryCategoryEXPECTATION),
		Content:     "1on1記録、健康情報、個人連絡先などの機微情報をAIに入力するときは、必要最小限に要約し、個人を特定できる情報を避ける。監査ログに残る前提で扱う。",
	},
	{
		Title:       "ハラスメント相談の初動対応",
		AceCategory: string(api.AceAnalysisPrimaryCategoryEXPECTATION),
		Content:     "ハラスメントが疑われる相談を受けたとき、マネージャーは事実確認を急ぎすぎず、相談者の安全確保、記録、専門窓口への連携を優先する。独断で解決しようとしない。",
	},
}

// SeedDefaultRetrievalKnowledge は標準 retrieval 評価ケース用のナレッジを再投入する。
func SeedDefaultRetrievalKnowledge(ctx context.Context, db *sql.DB) error {
	titles := make([]string, 0, len(defaultRetrievalSeedDocuments))
	for _, document := range defaultRetrievalSeedDocuments {
		titles = append(titles, document.Title)
	}

	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer rollbackSeed(tx)

	if _, err := tx.ExecContext(ctx, `delete from knowledge_documents where title = any($1)`, pq.Array(titles)); err != nil {
		return err
	}

	for _, document := range defaultRetrievalSeedDocuments {
		var documentID int64
		if err := tx.QueryRowContext(ctx, `
			insert into knowledge_documents (title, content, access_scope, ace_category, updated_at)
			values ($1, $2, 'SHARED', $3, current_timestamp)
			returning id
		`, document.Title, document.Content, document.AceCategory).Scan(&documentID); err != nil {
			return err
		}

		for index, chunk := range seedChunks(document.Content) {
			if _, err := tx.ExecContext(ctx, `
				insert into knowledge_document_chunks (knowledge_document_id, chunk_index, content)
				values ($1, $2, $3)
			`, documentID, index, chunk); err != nil {
				return err
			}
		}
	}

	return tx.Commit()
}

func seedChunks(content string) []string {
	normalized := strings.Join(strings.Fields(strings.TrimSpace(content)), " ")
	if normalized == "" {
		return []string{}
	}
	runes := []rune(normalized)
	if len(runes) <= 180 {
		return []string{normalized}
	}

	chunks := make([]string, 0)
	for start := 0; start < len(runes); {
		end := start + 180
		if end > len(runes) {
			end = len(runes)
		}
		chunks = append(chunks, strings.TrimSpace(string(runes[start:end])))
		if end == len(runes) {
			break
		}
		start = end - 40
		if start < 0 {
			start = 0
		}
	}
	return chunks
}

func rollbackSeed(tx *sql.Tx) {
	_ = tx.Rollback()
}
