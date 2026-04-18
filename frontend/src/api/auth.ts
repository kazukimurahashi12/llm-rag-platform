import { apiClient } from "./client";
import { AuthTokenRequest, AuthTokenResponse } from "../types/api";

export async function issueAuthToken(payload: AuthTokenRequest) {
  const response = await apiClient.post<AuthTokenResponse>("/v1/auth/token", payload);
  return response.data;
}
