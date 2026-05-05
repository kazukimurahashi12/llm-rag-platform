import { z } from "zod";
import { apiClient } from "./client";
import { AdviceRequest, AdviceResponse } from "../types/api";

const adviceSchema = z.object({
  advice: z.string(),
  aceAnalysis: z.object({
    primaryCategory: z.enum(["ABILITY", "CULTURE", "EXPECTATION"]),
    reason: z.string(),
  }),
  usage: z.object({
    model: z.string(),
    promptTokens: z.number(),
    completionTokens: z.number(),
    totalTokens: z.number(),
    estimatedCostJpy: z.number(),
  }),
  retrievedDocuments: z.array(
    z.object({
      id: z.number(),
      title: z.string(),
      excerpt: z.string(),
      chunkIndex: z.number(),
      aceCategory: z.enum(["ABILITY", "CULTURE", "EXPECTATION"]).optional(),
      distanceScore: z.number().optional(),
      similarityScore: z.number().optional(),
    }),
  ),
});

export async function generateAdvice(payload: AdviceRequest): Promise<AdviceResponse> {
  const response = await apiClient.post("/v1/management/advice", payload);
  return adviceSchema.parse(response.data);
}
