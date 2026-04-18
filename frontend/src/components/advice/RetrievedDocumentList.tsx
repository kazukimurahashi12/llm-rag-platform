import MenuBookRoundedIcon from "@mui/icons-material/MenuBookRounded";
import { Card, CardContent, Divider, Stack, Typography } from "@mui/material";
import { RetrievedDocument } from "../../types/api";
import { formatNumber } from "../../lib/format";

export function RetrievedDocumentList({ items }: { items: RetrievedDocument[] }) {
  if (items.length === 0) {
    return <Typography color="text.secondary">根拠として参照された文書はありません。</Typography>;
  }

  const groupedItems = items.reduce<Map<number, RetrievedDocument[]>>((groups, item) => {
    const currentItems = groups.get(item.id) ?? [];
    currentItems.push(item);
    groups.set(item.id, currentItems);
    return groups;
  }, new Map());

  return (
    <Stack spacing={1.5}>
      {Array.from(groupedItems.entries()).map(([documentId, documentChunks]) => {
        const [firstChunk] = documentChunks;

        return (
        <Card key={documentId} variant="outlined">
          <CardContent>
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <MenuBookRoundedIcon color="primary" fontSize="small" />
                <Typography fontWeight={700}>{firstChunk.title}</Typography>
              </Stack>
              <Typography color="text.secondary" variant="body2">
                この文書から {documentChunks.length} 件のチャンクが参照されています。
              </Typography>
              <Stack spacing={1.5}>
                {documentChunks.map((item, index) => (
                  <Stack key={`${item.id}-${item.chunkIndex}`} spacing={1}>
                    {index > 0 ? <Divider /> : null}
                    <Typography color="text.secondary">
                      {formatChunkExcerpt(documentChunks, index)}
                    </Typography>
                    <Stack direction="row" spacing={2}>
                      <Typography variant="body2" color="text.secondary">
                        チャンク #{item.chunkIndex}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        類似度 {formatNumber(item.similarityScore, 3)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        距離 {formatNumber(item.distanceScore, 3)}
                      </Typography>
                    </Stack>
                  </Stack>
                ))}
              </Stack>
            </Stack>
          </CardContent>
        </Card>
        );
      })}
    </Stack>
  );
}

function formatChunkExcerpt(chunks: RetrievedDocument[], index: number): string {
  const currentExcerpt = chunks[index]?.excerpt ?? "";
  if (index === 0) {
    return currentExcerpt;
  }

  const previousExcerpt = chunks[index - 1]?.excerpt ?? "";
  const overlapLength = findOverlapLength(previousExcerpt, currentExcerpt);
  if (overlapLength === 0) {
    return currentExcerpt;
  }

  const trimmedExcerpt = currentExcerpt.slice(overlapLength).trimStart();
  if (!trimmedExcerpt) {
    return currentExcerpt;
  }

  return trimmedExcerpt;
}

function findOverlapLength(previousText: string, currentText: string): number {
  const maxOverlap = Math.min(previousText.length, currentText.length, 60);

  for (let length = maxOverlap; length >= 12; length -= 1) {
    const previousSuffix = previousText.slice(-length);
    const currentPrefix = currentText.slice(0, length);
    if (previousSuffix === currentPrefix) {
      return length;
    }
  }

  return 0;
}
