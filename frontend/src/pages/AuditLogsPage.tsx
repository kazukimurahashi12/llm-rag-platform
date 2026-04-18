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
      title="Audit Logs"
      description="Inspect usage, latency, and cost records for generated advice. Protected endpoints use stored Basic credentials."
    >
      <SectionCard title="Filters" description="Narrow the log list by model before opening a detailed prompt/response record.">
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField select fullWidth label="Model" value={model} onChange={(event) => setModel(event.target.value)}>
              <MenuItem value="">All models</MenuItem>
              <MenuItem value="gpt-4o-mini">gpt-4o-mini</MenuItem>
              <MenuItem value="gpt-4o">gpt-4o</MenuItem>
            </TextField>
          </Grid>
        </Grid>
      </SectionCard>

      <SectionCard title="Audit log records" description="Newest records first. Open a row to inspect masked or full prompt content.">
        {listQuery.isLoading ? <LoadingState label="Loading audit logs..." /> : null}
        {listQuery.isError ? <ErrorState message={getApiErrorMessage(listQuery.error)} /> : null}
        {listQuery.data && listQuery.data.items.length === 0 ? (
          <EmptyState title="No audit logs found" body="Run advice generation or broaden the filter to populate this table." />
        ) : null}
        {listQuery.data ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Model</TableCell>
                <TableCell>Total tokens</TableCell>
                <TableCell>Cost</TableCell>
                <TableCell>Latency</TableCell>
                <TableCell>Created</TableCell>
                <TableCell align="right">Detail</TableCell>
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
                      Open
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : null}
      </SectionCard>

      <Dialog open={selectedId !== null} onClose={() => setSelectedId(null)} fullWidth maxWidth="md">
        <DialogTitle>Audit log detail</DialogTitle>
        <DialogContent>
          {detailQuery.isLoading ? <LoadingState label="Loading audit detail..." /> : null}
          {detailQuery.isError ? <ErrorState message={getApiErrorMessage(detailQuery.error)} /> : null}
          {detailQuery.data ? (
            <Stack spacing={2}>
              <Typography variant="subtitle2" color="text.secondary">
                {detailQuery.data.model} · {formatDateTime(detailQuery.data.createdAt)}
              </Typography>
              <Typography variant="h6">Prompt</Typography>
              <Typography sx={{ whiteSpace: "pre-wrap" }}>{detailQuery.data.prompt}</Typography>
              <Typography variant="h6">Response</Typography>
              <Typography sx={{ whiteSpace: "pre-wrap" }}>{detailQuery.data.response}</Typography>
              <Typography color="text.secondary">
                Tokens {formatNumber(detailQuery.data.totalTokens)} · Cost {formatCurrencyJpy(detailQuery.data.costJpy)} ·
                Latency {formatNumber(detailQuery.data.latencyMs)} ms
              </Typography>
            </Stack>
          ) : null}
        </DialogContent>
      </Dialog>
    </PageScaffold>
  );
}
