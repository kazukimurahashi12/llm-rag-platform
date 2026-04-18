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
  SHARED: "success",
  ADMIN_ONLY: "warning",
};

export function StatusBadge({ status }: StatusBadgeProps) {
  return <Chip color={colorMap[status] ?? "default"} label={status} size="small" variant="filled" />;
}
