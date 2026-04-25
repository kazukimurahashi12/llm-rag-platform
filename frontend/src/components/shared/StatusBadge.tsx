import { Chip } from "@mui/material";

interface StatusBadgeProps {
  status: string;
}

const colorMap: Record<string, "success" | "info" | "error" | "warning" | "default"> = {
  COMPLETED: "success",
  SUCCESS: "success",
  RUNNING: "info",
  QUEUED: "warning",
  FAILED: "error",
  OK: "success",
  MISMATCH: "error",
  SHARED: "success",
  ADMIN_ONLY: "warning",
};

const labelMap: Record<string, string> = {
  COMPLETED: "完了",
  SUCCESS: "成功",
  RUNNING: "実行中",
  QUEUED: "待機中",
  FAILED: "失敗",
  OK: "一致",
  MISMATCH: "不一致",
  SHARED: "共有",
  ADMIN_ONLY: "管理者のみ",
};

export function StatusBadge({ status }: StatusBadgeProps) {
  return <Chip color={colorMap[status] ?? "default"} label={labelMap[status] ?? status} size="small" variant="filled" />;
}
