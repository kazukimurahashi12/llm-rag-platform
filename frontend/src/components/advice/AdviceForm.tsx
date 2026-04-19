import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";
import { Button, MenuItem, Stack, TextField } from "@mui/material";
import { FormEvent } from "react";

export interface AdviceFormValue {
  situation: string;
  targetGoal: string;
  tone: string;
  model: string;
}

interface AdviceFormProps {
  value: AdviceFormValue;
  onChange: (value: AdviceFormValue) => void;
  onSubmit: () => void;
  loading: boolean;
}

export const tones = [
  { value: "empathetic", label: "共感的" },
  { value: "direct", label: "率直" },
  { value: "supportive", label: "支援的" },
] as const;

const models = [
  { value: "gpt-4o-mini", label: "gpt-4o-mini" },
  { value: "gpt-4o", label: "gpt-4o" },
];

export function AdviceForm({ value, onChange, onSubmit, loading }: AdviceFormProps) {
  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <form onSubmit={handleSubmit}>
      <Stack spacing={2.5}>
        <TextField
          label="状況"
          value={value.situation}
          onChange={(event) => onChange({ ...value, situation: event.target.value })}
          multiline
          minRows={6}
          placeholder="支援が必要なマネジメント上の状況を入力してください。"
          required
        />
        <TextField
          label="目標"
          value={value.targetGoal}
          onChange={(event) => onChange({ ...value, targetGoal: event.target.value })}
          multiline
          minRows={4}
          placeholder="達成したい行動変容やチームの状態を入力してください。"
          required
        />
        <Stack direction="row" spacing={2}>
          <TextField
            select
            fullWidth
            label="トーン"
            value={value.tone}
            onChange={(event) => onChange({ ...value, tone: event.target.value })}
          >
            {tones.map((tone) => (
              <MenuItem key={tone.value} value={tone.value}>
                {tone.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            fullWidth
            label="モデル"
            value={value.model}
            onChange={(event) => onChange({ ...value, model: event.target.value })}
          >
            {models.map((model) => (
              <MenuItem key={model.value} value={model.value}>
                {model.label}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        <Button size="large" variant="contained" type="submit" startIcon={<AutoAwesomeRoundedIcon />} disabled={loading}>
          {loading ? "生成中..." : "助言を生成"}
        </Button>
      </Stack>
    </form>
  );
}
