import { apiClient } from "./client";
import {
  GroundednessCaseEvaluationResponse,
  PromptInjectionEvaluationResponse,
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

export async function fetchDefaultPromptInjectionEvaluation() {
  const response = await apiClient.get<PromptInjectionEvaluationResponse>("/v1/prompt-injection-evaluations/default");
  return response.data;
}

export async function fetchDefaultGroundednessEvaluation() {
  const response = await apiClient.get<GroundednessCaseEvaluationResponse>(
    "/v1/groundedness-evaluations/default",
    {
      timeout: 120000,
    },
  );
  return response.data;
}
