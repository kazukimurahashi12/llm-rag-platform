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
            <Typography variant="h6">AIワークスペース</Typography>
            <Typography color="text.secondary">助言を生成すると、回答・根拠・使用量をここで確認できます。</Typography>
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
          <Typography variant="h6">助言結果</Typography>
        </Stack>
        <Typography sx={{ whiteSpace: "pre-wrap", lineHeight: 1.8 }}>{data.advice}</Typography>
      </Box>

      <Stack spacing={1.25}>
        <Typography variant="subtitle1">使用量</Typography>
        <Stack direction="row" flexWrap="wrap" gap={1}>
          <UsagePill label="モデル" value={data.usage.model} />
          <UsagePill label="入力トークン" value={formatNumber(data.usage.promptTokens)} />
          <UsagePill label="出力トークン" value={formatNumber(data.usage.completionTokens)} />
          <UsagePill label="合計トークン" value={formatNumber(data.usage.totalTokens)} />
          <UsagePill label="推定コスト" value={formatCurrencyJpy(data.usage.estimatedCostJpy)} />
        </Stack>
      </Stack>

      <Divider />

      <Stack spacing={1.5}>
        <Stack direction="row" spacing={1.25} alignItems="center">
          <TopicRoundedIcon color="primary" />
          <Typography variant="subtitle1">参照文書</Typography>
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
