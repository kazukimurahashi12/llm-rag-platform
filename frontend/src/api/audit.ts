import { apiClient } from "./client";
import { AuditLogDetailResponse, AuditLogListResponse } from "../types/api";

export async function fetchAuditLogs(params: Record<string, string | number | undefined>) {
  const response = await apiClient.get<AuditLogListResponse>("/v1/audit-logs", { params });
  return response.data;
}

export async function fetchAuditLogDetail(auditLogId: number) {
  const response = await apiClient.get<AuditLogDetailResponse>(`/v1/audit-logs/${auditLogId}`);
  return response.data;
}
