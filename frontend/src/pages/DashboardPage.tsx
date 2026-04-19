import InsightsRoundedIcon from "@mui/icons-material/InsightsRounded";
import PaidRoundedIcon from "@mui/icons-material/PaidRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import TravelExploreRoundedIcon from "@mui/icons-material/TravelExploreRounded";
import { Grid, Stack, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { fetchDashboardSummary } from "../api/dashboard";
import { getApiErrorMessage } from "../api/client";
import { PageScaffold } from "../components/layout/PageScaffold";
import { ErrorState, LoadingState } from "../components/shared/FeedbackState";
import { MetricCard } from "../components/shared/MetricCard";
import { SectionCard } from "../components/shared/SectionCard";
import { formatCurrencyJpy, formatNumber } from "../lib/format";

export function DashboardPage() {
  const summaryQuery = useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: fetchDashboardSummary,
    refetchInterval: 30000,
  });

  const summary = summaryQuery.data;

  return (
    <PageScaffold
      title="ダッシュボード"
      description="リクエスト量、検索利用状況、再インデックス実行状況を簡易的に俯瞰します。"
    >
      {summaryQuery.isLoading ? <LoadingState label="ダッシュボード集計を読み込み中..." /> : null}
      {summaryQuery.isError ? <ErrorState message={getApiErrorMessage(summaryQuery.error)} /> : null}
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard
            label="総リクエスト数"
            value={formatNumber(summary?.totalAdviceRequests)}
            helper="監査ログに保存された advice 実行回数"
            icon={<InsightsRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard
            label="平均レイテンシ"
            value={summary ? `${formatNumber(summary.averageLatencyMs, 1)} ms` : "-"}
            helper="監査ログから集計した平均処理時間"
            icon={<TravelExploreRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard
            label="平均コスト"
            value={formatCurrencyJpy(summary?.averageCostJpy)}
            helper="1回生成あたりの円換算コスト"
            icon={<PaidRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard
            label="再インデックス成功率"
            value={summary ? `${formatNumber(summary.reindexSuccessRate * 100, 1)}%` : "-"}
            helper={
              summary
                ? `完了 ${formatNumber(summary.completedReindexJobs)} 件 / 失敗 ${formatNumber(summary.failedReindexJobs)} 件`
                : "正常完了したジョブの割合"
            }
            icon={<RefreshRoundedIcon color="primary" />}
          />
        </Grid>
      </Grid>

      <SectionCard
        title="運用サマリー"
        description="監査ログと再インデックスジョブから主要 KPI を集計して表示します。"
      >
        <Stack spacing={1}>
          <Typography color="text.secondary">総リクエスト数、平均レイテンシ、平均コストは `audit_logs` から集計しています。</Typography>
          <Typography color="text.secondary">再インデックス成功率は `knowledge_reindex_jobs` の `COMPLETED` と `FAILED` から算出しています。</Typography>
        </Stack>
      </SectionCard>
    </PageScaffold>
  );
}
