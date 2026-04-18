import InsightsRoundedIcon from "@mui/icons-material/InsightsRounded";
import PaidRoundedIcon from "@mui/icons-material/PaidRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import TravelExploreRoundedIcon from "@mui/icons-material/TravelExploreRounded";
import { Grid, Stack, Typography } from "@mui/material";
import { PageScaffold } from "../components/layout/PageScaffold";
import { MetricCard } from "../components/shared/MetricCard";
import { SectionCard } from "../components/shared/SectionCard";

export function DashboardPage() {
  return (
    <PageScaffold
      title="Dashboard"
      description="A lightweight operational overview for request volume, retrieval behavior, and reindex job activity."
    >
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="Total requests" value="-" helper="Connect audit metrics to populate" icon={<InsightsRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="Avg latency" value="-" helper="Measured from audit logs" icon={<TravelExploreRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="Avg cost" value="-" helper="JPY per generation" icon={<PaidRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="Reindex success" value="-" helper="Jobs completed successfully" icon={<RefreshRoundedIcon color="primary" />} />
        </Grid>
      </Grid>

      <SectionCard
        title="MVP dashboard"
        description="This page is intentionally light. It should aggregate audit and reindex metrics after the core screens are confirmed."
      >
        <Stack spacing={1}>
          <Typography color="text.secondary">Use `Advice`, `Knowledge`, `Reindex Jobs`, and `Audit Logs` to validate the end-to-end flow first.</Typography>
          <Typography color="text.secondary">Then backfill KPI cards from backend summaries or derived query calculations.</Typography>
        </Stack>
      </SectionCard>
    </PageScaffold>
  );
}
