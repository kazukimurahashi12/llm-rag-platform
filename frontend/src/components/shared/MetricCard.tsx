import { Card, CardContent, Stack, Typography } from "@mui/material";
import { ReactNode } from "react";

interface MetricCardProps {
  label: string;
  value: string;
  helper?: string;
  icon?: ReactNode;
}

export function MetricCard({ label, value, helper, icon }: MetricCardProps) {
  return (
    <Card>
      <CardContent>
        <Stack spacing={1.5}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography color="text.secondary" variant="body2">
              {label}
            </Typography>
            {icon}
          </Stack>
          <Typography variant="h4">{value}</Typography>
          {helper ? (
            <Typography color="text.secondary" variant="body2">
              {helper}
            </Typography>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  );
}
