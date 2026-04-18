import PsychologyRoundedIcon from "@mui/icons-material/PsychologyRounded";
import SourceRoundedIcon from "@mui/icons-material/SourceRounded";
import TrendingUpRoundedIcon from "@mui/icons-material/TrendingUpRounded";
import {
  Alert,
  Grid,
} from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { generateAdvice } from "../api/advice";
import { AdviceForm, AdviceFormValue } from "../components/advice/AdviceForm";
import { AdviceResponsePanel } from "../components/advice/AdviceResponsePanel";
import { PageScaffold } from "../components/layout/PageScaffold";
import { EmptyState, ErrorState } from "../components/shared/FeedbackState";
import { MetricCard } from "../components/shared/MetricCard";
import { SectionCard } from "../components/shared/SectionCard";
import { getApiErrorMessage } from "../api/client";
import { AdviceResponse } from "../types/api";

const initialValue: AdviceFormValue = {
  situation: "",
  targetGoal: "",
  tone: "empathetic",
  model: "gpt-4o-mini",
};

export function AdvicePage() {
  const [formValue, setFormValue] = useState<AdviceFormValue>(initialValue);
  const [adviceResult, setAdviceResult] = useState<AdviceResponse | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      generateAdvice({
        memberContext: {
          situation: formValue.situation,
          targetGoal: formValue.targetGoal,
        },
        setting: {
          tone: formValue.tone,
          model: formValue.model,
        },
      }),
    onSuccess: (data) => {
      setAdviceResult(data);
    },
  });

  const hasResult = Boolean(adviceResult);

  return (
    <PageScaffold
      title="助言生成"
      description="マネジメント支援の助言を生成し、根拠文書・使用量・コストをまとめて確認します。"
      rightPanel={<AdviceResponsePanel data={adviceResult} />}
    >
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 4 }}>
          <MetricCard
            label="参照文書数"
            value={hasResult ? String(adviceResult?.retrievedDocuments.length ?? 0) : "0"}
            helper="根拠として取得した文書チャンク数"
            icon={<SourceRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <MetricCard
            label="合計トークン"
            value={hasResult ? String(adviceResult?.usage.totalTokens ?? 0) : "0"}
            helper="入力と出力の合計使用量"
            icon={<TrendingUpRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <MetricCard
            label="応答トーン"
            value={formValue.tone}
            helper={`モデル ${formValue.model}`}
            icon={<PsychologyRoundedIcon color="primary" />}
          />
        </Grid>
      </Grid>

      <SectionCard
        title="助言を生成"
        description="状況、目標、応答トーンを指定して助言を生成します。"
      >
        {mutation.isError ? <ErrorState message={getApiErrorMessage(mutation.error)} /> : null}
        {formValue.model === "gpt-4o" ? (
          <Alert severity="info">MVPデモでは低コストな `gpt-4o-mini` を推奨します。高品質重視の確認時のみ `gpt-4o` を使用してください。</Alert>
        ) : null}
        <AdviceForm value={formValue} onChange={setFormValue} onSubmit={() => mutation.mutate()} loading={mutation.isPending} />
      </SectionCard>

      {!hasResult && !mutation.isPending ? (
        <EmptyState
          title="まだ助言が生成されていません"
          body="助言を生成すると、右ペインに回答・使用量・参照文書が表示されます。"
        />
      ) : null}
    </PageScaffold>
  );
}
