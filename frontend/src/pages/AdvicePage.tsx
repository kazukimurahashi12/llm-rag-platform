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
      title="Advice"
      description="Generate grounded management guidance and inspect source evidence, usage, and cost in one workspace."
      rightPanel={<AdviceResponsePanel data={adviceResult} />}
    >
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 4 }}>
          <MetricCard
            label="Vector evidence"
            value={hasResult ? String(adviceResult?.retrievedDocuments.length ?? 0) : "0"}
            helper="Retrieved supporting document chunks"
            icon={<SourceRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <MetricCard
            label="Total tokens"
            value={hasResult ? String(adviceResult?.usage.totalTokens ?? 0) : "0"}
            helper="Prompt + completion usage"
            icon={<TrendingUpRoundedIcon color="primary" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <MetricCard
            label="Assistant mode"
            value={formValue.tone}
            helper={`Model ${formValue.model}`}
            icon={<PsychologyRoundedIcon color="primary" />}
          />
        </Grid>
      </Grid>

      <SectionCard
        title="Generate advice"
        description="Describe the situation, specify the target outcome, and choose the response style."
      >
        {mutation.isError ? <ErrorState message={getApiErrorMessage(mutation.error)} /> : null}
        {formValue.model === "gpt-4o" ? (
          <Alert severity="info">Use `gpt-4o-mini` for lower-cost MVP demos. Keep `gpt-4o` for higher-stakes output review.</Alert>
        ) : null}
        <AdviceForm value={formValue} onChange={setFormValue} onSubmit={() => mutation.mutate()} loading={mutation.isPending} />
      </SectionCard>

      {!hasResult && !mutation.isPending ? (
        <EmptyState
          title="No advice generated yet"
          body="Run the advice flow to populate the right panel with the response, usage summary, and retrieved documents."
        />
      ) : null}
    </PageScaffold>
  );
}
