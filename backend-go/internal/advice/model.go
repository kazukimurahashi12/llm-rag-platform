package advice

// Request は Go 版 advice API の入力契約を表す。
type Request struct {
	MemberContext MemberContext `json:"memberContext"`
	Setting       *Setting      `json:"setting,omitempty"`
}

// MemberContext は相談状況と目標を保持する。
type MemberContext struct {
	Situation  string `json:"situation"`
	TargetGoal string `json:"targetGoal"`
}

// Setting は tone や model などの任意設定を保持する。
type Setting struct {
	Tone  string `json:"tone,omitempty"`
	Model string `json:"model,omitempty"`
}

// Response は Kotlin 版 AdviceResponse に寄せた最小レスポンスを表す。
type Response struct {
	Advice                 string                 `json:"advice"`
	AceAnalysis            AceAnalysis            `json:"aceAnalysis"`
	GroundednessEvaluation GroundednessEvaluation `json:"groundednessEvaluation"`
	Usage                  UsageInfo              `json:"usage"`
	RetrievedDocuments     []RetrievedDocument    `json:"retrievedDocuments"`
}

// AceAnalysis は主要カテゴリとその理由を表す。
type AceAnalysis struct {
	PrimaryCategory string `json:"primaryCategory"`
	Reason          string `json:"reason"`
}

// GroundednessEvaluation は groundedness の暫定評価結果を表す。
type GroundednessEvaluation struct {
	GroundednessScore float64 `json:"groundednessScore"`
	Reason            string  `json:"reason"`
	Status            string  `json:"status"`
	FallbackApplied   bool    `json:"fallbackApplied"`
}

// UsageInfo は usage と概算コストの最小情報を表す。
type UsageInfo struct {
	Model            string  `json:"model"`
	PromptTokens     int     `json:"promptTokens"`
	CompletionTokens int     `json:"completionTokens"`
	TotalTokens      int     `json:"totalTokens"`
	EstimatedCostJpy float64 `json:"estimatedCostJpy"`
}

// RetrievedDocument は将来の RAG 用根拠文書の shape を先に定義する。
type RetrievedDocument struct {
	ID             int64    `json:"id"`
	Title          string   `json:"title"`
	Excerpt        string   `json:"excerpt"`
	ChunkIndex     int      `json:"chunkIndex"`
	AceCategory    *string  `json:"aceCategory,omitempty"`
	DistanceScore  *float64 `json:"distanceScore,omitempty"`
	SimilarityScore *float64 `json:"similarityScore,omitempty"`
}
