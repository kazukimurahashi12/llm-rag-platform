import AddRoundedIcon from "@mui/icons-material/AddRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import {
  Alert,
  Button,
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
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { createKnowledgeDocument, fetchKnowledgeDocuments, reindexAllDocuments, reindexDocument } from "../api/knowledge";
import { getApiErrorMessage } from "../api/client";
import { PageScaffold } from "../components/layout/PageScaffold";
import { EmptyState, ErrorState, LoadingState } from "../components/shared/FeedbackState";
import { SectionCard } from "../components/shared/SectionCard";
import { StatusBadge } from "../components/shared/StatusBadge";
import { formatDateTime } from "../lib/format";

export function KnowledgePage() {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [accessScope, setAccessScope] = useState<"SHARED" | "ADMIN_ONLY">("SHARED");

  const listQuery = useQuery({
    queryKey: ["knowledge-documents"],
    queryFn: () => fetchKnowledgeDocuments({ limit: 20, offset: 0 }),
  });

  const createMutation = useMutation({
    mutationFn: () => createKnowledgeDocument({ title, content, accessScope, allowedUsernames: [] }),
    onSuccess: async () => {
      setTitle("");
      setContent("");
      await queryClient.invalidateQueries({ queryKey: ["knowledge-documents"] });
    },
  });

  const fullReindexMutation = useMutation({
    mutationFn: reindexAllDocuments,
  });

  const singleReindexMutation = useMutation({
    mutationFn: (knowledgeDocumentId: number) => reindexDocument(knowledgeDocumentId),
  });

  return (
    <PageScaffold
      title="ナレッジ"
      description="RAG 用の文書を管理し、公開範囲を確認しながら再インデックスを実行します。"
    >
      <SectionCard
        title="文書を登録"
        description="管理者トークンで保護されています。ナレッジベースに文書を追加して検索対象にします。"
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => createMutation.mutate()} disabled={createMutation.isPending}>
            文書を追加
          </Button>
        }
      >
        {createMutation.isError ? <ErrorState message={getApiErrorMessage(createMutation.error)} /> : null}
        {createMutation.isSuccess ? <Alert severity="success">文書を登録しました。</Alert> : null}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField fullWidth label="タイトル" value={title} onChange={(event) => setTitle(event.target.value)} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField select fullWidth label="公開範囲" value={accessScope} onChange={(event) => setAccessScope(event.target.value as "SHARED" | "ADMIN_ONLY")}>
              <MenuItem value="SHARED">共有</MenuItem>
              <MenuItem value="ADMIN_ONLY">管理者のみ</MenuItem>
            </TextField>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              multiline
              minRows={6}
              label="本文"
              value={content}
              onChange={(event) => setContent(event.target.value)}
            />
          </Grid>
        </Grid>
      </SectionCard>

      <SectionCard
        title="文書一覧"
        description="現在の RAG 入力文書を確認し、埋め込み再生成ジョブを実行します。"
        action={
          <Button
            variant="outlined"
            startIcon={<RefreshRoundedIcon />}
            onClick={() => fullReindexMutation.mutate()}
            disabled={fullReindexMutation.isPending}
          >
            全件再インデックス
          </Button>
        }
      >
        {fullReindexMutation.isSuccess ? <Alert severity="success">全件再インデックスを受け付けました。</Alert> : null}
        {fullReindexMutation.isError ? <ErrorState message={getApiErrorMessage(fullReindexMutation.error)} /> : null}
        {singleReindexMutation.isError ? <ErrorState message={getApiErrorMessage(singleReindexMutation.error)} /> : null}
        {listQuery.isLoading ? <LoadingState label="ナレッジ文書を読み込み中..." /> : null}
        {listQuery.isError ? <ErrorState message={getApiErrorMessage(listQuery.error)} /> : null}
        {listQuery.data && listQuery.data.items.length === 0 ? (
          <EmptyState title="ナレッジ文書がありません" body="最初の文書を登録すると、検索・根拠表示の動作を確認できます。" />
        ) : null}
        {listQuery.data ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>タイトル</TableCell>
                <TableCell>公開範囲</TableCell>
                <TableCell>作成日時</TableCell>
                <TableCell>許可ユーザー</TableCell>
                <TableCell align="right">操作</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {listQuery.data.items.map((item) => (
                <TableRow key={item.id} hover>
                  <TableCell>
                    <Stack spacing={0.5}>
                      <Typography fontWeight={700}>{item.title}</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 540 }}>
                        {item.content.slice(0, 120)}
                        {item.content.length > 120 ? "..." : ""}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={item.accessScope} />
                  </TableCell>
                  <TableCell>{formatDateTime(item.createdAt)}</TableCell>
                  <TableCell>{item.allowedUsernames.join(", ") || "-"}</TableCell>
                  <TableCell align="right">
                    <Button size="small" onClick={() => singleReindexMutation.mutate(item.id)}>
                      再インデックス
                    </Button>
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
