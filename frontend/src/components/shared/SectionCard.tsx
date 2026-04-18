import { Card, CardContent, Stack, Typography } from "@mui/material";
import { PropsWithChildren, ReactNode } from "react";

interface SectionCardProps extends PropsWithChildren {
  title: string;
  description?: string;
  action?: ReactNode;
}

export function SectionCard({ title, description, action, children }: SectionCardProps) {
  return (
    <Card>
      <CardContent>
        <Stack spacing={2.5}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
            <div>
              <Typography variant="h6">{title}</Typography>
              {description ? (
                <Typography color="text.secondary" sx={{ mt: 0.5 }}>
                  {description}
                </Typography>
              ) : null}
            </div>
            {action}
          </Stack>
          {children}
        </Stack>
      </CardContent>
    </Card>
  );
}
