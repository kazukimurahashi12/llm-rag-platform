import { apiClient } from "./client";
import { DashboardSummaryResponse } from "../types/api";

export async function fetchDashboardSummary() {
  const response = await apiClient.get<DashboardSummaryResponse>("/v1/dashboard/summary");
  return response.data;
}
