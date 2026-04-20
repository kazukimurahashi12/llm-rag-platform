import { apiClient } from "./client";
import {
  RetrievalEvaluationComparisonRequest,
  RetrievalEvaluationComparisonResponse,
  RetrievalEvaluationResponse,
} from "../types/api";

export async function fetchDefaultRetrievalEvaluation(topK = 3) {
  const response = await apiClient.get<RetrievalEvaluationResponse>("/v1/retrieval-evaluations/default", {
    params: { topK },
  });
  return response.data;
}

export async function compareRetrievalEvaluations(payload: RetrievalEvaluationComparisonRequest) {
  const response = await apiClient.post<RetrievalEvaluationComparisonResponse>(
    "/v1/retrieval-evaluations/comparisons",
    payload,
  );
  return response.data;
}
