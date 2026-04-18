import { Box, Paper, Stack, Typography } from "@mui/material";
import { PropsWithChildren, ReactNode } from "react";

interface PageScaffoldProps extends PropsWithChildren {
  title: string;
  description: string;
  rightPanel?: ReactNode;
}

export function PageScaffold({ title, description, rightPanel, children }: PageScaffoldProps) {
  return (
    <Stack spacing={2.5}>
      <Box>
        <Typography variant="h4">{title}</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5 }}>
          {description}
        </Typography>
      </Box>

      <Box
        sx={{
          display: "grid",
          gap: 2.5,
          gridTemplateColumns: rightPanel ? "minmax(0, 1.7fr) minmax(360px, 1fr)" : "minmax(0, 1fr)",
          alignItems: "start",
        }}
      >
        <Stack spacing={2.5}>{children}</Stack>
        {rightPanel ? (
          <Paper sx={{ p: 2.5, borderRadius: 6, position: "sticky", top: 24 }}>{rightPanel}</Paper>
        ) : null}
      </Box>
    </Stack>
  );
}
