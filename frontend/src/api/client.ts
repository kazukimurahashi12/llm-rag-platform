import axios from "axios";
import { env } from "../env";
import { buildBasicAuthHeader, getStoredCredentials } from "../lib/auth";
import { ApiErrorResponse } from "../types/api";

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
  const credentials = getStoredCredentials();
  if (credentials) {
    config.headers.Authorization = buildBasicAuthHeader(credentials);
  }
  return config;
});

export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return (
      error.response?.data?.message ??
      error.response?.data?.details?.join(", ") ??
      error.message
    );
  }

  if (error instanceof Error) {
    return error.message;
  }

  return "Unexpected error";
}
