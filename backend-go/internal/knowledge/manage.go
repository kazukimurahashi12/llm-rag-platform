package knowledge

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
)

// ErrKnowledgeDocumentNotFound は対象文書未存在を表す。
var ErrKnowledgeDocumentNotFound = errors.New("knowledge document not found")

// ManagementService は knowledge document の read/create/update を担当する。
type ManagementService struct {
	repository *Repository
	cfg        config.RAGConfig
	openAI     *openai.Client
}

// NewManagementService は management service を生成する。
func NewManagementService(repository *Repository, cfg config.RAGConfig, openAI *openai.Client) *ManagementService {
	return &ManagementService{
		repository: repository,
		cfg:        cfg,
		openAI:     openAI,
	}
}

// GetDocuments は ACL を考慮して文書一覧を返す。
func (s *ManagementService) GetDocuments(
	ctx context.Context,
	limit int,
	offset int,
	currentUsername string,
	isAdmin bool,
) (*api.KnowledgeDocumentListResponse, error) {
	safeLimit := limit
	if safeLimit < 1 {
		safeLimit = 20
	}
	if safeLimit > 100 {
		safeLimit = 100
	}
	safeOffset := offset
	if safeOffset < 0 {
		safeOffset = 0
	}

	documents, err := s.repository.ListDocuments(ctx)
	if err != nil {
		return nil, err
	}

	accessible := make([]api.KnowledgeDocumentResponse, 0, len(documents))
	for _, document := range documents {
		if !canAccessDocument(document, currentUsername, isAdmin) {
			continue
		}
		accessible = append(accessible, toDocumentResponse(document))
	}

	start := safeOffset
	if start > len(accessible) {
		start = len(accessible)
	}
	end := start + safeLimit
	if end > len(accessible) {
		end = len(accessible)
	}

	return &api.KnowledgeDocumentListResponse{
		Items:      accessible[start:end],
		Limit:      safeLimit,
		Offset:     safeOffset,
		TotalCount: int64(len(accessible)),
	}, nil
}

// CreateDocument は文書保存、chunking、embedding まで行う。
func (s *ManagementService) CreateDocument(
	ctx context.Context,
	request api.KnowledgeDocumentCreateRequest,
) (*api.KnowledgeDocumentResponse, error) {
	document, err := s.repository.CreateDocument(
		ctx,
		strings.TrimSpace(request.Title),
		request.Content,
		toCreateAccessScope(request.AccessScope),
		string(request.AceCategory),
		derefStringSlice(request.AllowedUsernames),
	)
	if err != nil {
		return nil, err
	}

	if err := s.replaceChunksAndEmbeddings(ctx, document.ID, document.Content); err != nil {
		return nil, err
	}

	response := toDocumentResponse(*document)
	return &response, nil
}

// UpdateDocument は文書と chunk/embedding を全置換する。
func (s *ManagementService) UpdateDocument(
	ctx context.Context,
	documentID int64,
	request api.KnowledgeDocumentUpdateRequest,
) (*api.KnowledgeDocumentResponse, error) {
	document, err := s.repository.UpdateDocument(
		ctx,
		documentID,
		strings.TrimSpace(request.Title),
		request.Content,
		toUpdateAccessScope(request.AccessScope),
		string(request.AceCategory),
		derefStringSlice(request.AllowedUsernames),
	)
	if err != nil {
		return nil, err
	}
	if document == nil {
		return nil, ErrKnowledgeDocumentNotFound
	}

	if err := s.replaceChunksAndEmbeddings(ctx, document.ID, document.Content); err != nil {
		return nil, err
	}

	response := toDocumentResponse(*document)
	return &response, nil
}

// ReindexAllDocuments は全件の chunk/embedding を再生成する。
func (s *ManagementService) ReindexAllDocuments(ctx context.Context) (*api.KnowledgeReindexResponse, error) {
	documents, err := s.repository.ListDocuments(ctx)
	if err != nil {
		return nil, err
	}

	var chunksProcessed int64
	var embeddingsUpdated int64
	for _, document := range documents {
		chunkCount, embeddingCount, err := s.replaceChunksAndEmbeddingsWithCount(ctx, document.ID, document.Content)
		if err != nil {
			return nil, err
		}
		chunksProcessed += chunkCount
		embeddingsUpdated += embeddingCount
	}

	return &api.KnowledgeReindexResponse{
		ChunksProcessed:     chunksProcessed,
		DocumentsProcessed:  int64(len(documents)),
		EmbeddingsUpdated:   embeddingsUpdated,
		VectorSearchEnabled: s.cfg.VectorSearchEnabled,
	}, nil
}

// ReindexDocument は単一文書の chunk/embedding を再生成する。
func (s *ManagementService) ReindexDocument(ctx context.Context, documentID int64) (*api.KnowledgeReindexResponse, error) {
	document, err := s.repository.FindDocumentByID(ctx, documentID)
	if err != nil {
		return nil, err
	}
	if document == nil {
		return nil, ErrKnowledgeDocumentNotFound
	}

	chunkCount, embeddingCount, err := s.replaceChunksAndEmbeddingsWithCount(ctx, document.ID, document.Content)
	if err != nil {
		return nil, err
	}

	return &api.KnowledgeReindexResponse{
		ChunksProcessed:     chunkCount,
		DocumentsProcessed:  1,
		EmbeddingsUpdated:   embeddingCount,
		VectorSearchEnabled: s.cfg.VectorSearchEnabled,
	}, nil
}

func (s *ManagementService) replaceChunksAndEmbeddings(ctx context.Context, documentID int64, content string) error {
	_, _, err := s.replaceChunksAndEmbeddingsWithCount(ctx, documentID, content)
	return err
}

func (s *ManagementService) replaceChunksAndEmbeddingsWithCount(ctx context.Context, documentID int64, content string) (int64, int64, error) {
	chunks := chunkContent(content, 180, 40)
	chunkIDs, err := s.repository.ReplaceChunks(ctx, documentID, chunks)
	if err != nil {
		return 0, 0, err
	}

	if !s.cfg.VectorSearchEnabled {
		return int64(len(chunks)), 0, nil
	}

	var embeddingsUpdated int64
	for index, chunk := range chunks {
		embedding, err := s.openAI.Embed(ctx, s.cfg.EmbeddingModel, chunk, s.cfg.EmbeddingDimensions)
		if err != nil {
			return 0, 0, err
		}
		if err := s.repository.UpdateChunkEmbedding(ctx, chunkIDs[index], toVectorLiteral(embedding, s.cfg.EmbeddingDimensions)); err != nil {
			return 0, 0, err
		}
		embeddingsUpdated++
	}
	return int64(len(chunks)), embeddingsUpdated, nil
}

func chunkContent(content string, chunkSize int, overlap int) []string {
	normalized := strings.Join(strings.Fields(strings.TrimSpace(content)), " ")
	if normalized == "" {
		return []string{}
	}
	runes := []rune(normalized)
	if len(runes) <= chunkSize {
		return []string{normalized}
	}

	if overlap < 0 {
		overlap = 0
	}
	if overlap >= chunkSize {
		overlap = chunkSize / 4
	}

	chunks := make([]string, 0)
	step := chunkSize - overlap
	if step <= 0 {
		step = chunkSize
	}
	for start := 0; start < len(runes); start += step {
		end := start + chunkSize
		if end > len(runes) {
			end = len(runes)
		}
		chunk := strings.TrimSpace(string(runes[start:end]))
		if chunk != "" {
			chunks = append(chunks, chunk)
		}
		if end == len(runes) {
			break
		}
	}
	return chunks
}

func canAccessDocument(document DocumentRecord, currentUsername string, isAdmin bool) bool {
	if isAdmin {
		return true
	}
	for _, username := range document.AllowedUsernames {
		if username == currentUsername {
			return true
		}
	}
	return document.AccessScope == "SHARED" && len(document.AllowedUsernames) == 0
}

func toDocumentResponse(document DocumentRecord) api.KnowledgeDocumentResponse {
	return api.KnowledgeDocumentResponse{
		AccessScope:      api.KnowledgeDocumentResponseAccessScope(document.AccessScope),
		AceCategory:      api.KnowledgeDocumentResponseAceCategory(document.AceCategory),
		AllowedUsernames: document.AllowedUsernames,
		Content:          document.Content,
		CreatedAt:        document.CreatedAt.UTC(),
		Id:               document.ID,
		Title:            document.Title,
		UpdatedAt:        document.UpdatedAt.UTC(),
	}
}

func toCreateAccessScope(value *api.KnowledgeDocumentCreateRequestAccessScope) string {
	if value == nil || strings.TrimSpace(string(*value)) == "" {
		return "SHARED"
	}
	return string(*value)
}

func toUpdateAccessScope(value *api.KnowledgeDocumentUpdateRequestAccessScope) string {
	if value == nil || strings.TrimSpace(string(*value)) == "" {
		return "SHARED"
	}
	return string(*value)
}

func derefStringSlice(value *[]string) []string {
	if value == nil {
		return nil
	}
	result := make([]string, len(*value))
	copy(result, *value)
	return result
}

// Keep time imported in this file only when generated response models require time normalization intent.
var _ = time.UTC
