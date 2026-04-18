import axios from "axios";
import { env } from "../env";
import { clearAuthSession, getStoredAuthSession } from "../lib/auth";
import { ApiErrorResponse } from "../types/api";

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
  const requestUrl = config.url ?? "";
  if (requestUrl === "/v1/auth/token") {
    if (config.headers?.Authorization) {
      delete config.headers.Authorization;
    }
    return config;
  }

  const session = getStoredAuthSession();
  if (session) {
    config.headers.Authorization = `Bearer ${session.accessToken}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearAuthSession();
    }
    return Promise.reject(error);
  },
);

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
