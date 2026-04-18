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
      title="ダッシュボード"
      description="リクエスト量、検索利用状況、再インデックス実行状況を簡易的に俯瞰します。"
    >
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="総リクエスト数" value="-" helper="監査ログ指標を接続すると表示されます" icon={<InsightsRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="平均レイテンシ" value="-" helper="監査ログから集計します" icon={<TravelExploreRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="平均コスト" value="-" helper="1回生成あたりの円換算コスト" icon={<PaidRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="再インデックス成功率" value="-" helper="正常完了したジョブの割合" icon={<RefreshRoundedIcon color="primary" />} />
        </Grid>
      </Grid>

      <SectionCard
        title="MVPダッシュボード"
        description="このページは最小構成です。主要画面の接続確認後に監査ログや再インデックス指標を集約します。"
      >
        <Stack spacing={1}>
          <Typography color="text.secondary">まずは `助言生成`、`ナレッジ`、`再インデックス`、`監査ログ` の画面で end-to-end の動作を確認してください。</Typography>
          <Typography color="text.secondary">その後に backend 集計値やクエリ結果を使って KPI カードを埋めます。</Typography>
        </Stack>
      </SectionCard>
    </PageScaffold>
  );
}
