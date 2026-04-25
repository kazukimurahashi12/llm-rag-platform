export type AdviceTone = "empathetic" | "direct" | "supportive";

export interface AuthTokenRequest {
  username: string;
  password: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  username: string;
  roles: string[];
}

export interface DashboardSummaryResponse {
  totalAdviceRequests: number;
  averageLatencyMs: number;
  averageCostJpy: number;
  reindexSuccessRate: number;
  completedReindexJobs: number;
  failedReindexJobs: number;
  queuedReindexJobs: number;
  runningReindexJobs: number;
  totalReindexJobs: number;
  totalKnowledgeDocuments: number;
  totalKnowledgeChunks: number;
  sharedKnowledgeDocuments: number;
  restrictedKnowledgeDocuments: number;
  vectorAcceptedRetrievals: number;
  vectorThresholdFallbacks: number;
  vectorThresholdFilteredChunks: number;
}

export interface RetrievalEvaluationCaseResult {
  label?: string;
  query: string;
  expectedDocumentTitles: string[];
  retrievedDocumentTitles: string[];
  matchedDocumentTitles: string[];
  matched: boolean;
  retrievedCount: number;
  firstRelevantRank?: number;
  reciprocalRank: number;
  recallAtK: number;
  precisionAtK: number;
}

export interface RetrievalEvaluationResponse {
  topK: number;
  totalCases: number;
  matchedCases: number;
  hitRate: number;
  meanReciprocalRank: number;
  averageRecallAtK: number;
  averagePrecisionAtK: number;
  averageRetrievedCount: number;
  caseResults: RetrievalEvaluationCaseResult[];
}

export interface RetrievalEvaluationVariantRequest {
  label: string;
  topK?: number;
  minSimilarityScore?: number;
  rerankEnabled?: boolean;
}

export interface RetrievalEvaluationComparisonRequest {
  variants: RetrievalEvaluationVariantRequest[];
}

export interface RetrievalEvaluationVariantResult {
  label: string;
  topK: number;
  minSimilarityScore?: number;
  rerankEnabled?: boolean;
  totalCases: number;
  matchedCases: number;
  hitRate: number;
  meanReciprocalRank: number;
  averageRecallAtK: number;
  averagePrecisionAtK: number;
  averageRetrievedCount: number;
}

export interface RetrievalEvaluationComparisonResponse {
  variantResults: RetrievalEvaluationVariantResult[];
}

export interface PromptInjectionEvaluationCaseRequest {
  label?: string;
  input: string;
  expectedBlocked: boolean;
}

export interface PromptInjectionEvaluationRequest {
  cases: PromptInjectionEvaluationCaseRequest[];
}

export interface PromptInjectionEvaluationCaseResult {
  label?: string;
  input: string;
  expectedBlocked: boolean;
  blocked: boolean;
  matched: boolean;
  detectionMessage?: string;
  expectedOutcome: string;
  actualOutcome: string;
}

export interface PromptInjectionEvaluationResponse {
  totalCases: number;
  expectedBlockedCases: number;
  expectedAllowedCases: number;
  correctlyBlockedCases: number;
  correctlyAllowedCases: number;
  detectionRate: number;
  falsePositiveRate: number;
  accuracy: number;
  caseResults: PromptInjectionEvaluationCaseResult[];
}

export interface AdviceRequest {
  memberContext: {
    situation: string;
    targetGoal: string;
  };
  setting?: {
    tone?: string;
    model?: string;
  };
}

export interface UsageInfo {
  model: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  estimatedCostJpy: number;
}

export interface RetrievedDocument {
  id: number;
  title: string;
  excerpt: string;
  chunkIndex: number;
  distanceScore?: number;
  similarityScore?: number;
}

export interface AdviceResponse {
  advice: string;
  usage: UsageInfo;
  retrievedDocuments: RetrievedDocument[];
}

export interface AuditLogSummaryItem {
  id: number;
  model: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  costJpy: number;
  latencyMs: number;
  createdAt: string;
}

export interface AuditLogListResponse {
  items: AuditLogSummaryItem[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface AuditLogDetailResponse {
  id: number;
  model: string;
  prompt: string;
  response: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  costJpy: number;
  latencyMs: number;
  createdAt: string;
}

export type KnowledgeAccessScope = "SHARED" | "ADMIN_ONLY";

export interface KnowledgeDocumentResponse {
  id: number;
  title: string;
  content: string;
  accessScope: KnowledgeAccessScope;
  allowedUsernames: string[];
  createdAt: string;
}

export interface KnowledgeDocumentListResponse {
  items: KnowledgeDocumentResponse[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface KnowledgeDocumentCreateRequest {
  title: string;
  content: string;
  accessScope: KnowledgeAccessScope;
  allowedUsernames: string[];
}

export interface KnowledgeReindexResponse {
  documentsProcessed: number;
  chunksProcessed: number;
  embeddingsUpdated: number;
  vectorSearchEnabled: boolean;
}

export interface KnowledgeReindexJobAcceptedResponse {
  jobId: string;
  status: string;
  acceptedAt: string;
}

export interface KnowledgeReindexJobStatusResponse {
  jobId: string;
  status: string;
  acceptedAt: string;
  startedAt?: string;
  completedAt?: string;
  knowledgeDocumentId?: number;
  result?: KnowledgeReindexResponse;
  errorMessage?: string;
}

export interface KnowledgeReindexJobListResponse {
  items: KnowledgeReindexJobStatusResponse[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface ApiErrorResponse {
  status?: number;
  message?: string;
  details?: string[];
}
