import BarChartRoundedIcon from "@mui/icons-material/BarChartRounded";
import GppMaybeRoundedIcon from "@mui/icons-material/GppMaybeRounded";
import FactCheckRoundedIcon from "@mui/icons-material/FactCheckRounded";
import PolicyRoundedIcon from "@mui/icons-material/PolicyRounded";
import ChecklistRoundedIcon from "@mui/icons-material/ChecklistRounded";
import InsightsRoundedIcon from "@mui/icons-material/InsightsRounded";
import PrecisionManufacturingRoundedIcon from "@mui/icons-material/PrecisionManufacturingRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import {
  Alert,
  Button,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  compareRetrievalEvaluations,
  fetchDefaultGroundednessEvaluation,
  fetchDefaultPromptInjectionEvaluation,
  fetchDefaultRetrievalEvaluation,
} from "../api/evaluation";
import { getApiErrorMessage } from "../api/client";
import { PageScaffold } from "../components/layout/PageScaffold";
import { ErrorState, LoadingState } from "../components/shared/FeedbackState";
import { MetricCard } from "../components/shared/MetricCard";
import { SectionCard } from "../components/shared/SectionCard";
import { StatusBadge } from "../components/shared/StatusBadge";
import { formatNumber } from "../lib/format";

function formatPercent(value?: number) {
  if (value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return `${formatNumber(value * 100, 1)}%`;
}

export function EvaluationPage() {
  const defaultEvaluationQuery = useQuery({
    queryKey: ["retrieval-evaluation-default", 3],
    queryFn: () => fetchDefaultRetrievalEvaluation(3),
  });

  const comparisonMutation = useMutation({
    mutationFn: () =>
      compareRetrievalEvaluations({
        variants: [
          {
            label: "top1 / no rerank",
            topK: 1,
            minSimilarityScore: 0.6,
            rerankEnabled: false,
          },
          {
            label: "top3 / rerank",
            topK: 3,
            minSimilarityScore: 0.6,
            rerankEnabled: true,
          },
          {
            label: "top5 / rerank",
            topK: 5,
            minSimilarityScore: 0.5,
            rerankEnabled: true,
          },
        ],
      }),
  });

  const evaluation = defaultEvaluationQuery.data;
  const groundednessMutation = useMutation({
    mutationFn: fetchDefaultGroundednessEvaluation,
  });
  const promptInjectionQuery = useQuery({
    queryKey: ["prompt-injection-evaluation-default"],
    queryFn: fetchDefaultPromptInjectionEvaluation,
  });
  const promptInjectionEvaluation = promptInjectionQuery.data;
  const groundednessEvaluation = groundednessMutation.data;

  return (
    <PageScaffold
      title="Retrieval 評価"
      description="標準評価セットを使って、RAG が期待文書を取得できているかを確認します。"
    >
      {defaultEvaluationQuery.isLoading ? <LoadingState label="Retrieval 評価を実行中..." /> : null}
      {defaultEvaluationQuery.isError ? <ErrorState message={getApiErrorMessage(defaultEvaluationQuery.error)} /> : null}

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="Hit Rate" value={formatPercent(evaluation?.hitRate)} helper="期待文書を1件以上拾えた割合" icon={<InsightsRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard
            label="MRR（期待する文書が上位に出ているかの平均指標）"
            value={formatNumber(evaluation?.meanReciprocalRank, 3)}
            helper="期待文書が上位に出るほど高くなります"
            icon={<BarChartRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="Recall@K" value={formatPercent(evaluation?.averageRecallAtK)} helper={`topK=${evaluation?.topK ?? "-"}`} icon={<ChecklistRoundedIcon color="primary" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <MetricCard label="Precision@K" value={formatPercent(evaluation?.averagePrecisionAtK)} helper="取得結果に期待文書がどれだけ含まれるか" icon={<PrecisionManufacturingRoundedIcon color="primary" />} />
        </Grid>
      </Grid>

      <SectionCard
        title="標準評価ケース"
        description="Go backend に embed された retrieval-cases.json を現在の retrieval 設定で実行した結果です。"
      >
        {evaluation ? (
          <Stack spacing={2}>
            <Alert severity="info">
              {evaluation.totalCases} ケース中 {evaluation.matchedCases} ケースが hit。平均取得件数は {formatNumber(evaluation.averageRetrievedCount, 1)} 件です。
            </Alert>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Case</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Expected</TableCell>
                  <TableCell>Retrieved</TableCell>
                  <TableCell>Rank</TableCell>
                  <TableCell>Recall</TableCell>
                  <TableCell>Precision</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {evaluation.caseResults.map((item) => (
                  <TableRow key={item.label ?? item.query} hover>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <Typography fontWeight={700}>{item.label ?? "unlabeled"}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {item.query}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={item.matched ? "HIT" : "MISS"} />
                    </TableCell>
                    <TableCell>{item.expectedDocumentTitles.join(", ")}</TableCell>
                    <TableCell>{item.retrievedDocumentTitles.join(", ") || "-"}</TableCell>
                    <TableCell>{item.firstRelevantRank ?? "-"}</TableCell>
                    <TableCell>{formatPercent(item.recallAtK)}</TableCell>
                    <TableCell>{formatPercent(item.precisionAtK)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        ) : null}
      </SectionCard>

      <SectionCard
        title="Groundedness 評価"
        description="Go backend に embed された groundedness-cases.json を使って、judge と fallback 方針の妥当性を確認します。"
        action={
          <Button
            variant="contained"
            startIcon={<RefreshRoundedIcon />}
            onClick={() => groundednessMutation.mutate()}
            disabled={groundednessMutation.isPending}
          >
            評価実行
          </Button>
        }
      >
        {groundednessMutation.isPending ? <LoadingState label="Groundedness 評価を実行中..." /> : null}
        {groundednessMutation.isError ? <ErrorState message={getApiErrorMessage(groundednessMutation.error)} /> : null}
        {groundednessEvaluation ? (
          <Stack spacing={2.5}>
            <Grid container spacing={2.5}>
              <Grid size={{ xs: 12, md: 3 }}>
                <MetricCard
                  label="Accuracy"
                  value={formatPercent(groundednessEvaluation.accuracy)}
                  helper={`全 ${groundednessEvaluation.totalCases} ケースの一致率`}
                  icon={<FactCheckRoundedIcon color="primary" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <MetricCard
                  label="平均 groundedness"
                  value={formatPercent(groundednessEvaluation.averageGroundednessScore)}
                  helper="judge が返した score の平均"
                  icon={<InsightsRoundedIcon color="primary" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <MetricCard
                  label="LOW 件数"
                  value={formatNumber(groundednessEvaluation.lowGroundednessCases)}
                  helper="純粋に LOW_GROUNDEDNESS になったケース数"
                  icon={<GppMaybeRoundedIcon color="primary" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <MetricCard
                  label="NO_EVIDENCE"
                  value={formatNumber(groundednessEvaluation.noEvidenceCases)}
                  helper="根拠文書が不足していたケース数"
                  icon={<PolicyRoundedIcon color="primary" />}
                />
              </Grid>
            </Grid>

            <Grid container spacing={2.5}>
              <Grid size={{ xs: 12, md: 4 }}>
                <MetricCard
                  label="PARSE_FAILED"
                  value={formatNumber(groundednessEvaluation.parseFailedCases)}
                  helper="judge 応答の JSON 解析に失敗した件数"
                  icon={<PolicyRoundedIcon color="primary" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <MetricCard
                  label="JUDGE_ERROR"
                  value={formatNumber(groundednessEvaluation.judgeErrorCases)}
                  helper="judge API 呼び出し自体に失敗した件数"
                  icon={<PolicyRoundedIcon color="primary" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <MetricCard
                  label="Fallback 件数"
                  value={formatNumber(groundednessEvaluation.fallbackAppliedCases)}
                  helper="実際に保守的 fallback 応答へ切り替わったケース数"
                  icon={<PolicyRoundedIcon color="primary" />}
                />
              </Grid>
            </Grid>

            <Alert severity="info">
              {groundednessEvaluation.totalCases} ケース中 {groundednessEvaluation.matchedCases} ケースが期待どおり。
              GROUNDED {groundednessEvaluation.groundedCases} 件 / LOW {groundednessEvaluation.lowGroundednessCases} 件 / NO_EVIDENCE {groundednessEvaluation.noEvidenceCases} 件。
            </Alert>

            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Case</TableCell>
                  <TableCell>Expected</TableCell>
                  <TableCell>Actual</TableCell>
                  <TableCell>Fallback</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Score</TableCell>
                  <TableCell>Reason</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {groundednessEvaluation.caseResults.map((item) => (
                  <TableRow key={item.label ?? `${item.situation}-${item.targetGoal}`} hover>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <Typography fontWeight={700}>{item.label ?? "unlabeled"}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {item.situation}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{item.expectedStatus}</TableCell>
                    <TableCell>{item.actualStatus}</TableCell>
                    <TableCell>{item.fallbackApplied ? "APPLIED" : "NONE"}</TableCell>
                    <TableCell>
                      <StatusBadge status={item.matched ? "OK" : "MISMATCH"} />
                    </TableCell>
                    <TableCell>{formatPercent(item.groundednessScore)}</TableCell>
                    <TableCell>{item.reason}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        ) : (
          <Typography color="text.secondary">
            評価実行を押すと、標準 groundedness ケースを使って judge と fallback 方針を評価します。
          </Typography>
        )}
      </SectionCard>

      <SectionCard
        title="Prompt Injection 評価"
        description="Go backend に embed された prompt-injection-cases.json を使って guard の検知精度を確認します。"
      >
        {promptInjectionQuery.isLoading ? <LoadingState label="Prompt Injection 評価を実行中..." /> : null}
        {promptInjectionQuery.isError ? <ErrorState message={getApiErrorMessage(promptInjectionQuery.error)} /> : null}
        {promptInjectionEvaluation ? (
          <Stack spacing={2.5}>
            <Grid container spacing={2.5}>
              <Grid size={{ xs: 12, md: 4 }}>
                <MetricCard
                  label="Detection Rate"
                  value={formatPercent(promptInjectionEvaluation.detectionRate)}
                  helper="block が期待されるケースを正しく止めた割合"
                  icon={<PolicyRoundedIcon color="primary" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <MetricCard
                  label="False Positive Rate"
                  value={formatPercent(promptInjectionEvaluation.falsePositiveRate)}
                  helper="allow が期待されるケースを誤って止めた割合"
                  icon={<GppMaybeRoundedIcon color="primary" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <MetricCard
                  label="Accuracy"
                  value={formatPercent(promptInjectionEvaluation.accuracy)}
                  helper={`全 ${promptInjectionEvaluation.totalCases} ケースの正答率`}
                  icon={<InsightsRoundedIcon color="primary" />}
                />
              </Grid>
            </Grid>

            <Alert severity="info">
              block 期待 {promptInjectionEvaluation.expectedBlockedCases} ケース中 {promptInjectionEvaluation.correctlyBlockedCases} ケースを検知。
              allow 期待 {promptInjectionEvaluation.expectedAllowedCases} ケース中 {promptInjectionEvaluation.correctlyAllowedCases} ケースを通過。
            </Alert>

            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Case</TableCell>
                  <TableCell>Expected</TableCell>
                  <TableCell>Actual</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Guard Message</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {promptInjectionEvaluation.caseResults.map((item) => (
                  <TableRow key={item.label ?? item.input} hover>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <Typography fontWeight={700}>{item.label ?? "unlabeled"}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {item.input}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{item.expectedOutcome}</TableCell>
                    <TableCell>{item.actualOutcome}</TableCell>
                    <TableCell>
                      <StatusBadge status={item.matched ? "OK" : "MISMATCH"} />
                    </TableCell>
                    <TableCell>{item.detectionMessage ?? "-"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        ) : null}
      </SectionCard>

      <SectionCard
        title="条件比較"
        description="topK、similarity threshold、rerank 有無を変えて標準評価セットを横並びで比較します。"
        action={
          <Button
            variant="contained"
            startIcon={<RefreshRoundedIcon />}
            onClick={() => comparisonMutation.mutate()}
            disabled={comparisonMutation.isPending}
          >
            比較実行
          </Button>
        }
      >
        {comparisonMutation.isError ? <ErrorState message={getApiErrorMessage(comparisonMutation.error)} /> : null}
        {comparisonMutation.isPending ? <LoadingState label="比較評価を実行中..." /> : null}
        {comparisonMutation.data ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Variant</TableCell>
                <TableCell>topK</TableCell>
                <TableCell>Threshold</TableCell>
                <TableCell>Rerank</TableCell>
                <TableCell>Hit Rate</TableCell>
                <TableCell>MRR</TableCell>
                <TableCell>Recall@K</TableCell>
                <TableCell>Precision@K</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {comparisonMutation.data.variantResults.map((item) => (
                <TableRow key={item.label} hover>
                  <TableCell>{item.label}</TableCell>
                  <TableCell>{item.topK}</TableCell>
                  <TableCell>{item.minSimilarityScore ?? "-"}</TableCell>
                  <TableCell>{item.rerankEnabled === undefined ? "-" : item.rerankEnabled ? "ON" : "OFF"}</TableCell>
                  <TableCell>{formatPercent(item.hitRate)}</TableCell>
                  <TableCell>{formatNumber(item.meanReciprocalRank, 3)}</TableCell>
                  <TableCell>{formatPercent(item.averageRecallAtK)}</TableCell>
                  <TableCell>{formatPercent(item.averagePrecisionAtK)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <Typography color="text.secondary">比較実行を押すと、複数条件の評価結果を表示します。</Typography>
        )}
      </SectionCard>
    </PageScaffold>
  );
}
