import LockOpenRoundedIcon from "@mui/icons-material/LockOpenRounded";
import LogoutRoundedIcon from "@mui/icons-material/LogoutRounded";
import {
  Alert,
  Avatar,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { AxiosError } from "axios";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { issueAuthToken } from "../../api/auth";
import { getApiErrorMessage } from "../../api/client";
import { AUTH_CHANGED_EVENT, clearAuthSession, getStoredAuthSession, saveAuthSession } from "../../lib/auth";
import { ApiErrorResponse } from "../../types/api";

export function AuthToolbar() {
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState(getStoredAuthSession()?.username ?? "admin");
  const [password, setPassword] = useState("change-me");
  const [message, setMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [authSession, setAuthSession] = useState(getStoredAuthSession());

  useEffect(() => {
    const syncSession = () => {
      const session = getStoredAuthSession();
      setAuthSession(session);
      if (session) {
        setUsername(session.username);
      }
    };
    window.addEventListener(AUTH_CHANGED_EVENT, syncSession);
    return () => window.removeEventListener(AUTH_CHANGED_EVENT, syncSession);
  }, []);

  const label = useMemo(() => authSession?.username ?? "未サインイン", [authSession?.username]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setMessage(null);
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
      setPassword("");
      setMessage("保護API用のトークンを保存しました。");
      setOpen(false);
    } catch (error) {
      clearAuthSession();
      setErrorMessage(getApiErrorMessage(error as AxiosError<ApiErrorResponse>));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = () => {
    clearAuthSession();
    setMessage("保存済みトークンを削除しました。");
    setErrorMessage(null);
    window.location.reload();
  };

  return (
    <Stack direction="row" spacing={1.5} alignItems="center">
      {message ? (
        <Alert severity="success" sx={{ py: 0 }}>
          {message}
        </Alert>
      ) : null}
      <Stack direction="row" spacing={1.25} alignItems="center">
        <Avatar sx={{ bgcolor: "rgba(65, 168, 98, 0.16)", color: "primary.dark" }}>{label[0]?.toUpperCase()}</Avatar>
        <div>
          <Typography fontWeight={700}>{label}</Typography>
        </div>
      </Stack>
      <Button startIcon={<LockOpenRoundedIcon />} variant="outlined" onClick={() => setOpen(true)}>
        サインイン
      </Button>
      <Button startIcon={<LogoutRoundedIcon />} color="inherit" onClick={handleLogout}>
        サインアウト
      </Button>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <form onSubmit={handleSubmit}>
          <DialogTitle>サインイン</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}
              <TextField label="ユーザー名" value={username} onChange={(event) => setUsername(event.target.value)} required />
              <TextField
                label="パスワード"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 3 }}>
            <Button onClick={() => setOpen(false)} color="inherit">
              キャンセル
            </Button>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              {isSubmitting ? "サインイン中..." : "サインイン"}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Stack>
  );
}
