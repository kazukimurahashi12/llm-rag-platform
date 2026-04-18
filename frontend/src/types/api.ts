export type AdviceTone = "empathetic" | "direct" | "supportive";

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
