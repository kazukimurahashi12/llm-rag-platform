import { apiClient } from "./client";
import { KnowledgeReindexJobAcceptedResponse, KnowledgeReindexJobListResponse } from "../types/api";

export async function fetchReindexJobs(params: Record<string, string | number | undefined>) {
  const response = await apiClient.get<KnowledgeReindexJobListResponse>("/v1/knowledge-documents/reindex-jobs", {
    params,
  });
  return response.data;
}

export async function retryReindexJob(jobId: string) {
  const response = await apiClient.post<KnowledgeReindexJobAcceptedResponse>(
    `/v1/knowledge-documents/reindex-jobs/${jobId}/retry`,
  );
  return response.data;
}

export async function deleteReindexJob(jobId: string) {
  await apiClient.delete(`/v1/knowledge-documents/reindex-jobs/${jobId}`);
}
