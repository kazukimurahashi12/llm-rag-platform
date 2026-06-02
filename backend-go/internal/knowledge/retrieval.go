package knowledge

import (
	"context"
	"fmt"
	"sort"
	"strings"
	"unicode"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
)

// RetrievalService は Go 版の vector + keyword retrieval を担当する。
type RetrievalService struct {
	repository *Repository
	cfg        config.RAGConfig
	openAI     *openai.Client
	metrics    *RetrievalMetrics
}

// RetrievedKnowledge は prompt 用文脈と取得文書をまとめる。
type RetrievedKnowledge struct {
	PromptContext string
	Documents     []api.RetrievedDocument
}

type scoredChunk struct {
	chunk ChunkRecord
	score float64
}

// RetrievalOptions は retrieval 評価や比較時の上書き条件を表す。
type RetrievalOptions struct {
	TopK               int
	MinSimilarityScore *float64
	RerankEnabled      *bool
}

// NewRetrievalService は retrieval service を生成する。
func NewRetrievalService(repository *Repository, cfg config.RAGConfig, openAI *openai.Client, metrics *RetrievalMetrics) *RetrievalService {
	return &RetrievalService{
		repository: repository,
		cfg:        cfg,
		openAI:     openAI,
		metrics:    metrics,
	}
}

// Retrieve は keyword retrieval を実行する。
func (s *RetrievalService) Retrieve(
	ctx context.Context,
	query string,
	topK int,
	currentUsername string,
	isAdmin bool,
	preferredAceCategory api.AceAnalysisPrimaryCategory,
) (*RetrievedKnowledge, error) {
	return s.RetrieveWithOptions(ctx, query, currentUsername, isAdmin, preferredAceCategory, RetrievalOptions{
		TopK: topK,
	})
}

// RetrieveWithOptions は比較用の上書き条件つき retrieval を実行する。
func (s *RetrievalService) RetrieveWithOptions(
	ctx context.Context,
	query string,
	currentUsername string,
	isAdmin bool,
	preferredAceCategory api.AceAnalysisPrimaryCategory,
	options RetrievalOptions,
) (*RetrievedKnowledge, error) {
	topK := options.TopK
	if topK <= 0 {
		topK = int(s.cfg.TopK)
	}
	candidateLimit := s.candidateLimit(topK, options)
	keywords := extractKeywords(query)

	if s.cfg.VectorSearchEnabled {
		vectorResult, err := s.retrieveByVector(ctx, query, keywords, topK, candidateLimit, currentUsername, isAdmin, preferredAceCategory, options)
		if err == nil && vectorResult != nil && len(vectorResult.Documents) > 0 {
			if s.metrics != nil {
				s.metrics.RecordVectorAccepted()
			}
			return vectorResult, nil
		}
	}

	chunks, err := s.repository.FindAllChunks(ctx)
	if err != nil {
		return nil, err
	}

	if len(keywords) == 0 {
		return &RetrievedKnowledge{
			PromptContext: "追加ナレッジなし",
			Documents:     []api.RetrievedDocument{},
		}, nil
	}

	scoredChunks := make([]scoredChunk, 0)
	for _, chunk := range chunks {
		if !canAccess(chunk, currentUsername, isAdmin) {
			continue
		}

		searchableText := strings.ToLower(chunk.Title + " " + chunk.Content)
		score := 0.0
		for _, keyword := range keywords {
			if strings.Contains(searchableText, keyword) {
				score += 1.0
			}
		}
		score += aceCategoryBoost(chunk.AceCategory, preferredAceCategory)
		if score <= 0 {
			continue
		}

		scoredChunks = append(scoredChunks, scoredChunk{chunk: chunk, score: score})
	}

	sort.SliceStable(scoredChunks, func(i, j int) bool {
		return scoredChunks[i].score > scoredChunks[j].score
	})

	if len(scoredChunks) > candidateLimit {
		scoredChunks = scoredChunks[:candidateLimit]
	}

	scoredChunks = s.rerankKeywordChunks(scoredChunks, keywords, topK, options)
	if len(scoredChunks) > topK {
		scoredChunks = scoredChunks[:topK]
	}

	documents := make([]api.RetrievedDocument, 0, len(scoredChunks))
	for _, matched := range scoredChunks {
		document := api.RetrievedDocument{
			Id:         matched.chunk.DocumentID,
			Title:      matched.chunk.Title,
			Excerpt:    truncate(matched.chunk.Content, 200),
			ChunkIndex: matched.chunk.ChunkIndex,
		}
		if aceCategory := toRetrievedAceCategory(matched.chunk.AceCategory); aceCategory != nil {
			document.AceCategory = aceCategory
		}
		documents = append(documents, document)
	}

	promptLines := make([]string, 0, len(documents))
	for _, document := range documents {
		promptLines = append(promptLines, "- "+document.Title+": "+document.Excerpt)
	}

	promptContext := "追加ナレッジなし"
	if len(promptLines) > 0 {
		promptContext = strings.Join(promptLines, "\n")
	}

	return &RetrievedKnowledge{
		PromptContext: promptContext,
		Documents:     documents,
	}, nil
}

func (s *RetrievalService) retrieveByVector(
	ctx context.Context,
	query string,
	keywords []string,
	topK int,
	candidateLimit int,
	currentUsername string,
	isAdmin bool,
	preferredAceCategory api.AceAnalysisPrimaryCategory,
	options RetrievalOptions,
) (*RetrievedKnowledge, error) {
	queryEmbedding, err := s.openAI.Embed(ctx, s.cfg.EmbeddingModel, query, s.cfg.EmbeddingDimensions)
	if err != nil {
		return nil, err
	}

	matches, err := s.repository.FindNearestChunks(ctx, toVectorLiteral(queryEmbedding, s.cfg.EmbeddingDimensions), candidateLimit)
	if err != nil {
		return nil, err
	}

	filteredMatches := make([]VectorMatch, 0, len(matches))
	filteredOutCount := 0
	minSimilarityScore := s.cfg.MinSimilarityScore
	if options.MinSimilarityScore != nil {
		minSimilarityScore = *options.MinSimilarityScore
	}
	for _, match := range matches {
		if !canAccess(match.ChunkRecord, currentUsername, isAdmin) {
			continue
		}
		if match.SimilarityScore < minSimilarityScore {
			filteredOutCount++
			continue
		}
		filteredMatches = append(filteredMatches, match)
	}
	if s.metrics != nil {
		s.metrics.RecordThresholdFiltered(filteredOutCount)
	}

	sort.SliceStable(filteredMatches, func(i, j int) bool {
		left := filteredMatches[i].SimilarityScore + aceCategoryBoost(filteredMatches[i].AceCategory, preferredAceCategory)
		right := filteredMatches[j].SimilarityScore + aceCategoryBoost(filteredMatches[j].AceCategory, preferredAceCategory)
		return left > right
	})

	if len(filteredMatches) == 0 {
		if filteredOutCount > 0 && s.metrics != nil {
			s.metrics.RecordThresholdFallback()
		}
		return &RetrievedKnowledge{
			PromptContext: "追加ナレッジなし",
			Documents:     []api.RetrievedDocument{},
		}, nil
	}

	filteredMatches = s.rerankVectorMatches(filteredMatches, keywords, topK, options)
	if len(filteredMatches) > topK {
		filteredMatches = filteredMatches[:topK]
	}

	documents := make([]api.RetrievedDocument, 0, len(filteredMatches))
	for _, match := range filteredMatches {
		document := api.RetrievedDocument{
			Id:              match.DocumentID,
			Title:           match.Title,
			Excerpt:         truncate(match.Content, 200),
			ChunkIndex:      match.ChunkIndex,
			DistanceScore:   &match.DistanceScore,
			SimilarityScore: &match.SimilarityScore,
		}
		if aceCategory := toRetrievedAceCategory(match.AceCategory); aceCategory != nil {
			document.AceCategory = aceCategory
		}
		documents = append(documents, document)
	}

	promptLines := make([]string, 0, len(documents))
	for _, document := range documents {
		promptLines = append(promptLines, "- "+document.Title+": "+document.Excerpt)
	}

	return &RetrievedKnowledge{
		PromptContext: strings.Join(promptLines, "\n"),
		Documents:     documents,
	}, nil
}

func canAccess(chunk ChunkRecord, currentUsername string, isAdmin bool) bool {
	if isAdmin {
		return true
	}
	for _, allowedUsername := range chunk.AllowedUsernames {
		if allowedUsername == currentUsername {
			return true
		}
	}

	return chunk.AccessScope == "SHARED" && len(chunk.AllowedUsernames) == 0
}

func extractKeywords(query string) []string {
	var normalizedBuilder strings.Builder
	for _, r := range strings.ToLower(query) {
		switch {
		case unicode.IsLetter(r), unicode.IsNumber(r), unicode.IsSpace(r):
			normalizedBuilder.WriteRune(r)
		default:
			normalizedBuilder.WriteRune(' ')
		}
	}

	normalizedQuery := normalizedBuilder.String()
	wordTokens := make([]string, 0)
	for _, token := range strings.Fields(normalizedQuery) {
		if len([]rune(token)) >= 2 {
			wordTokens = append(wordTokens, token)
		}
	}

	compactText := strings.Join(strings.Fields(normalizedQuery), "")
	bigramTokens := make([]string, 0)
	compactRunes := []rune(compactText)
	for i := 0; i+1 < len(compactRunes); i++ {
		bigramTokens = append(bigramTokens, string(compactRunes[i:i+2]))
	}

	seen := map[string]struct{}{}
	keywords := make([]string, 0, len(wordTokens)+len(bigramTokens))
	for _, token := range append(wordTokens, bigramTokens...) {
		if _, exists := seen[token]; exists {
			continue
		}
		seen[token] = struct{}{}
		keywords = append(keywords, token)
	}

	return keywords
}

func aceCategoryBoost(documentCategory string, preferredCategory api.AceAnalysisPrimaryCategory) float64 {
	if documentCategory == string(preferredCategory) {
		return 0.35
	}
	return 0.0
}

func lexicalRerankScore(title string, content string, keywords []string) float64 {
	normalizedTitle := strings.ToLower(title)
	normalizedContent := strings.ToLower(content)
	titleMatches := 0
	contentMatches := 0
	for _, keyword := range keywords {
		if strings.Contains(normalizedTitle, keyword) {
			titleMatches++
		}
		if strings.Contains(normalizedContent, keyword) {
			contentMatches++
		}
	}
	return (float64(titleMatches) * 0.25) + (float64(contentMatches) * 0.1)
}

func (s *RetrievalService) rerankVectorMatches(matches []VectorMatch, keywords []string, topK int, options RetrievalOptions) []VectorMatch {
	if !s.isRerankEnabled(options) {
		if len(matches) > topK {
			return matches[:topK]
		}
		return matches
	}

	sort.SliceStable(matches, func(i, j int) bool {
		left := matches[i].SimilarityScore + lexicalRerankScore(matches[i].Title, matches[i].Content, keywords)
		right := matches[j].SimilarityScore + lexicalRerankScore(matches[j].Title, matches[j].Content, keywords)
		return left > right
	})

	if len(matches) > topK {
		return matches[:topK]
	}
	return matches
}

func (s *RetrievalService) rerankKeywordChunks(chunks []scoredChunk, keywords []string, topK int, options RetrievalOptions) []scoredChunk {
	if !s.isRerankEnabled(options) {
		if len(chunks) > topK {
			return chunks[:topK]
		}
		return chunks
	}

	sort.SliceStable(chunks, func(i, j int) bool {
		left := lexicalRerankScore(chunks[i].chunk.Title, chunks[i].chunk.Content, keywords)
		right := lexicalRerankScore(chunks[j].chunk.Title, chunks[j].chunk.Content, keywords)
		return left > right
	})

	if len(chunks) > topK {
		return chunks[:topK]
	}
	return chunks
}

func (s *RetrievalService) candidateLimit(topK int, options RetrievalOptions) int {
	if !s.isRerankEnabled(options) {
		return topK
	}
	multiplier := int(s.cfg.RerankCandidateMultiplier)
	if multiplier < 1 {
		multiplier = 1
	}
	return topK * multiplier
}

func (s *RetrievalService) isRerankEnabled(options RetrievalOptions) bool {
	if options.RerankEnabled != nil {
		return *options.RerankEnabled
	}
	return s.cfg.RerankEnabled
}

func toRetrievedAceCategory(value string) *api.RetrievedDocumentAceCategory {
	category := api.RetrievedDocumentAceCategory(value)
	switch category {
	case api.RetrievedDocumentAceCategory("ABILITY"),
		api.RetrievedDocumentAceCategory("CULTURE"),
		api.RetrievedDocumentAceCategory("EXPECTATION"):
		return &category
	default:
		return nil
	}
}

func truncate(text string, maxLen int) string {
	runes := []rune(text)
	if len(runes) <= maxLen {
		return text
	}
	return string(runes[:maxLen])
}

func toVectorLiteral(embedding []float64, expectedDimensions int64) string {
	if expectedDimensions > 0 && int64(len(embedding)) != expectedDimensions {
		panic(fmt.Sprintf("expected embedding dimension %d but got %d", expectedDimensions, len(embedding)))
	}

	parts := make([]string, 0, len(embedding))
	for _, value := range embedding {
		parts = append(parts, fmt.Sprintf("%f", value))
	}

	return "[" + strings.Join(parts, ",") + "]"
}

func (s *RetrievalService) MetricsSnapshot() RetrievalMetricsSnapshot {
	if s.metrics == nil {
		return RetrievalMetricsSnapshot{}
	}
	return s.metrics.Snapshot()
}
