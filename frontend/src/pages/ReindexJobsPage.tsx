import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import ReplayRoundedIcon from "@mui/icons-material/ReplayRounded";
import {
  Alert,
  Button,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { getApiErrorMessage } from "../api/client";
import { deleteReindexJob, fetchReindexJobs, retryReindexJob } from "../api/reindex";
import { PageScaffold } from "../components/layout/PageScaffold";
import { EmptyState, ErrorState, LoadingState } from "../components/shared/FeedbackState";
import { SectionCard } from "../components/shared/SectionCard";
import { StatusBadge } from "../components/shared/StatusBadge";
import { formatDateTime } from "../lib/format";

export function ReindexJobsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");

  const listQuery = useQuery({
    queryKey: ["reindex-jobs", status],
    queryFn: () =>
      fetchReindexJobs({
        limit: 20,
        offset: 0,
        sortBy: "acceptedAt",
        sortDirection: "desc",
        status: status || undefined,
      }),
    refetchInterval: 5000,
  });

  const retryMutation = useMutation({
    mutationFn: (jobId: string) => retryReindexJob(jobId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["reindex-jobs"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (jobId: string) => deleteReindexJob(jobId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["reindex-jobs"] });
    },
  });

  return (
    <PageScaffold
      title="Reindex Jobs"
      description="Monitor asynchronous embedding refresh jobs with polling, filtering, retry, and delete actions."
    >
      <SectionCard title="Job filters" description="Use status to focus on failed or in-flight jobs.">
        <TextField select label="Status" value={status} onChange={(event) => setStatus(event.target.value)} sx={{ width: 280 }}>
          <MenuItem value="">All</MenuItem>
          <MenuItem value="QUEUED">QUEUED</MenuItem>
          <MenuItem value="RUNNING">RUNNING</MenuItem>
          <MenuItem value="COMPLETED">COMPLETED</MenuItem>
          <MenuItem value="FAILED">FAILED</MenuItem>
        </TextField>
      </SectionCard>

      <SectionCard title="Job history" description="Polling every 5 seconds keeps active jobs visible without manual refresh.">
        {retryMutation.isSuccess ? <Alert severity="success">Retry job accepted.</Alert> : null}
        {deleteMutation.isSuccess ? <Alert severity="success">Job deleted.</Alert> : null}
        {listQuery.isLoading ? <LoadingState label="Loading reindex jobs..." /> : null}
        {listQuery.isError ? <ErrorState message={getApiErrorMessage(listQuery.error)} /> : null}
        {retryMutation.isError ? <ErrorState message={getApiErrorMessage(retryMutation.error)} /> : null}
        {deleteMutation.isError ? <ErrorState message={getApiErrorMessage(deleteMutation.error)} /> : null}
        {listQuery.data && listQuery.data.items.length === 0 ? (
          <EmptyState title="No reindex jobs found" body="Trigger a reindex from the Knowledge screen to populate this job history." />
        ) : null}
        {listQuery.data ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Job ID</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Document</TableCell>
                <TableCell>Accepted</TableCell>
                <TableCell>Completed</TableCell>
                <TableCell>Error</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {listQuery.data.items.map((item) => (
                <TableRow key={item.jobId} hover>
                  <TableCell>{item.jobId}</TableCell>
                  <TableCell>
                    <StatusBadge status={item.status} />
                  </TableCell>
                  <TableCell>{item.knowledgeDocumentId ?? "All documents"}</TableCell>
                  <TableCell>{formatDateTime(item.acceptedAt)}</TableCell>
                  <TableCell>{formatDateTime(item.completedAt)}</TableCell>
                  <TableCell>{item.errorMessage ?? "-"}</TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                      <Button
                        size="small"
                        startIcon={<ReplayRoundedIcon />}
                        onClick={() => retryMutation.mutate(item.jobId)}
                        disabled={item.status !== "FAILED"}
                      >
                        Retry
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        startIcon={<DeleteOutlineRoundedIcon />}
                        onClick={() => deleteMutation.mutate(item.jobId)}
                        disabled={item.status === "RUNNING"}
                      >
                        Delete
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : null}
      </SectionCard>
    </PageScaffold>
  );
}
