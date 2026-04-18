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
import { getStoredAuthSession } from "../lib/auth";
import { formatDateTime } from "../lib/format";

export function ReindexJobsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const authSession = getStoredAuthSession();

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
    enabled: Boolean(authSession),
    refetchInterval: authSession ? 5000 : false,
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
      title="再インデックス"
      description="非同期の埋め込み再生成ジョブを監視し、絞り込み、再試行、削除を行います。"
    >
      <SectionCard title="ジョブ絞り込み" description="状態で絞り込むと、失敗中や実行中のジョブを追いやすくなります。">
        <TextField select label="状態" value={status} onChange={(event) => setStatus(event.target.value)} sx={{ width: 280 }}>
          <MenuItem value="">すべて</MenuItem>
          <MenuItem value="QUEUED">待機中</MenuItem>
          <MenuItem value="RUNNING">実行中</MenuItem>
          <MenuItem value="COMPLETED">完了</MenuItem>
          <MenuItem value="FAILED">失敗</MenuItem>
        </TextField>
      </SectionCard>

      <SectionCard title="ジョブ履歴" description="5秒ごとのポーリングで、実行中ジョブを手動更新なしで追跡します。">
        {!authSession ? (
          <EmptyState
            title="サインインが必要です"
            body="ヘッダーから JWT サインインしてから、保護された再インデックス情報を読み込んでください。"
          />
        ) : null}
        {retryMutation.isSuccess ? <Alert severity="success">再試行ジョブを受け付けました。</Alert> : null}
        {deleteMutation.isSuccess ? <Alert severity="success">ジョブを削除しました。</Alert> : null}
        {listQuery.isLoading ? <LoadingState label="再インデックスジョブを読み込み中..." /> : null}
        {listQuery.isError ? <ErrorState message={getApiErrorMessage(listQuery.error)} /> : null}
        {retryMutation.isError ? <ErrorState message={getApiErrorMessage(retryMutation.error)} /> : null}
        {deleteMutation.isError ? <ErrorState message={getApiErrorMessage(deleteMutation.error)} /> : null}
        {listQuery.data && listQuery.data.items.length === 0 ? (
          <EmptyState title="再インデックスジョブはありません" body="ナレッジ画面から再インデックスを実行すると、ここに履歴が表示されます。" />
        ) : null}
        {listQuery.data ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ジョブID</TableCell>
                <TableCell>状態</TableCell>
                <TableCell>対象文書</TableCell>
                <TableCell>受付日時</TableCell>
                <TableCell>完了日時</TableCell>
                <TableCell>エラー</TableCell>
                <TableCell align="right">操作</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {listQuery.data.items.map((item) => (
                <TableRow key={item.jobId} hover>
                  <TableCell>{item.jobId}</TableCell>
                  <TableCell>
                    <StatusBadge status={item.status} />
                  </TableCell>
                  <TableCell>{item.knowledgeDocumentId ?? "全件"}</TableCell>
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
                        再試行
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        startIcon={<DeleteOutlineRoundedIcon />}
                        onClick={() => deleteMutation.mutate(item.jobId)}
                        disabled={item.status === "RUNNING"}
                      >
                        削除
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
