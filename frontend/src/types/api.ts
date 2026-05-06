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
  averageGroundednessScore: number;
  groundedResponses: number;
  lowGroundednessResponses: number;
  groundednessFallbackResponses: number;
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
  abilityKnowledgeDocuments: number;
  cultureKnowledgeDocuments: number;
  expectationKnowledgeDocuments: number;
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

export interface GroundednessCaseEvaluationCaseResult {
  label?: string;
  situation: string;
  targetGoal: string;
  advice: string;
  expectedStatus: "GROUNDED" | "LOW_GROUNDEDNESS" | "NO_EVIDENCE" | "PARSE_FAILED" | "JUDGE_ERROR";
  actualStatus: "GROUNDED" | "LOW_GROUNDEDNESS" | "NO_EVIDENCE" | "PARSE_FAILED" | "JUDGE_ERROR";
  expectedFallbackApplied: boolean;
  fallbackApplied: boolean;
  matched: boolean;
  groundednessScore: number;
  reason: string;
}

export interface GroundednessCaseEvaluationResponse {
  totalCases: number;
  matchedCases: number;
  groundedCases: number;
  lowGroundednessCases: number;
  noEvidenceCases: number;
  parseFailedCases: number;
  judgeErrorCases: number;
  fallbackAppliedCases: number;
  accuracy: number;
  averageGroundednessScore: number;
  caseResults: GroundednessCaseEvaluationCaseResult[];
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
  aceCategory?: AceCategory;
  distanceScore?: number;
  similarityScore?: number;
}

export type AceCategory = "ABILITY" | "CULTURE" | "EXPECTATION";

export interface AceAnalysis {
  primaryCategory: AceCategory;
  reason: string;
}

export interface GroundednessEvaluation {
  groundednessScore: number;
  reason: string;
  status: "GROUNDED" | "LOW_GROUNDEDNESS" | "NO_EVIDENCE" | "PARSE_FAILED" | "JUDGE_ERROR";
  fallbackApplied: boolean;
}

export interface AdviceResponse {
  advice: string;
  aceAnalysis: AceAnalysis;
  groundednessEvaluation: GroundednessEvaluation;
  usage: UsageInfo;
  retrievedDocuments: RetrievedDocument[];
}

export interface AuditLogSummaryItem {
  id: number;
  model: string;
  groundednessEvaluation: GroundednessEvaluation;
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
  groundednessEvaluation: GroundednessEvaluation;
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
  aceCategory: AceCategory;
  accessScope: KnowledgeAccessScope;
  allowedUsernames: string[];
  createdAt: string;
  updatedAt: string;
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
  aceCategory: AceCategory;
  accessScope: KnowledgeAccessScope;
  allowedUsernames: string[];
}

export interface KnowledgeDocumentUpdateRequest {
  title: string;
  content: string;
  aceCategory: AceCategory;
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
