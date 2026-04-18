import { apiClient } from "./client";
import {
  KnowledgeDocumentCreateRequest,
  KnowledgeDocumentListResponse,
  KnowledgeDocumentResponse,
  KnowledgeReindexJobAcceptedResponse,
} from "../types/api";

export async function fetchKnowledgeDocuments(params: Record<string, string | number | undefined>) {
  const response = await apiClient.get<KnowledgeDocumentListResponse>("/v1/knowledge-documents", { params });
  return response.data;
}

export async function createKnowledgeDocument(payload: KnowledgeDocumentCreateRequest) {
  const response = await apiClient.post<KnowledgeDocumentResponse>("/v1/knowledge-documents", payload);
  return response.data;
}

export async function reindexAllDocuments() {
  const response = await apiClient.post<KnowledgeReindexJobAcceptedResponse>("/v1/knowledge-documents/reindex");
  return response.data;
}

export async function reindexDocument(knowledgeDocumentId: number) {
  const response = await apiClient.post<KnowledgeReindexJobAcceptedResponse>(
    `/v1/knowledge-documents/${knowledgeDocumentId}/reindex`,
  );
  return response.data;
}
