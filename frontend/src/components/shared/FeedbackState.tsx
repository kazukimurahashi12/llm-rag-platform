import ErrorOutlineRoundedIcon from "@mui/icons-material/ErrorOutlineRounded";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { Alert, CircularProgress, Paper, Stack, Typography } from "@mui/material";

export function LoadingState({ label }: { label: string }) {
  return (
    <Paper sx={{ p: 3, borderRadius: 5 }}>
      <Stack direction="row" spacing={1.5} alignItems="center">
        <CircularProgress size={22} />
        <Typography>{label}</Typography>
      </Stack>
    </Paper>
  );
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <Paper sx={{ p: 4, borderRadius: 5, bgcolor: "rgba(255,255,255,0.8)" }}>
      <Stack spacing={1.5} alignItems="flex-start">
        <InfoOutlinedIcon color="primary" />
        <Typography variant="h6">{title}</Typography>
        <Typography color="text.secondary">{body}</Typography>
      </Stack>
    </Paper>
  );
}

export function ErrorState({ message }: { message: string }) {
  return (
    <Alert icon={<ErrorOutlineRoundedIcon />} severity="error">
      {message}
    </Alert>
  );
}
