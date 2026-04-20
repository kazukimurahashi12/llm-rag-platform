import { Alert, Button, Stack, TextField } from "@mui/material";
import { AxiosError } from "axios";
import { FormEvent, useState } from "react";
import { issueAuthToken } from "../../api/auth";
import { getApiErrorMessage } from "../../api/client";
import { clearAuthSession, saveAuthSession } from "../../lib/auth";
import { ApiErrorResponse } from "../../types/api";

interface SignInFormProps {
  onSuccess?: () => void;
}

export function SignInForm({ onSuccess }: SignInFormProps) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("change-me");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      clearAuthSession();
      const token = await issueAuthToken({ username, password });
      saveAuthSession({
        username: token.username,
        accessToken: token.accessToken,
        expiresAt: token.expiresAt,
        roles: token.roles,
      });
      onSuccess?.();
    } catch (error) {
      clearAuthSession();
      setErrorMessage(getApiErrorMessage(error as AxiosError<ApiErrorResponse>));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <Stack spacing={2.5}>
        {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}
        <TextField label="ユーザー名" value={username} onChange={(event) => setUsername(event.target.value)} required />
        <TextField
          label="パスワード"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
        <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
          {isSubmitting ? "サインイン中..." : "サインイン"}
        </Button>
      </Stack>
    </form>
  );
}
