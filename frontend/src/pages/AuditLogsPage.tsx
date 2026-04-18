import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { fetchAuditLogDetail, fetchAuditLogs } from "../api/audit";
import { getApiErrorMessage } from "../api/client";
import { PageScaffold } from "../components/layout/PageScaffold";
import { EmptyState, ErrorState, LoadingState } from "../components/shared/FeedbackState";
import { SectionCard } from "../components/shared/SectionCard";
import { formatCurrencyJpy, formatDateTime, formatNumber } from "../lib/format";

export function AuditLogsPage() {
  const [model, setModel] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const listQuery = useQuery({
    queryKey: ["audit-logs", model],
    queryFn: () => fetchAuditLogs({ limit: 20, offset: 0, model: model || undefined }),
  });

  const detailQuery = useQuery({
    queryKey: ["audit-log-detail", selectedId],
    queryFn: () => fetchAuditLogDetail(selectedId!),
    enabled: selectedId !== null,
  });

  return (
    <PageScaffold
      title="監査ログ"
      description="生成された助言の使用量、レイテンシ、コストを確認します。保護APIには保存済みBearerトークンを使用します。"
    >
      <SectionCard title="絞り込み" description="モデルで絞り込んでから、詳細な入出力内容を確認できます。">
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField select fullWidth label="モデル" value={model} onChange={(event) => setModel(event.target.value)}>
              <MenuItem value="">すべて</MenuItem>
              <MenuItem value="gpt-4o-mini">gpt-4o-mini</MenuItem>
              <MenuItem value="gpt-4o">gpt-4o</MenuItem>
            </TextField>
          </Grid>
        </Grid>
      </SectionCard>

      <SectionCard title="監査ログ一覧" description="新しい順に表示します。行を開くと prompt / response の詳細を確認できます。">
        {listQuery.isLoading ? <LoadingState label="監査ログを読み込み中..." /> : null}
        {listQuery.isError ? <ErrorState message={getApiErrorMessage(listQuery.error)} /> : null}
        {listQuery.data && listQuery.data.items.length === 0 ? (
          <EmptyState title="監査ログがありません" body="助言生成を実行するか、絞り込み条件を見直してください。" />
        ) : null}
        {listQuery.data ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>モデル</TableCell>
                <TableCell>合計トークン</TableCell>
                <TableCell>コスト</TableCell>
                <TableCell>レイテンシ</TableCell>
                <TableCell>作成日時</TableCell>
                <TableCell align="right">詳細</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {listQuery.data.items.map((item) => (
                <TableRow key={item.id} hover>
                  <TableCell>{item.id}</TableCell>
                  <TableCell>{item.model}</TableCell>
                  <TableCell>{formatNumber(item.totalTokens)}</TableCell>
                  <TableCell>{formatCurrencyJpy(item.costJpy)}</TableCell>
                  <TableCell>{formatNumber(item.latencyMs)} ms</TableCell>
                  <TableCell>{formatDateTime(item.createdAt)}</TableCell>
                  <TableCell align="right">
                    <Button size="small" startIcon={<VisibilityRoundedIcon />} onClick={() => setSelectedId(item.id)}>
                      開く
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : null}
      </SectionCard>

      <Dialog open={selectedId !== null} onClose={() => setSelectedId(null)} fullWidth maxWidth="md">
        <DialogTitle>監査ログ詳細</DialogTitle>
        <DialogContent>
          {detailQuery.isLoading ? <LoadingState label="監査ログ詳細を読み込み中..." /> : null}
          {detailQuery.isError ? <ErrorState message={getApiErrorMessage(detailQuery.error)} /> : null}
          {detailQuery.data ? (
            <Stack spacing={2}>
              <Typography variant="subtitle2" color="text.secondary">
                {detailQuery.data.model} · {formatDateTime(detailQuery.data.createdAt)}
              </Typography>
              <Typography variant="h6">入力内容</Typography>
              <Typography sx={{ whiteSpace: "pre-wrap" }}>{detailQuery.data.prompt}</Typography>
              <Typography variant="h6">出力内容</Typography>
              <Typography sx={{ whiteSpace: "pre-wrap" }}>{detailQuery.data.response}</Typography>
              <Typography color="text.secondary">
                トークン {formatNumber(detailQuery.data.totalTokens)} · コスト {formatCurrencyJpy(detailQuery.data.costJpy)} ·
                レイテンシ {formatNumber(detailQuery.data.latencyMs)} ms
              </Typography>
            </Stack>
          ) : null}
        </DialogContent>
      </Dialog>
    </PageScaffold>
  );
}
