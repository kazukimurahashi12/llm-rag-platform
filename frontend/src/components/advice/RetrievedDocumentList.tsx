import MenuBookRoundedIcon from "@mui/icons-material/MenuBookRounded";
import { Card, CardContent, Stack, Typography } from "@mui/material";
import { RetrievedDocument } from "../../types/api";
import { formatNumber } from "../../lib/format";

export function RetrievedDocumentList({ items }: { items: RetrievedDocument[] }) {
  if (items.length === 0) {
    return <Typography color="text.secondary">No supporting documents were returned.</Typography>;
  }

  return (
    <Stack spacing={1.5}>
      {items.map((item) => (
        <Card key={`${item.id}-${item.chunkIndex}`} variant="outlined">
          <CardContent>
            <Stack spacing={1.25}>
              <Stack direction="row" spacing={1} alignItems="center">
                <MenuBookRoundedIcon color="primary" fontSize="small" />
                <Typography fontWeight={700}>{item.title}</Typography>
              </Stack>
              <Typography color="text.secondary">{item.excerpt}</Typography>
              <Stack direction="row" spacing={2}>
                <Typography variant="body2" color="text.secondary">
                  Chunk #{item.chunkIndex}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Similarity {formatNumber(item.similarityScore, 3)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Distance {formatNumber(item.distanceScore, 3)}
                </Typography>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}
