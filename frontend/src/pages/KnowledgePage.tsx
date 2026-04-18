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
      title="Knowledge"
      description="Manage RAG source documents, confirm visibility scope, and trigger document-level reindexing."
    >
      <SectionCard
        title="Register document"
        description="Protected by admin bearer token. Add a document to the knowledge base and make it available for retrieval."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => createMutation.mutate()} disabled={createMutation.isPending}>
            Add document
          </Button>
        }
      >
        {createMutation.isError ? <ErrorState message={getApiErrorMessage(createMutation.error)} /> : null}
        {createMutation.isSuccess ? <Alert severity="success">Knowledge document created.</Alert> : null}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField fullWidth label="Title" value={title} onChange={(event) => setTitle(event.target.value)} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField select fullWidth label="Access scope" value={accessScope} onChange={(event) => setAccessScope(event.target.value as "SHARED" | "ADMIN_ONLY")}>
              <MenuItem value="SHARED">SHARED</MenuItem>
              <MenuItem value="ADMIN_ONLY">ADMIN_ONLY</MenuItem>
            </TextField>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              multiline
              minRows={6}
              label="Content"
              value={content}
              onChange={(event) => setContent(event.target.value)}
            />
          </Grid>
        </Grid>
      </SectionCard>

      <SectionCard
        title="Documents"
        description="Review current RAG inputs and trigger embedding refresh jobs."
        action={
          <Button
            variant="outlined"
            startIcon={<RefreshRoundedIcon />}
            onClick={() => fullReindexMutation.mutate()}
            disabled={fullReindexMutation.isPending}
          >
            Reindex all
          </Button>
        }
      >
        {fullReindexMutation.isSuccess ? <Alert severity="success">Full reindex job accepted.</Alert> : null}
        {fullReindexMutation.isError ? <ErrorState message={getApiErrorMessage(fullReindexMutation.error)} /> : null}
        {singleReindexMutation.isError ? <ErrorState message={getApiErrorMessage(singleReindexMutation.error)} /> : null}
        {listQuery.isLoading ? <LoadingState label="Loading knowledge documents..." /> : null}
        {listQuery.isError ? <ErrorState message={getApiErrorMessage(listQuery.error)} /> : null}
        {listQuery.data && listQuery.data.items.length === 0 ? (
          <EmptyState title="No knowledge documents found" body="Register the first document to make the retrieval layer observable in the UI." />
        ) : null}
        {listQuery.data ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Title</TableCell>
                <TableCell>Scope</TableCell>
                <TableCell>Created</TableCell>
                <TableCell>Allowed users</TableCell>
                <TableCell align="right">Action</TableCell>
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
                      Reindex
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
