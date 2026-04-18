import SmartToyOutlinedIcon from "@mui/icons-material/SmartToyOutlined";
import TopicRoundedIcon from "@mui/icons-material/TopicRounded";
import { Box, Divider, Stack, Typography } from "@mui/material";
import { AdviceResponse } from "../../types/api";
import { formatCurrencyJpy, formatNumber } from "../../lib/format";
import { RetrievedDocumentList } from "./RetrievedDocumentList";

interface AdviceResponsePanelProps {
  data?: AdviceResponse | null;
}

export function AdviceResponsePanel({ data }: AdviceResponsePanelProps) {
  if (!data) {
    return (
      <Stack spacing={2.5}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <SmartToyOutlinedIcon color="primary" />
          <div>
            <Typography variant="h6">AI Workspace</Typography>
            <Typography color="text.secondary">Generate advice to inspect grounded output, sources, and usage.</Typography>
          </div>
        </Stack>
      </Stack>
    );
  }

  return (
    <Stack spacing={3}>
      <Box
        sx={{
          bgcolor: "rgba(65, 168, 98, 0.08)",
          borderRadius: 5,
          p: 2.5,
        }}
      >
        <Stack direction="row" spacing={1.25} alignItems="center" sx={{ mb: 1.5 }}>
          <SmartToyOutlinedIcon color="primary" />
          <Typography variant="h6">Advice Output</Typography>
        </Stack>
        <Typography sx={{ whiteSpace: "pre-wrap", lineHeight: 1.8 }}>{data.advice}</Typography>
      </Box>

      <Stack spacing={1.25}>
        <Typography variant="subtitle1">Usage</Typography>
        <Stack direction="row" flexWrap="wrap" gap={1}>
          <UsagePill label="Model" value={data.usage.model} />
          <UsagePill label="Prompt" value={formatNumber(data.usage.promptTokens)} />
          <UsagePill label="Completion" value={formatNumber(data.usage.completionTokens)} />
          <UsagePill label="Total tokens" value={formatNumber(data.usage.totalTokens)} />
          <UsagePill label="Estimated cost" value={formatCurrencyJpy(data.usage.estimatedCostJpy)} />
        </Stack>
      </Stack>

      <Divider />

      <Stack spacing={1.5}>
        <Stack direction="row" spacing={1.25} alignItems="center">
          <TopicRoundedIcon color="primary" />
          <Typography variant="subtitle1">Retrieved documents</Typography>
        </Stack>
        <RetrievedDocumentList items={data.retrievedDocuments} />
      </Stack>
    </Stack>
  );
}

function UsagePill({ label, value }: { label: string; value: string }) {
  return (
    <Box
      sx={{
        px: 1.5,
        py: 1,
        borderRadius: 999,
        bgcolor: "rgba(31, 122, 140, 0.08)",
      }}
    >
      <Typography color="text.secondary" variant="caption" display="block">
        {label}
      </Typography>
      <Typography fontWeight={700}>{value}</Typography>
    </Box>
  );
}
