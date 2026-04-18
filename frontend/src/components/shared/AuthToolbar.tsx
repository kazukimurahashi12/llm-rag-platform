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

  const label = useMemo(() => authSession?.username ?? "Anonymous", [authSession?.username]);
  const secondaryLabel = useMemo(() => {
    if (!authSession) {
      return "JWT sign-in required for protected screens";
    }
    return `${authSession.roles.join(", ")} · expires ${new Date(authSession.expiresAt).toLocaleString()}`;
  }, [authSession]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setMessage(null);
    setErrorMessage(null);
    try {
      const token = await issueAuthToken({ username, password });
      saveAuthSession({
        username: token.username,
        accessToken: token.accessToken,
        expiresAt: token.expiresAt,
        roles: token.roles,
      });
      setPassword("");
      setMessage("Bearer token stored for protected endpoints.");
      setOpen(false);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error as AxiosError<ApiErrorResponse>));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = () => {
    clearAuthSession();
    setMessage("Stored bearer token removed.");
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
          <Typography variant="body2" color="text.secondary">
            {secondaryLabel}
          </Typography>
        </div>
      </Stack>
      <Button startIcon={<LockOpenRoundedIcon />} variant="outlined" onClick={() => setOpen(true)}>
        Sign in
      </Button>
      <Button startIcon={<LogoutRoundedIcon />} color="inherit" onClick={handleLogout}>
        Sign out
      </Button>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <form onSubmit={handleSubmit}>
          <DialogTitle>JWT sign in</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}
              <TextField label="Username" value={username} onChange={(event) => setUsername(event.target.value)} required />
              <TextField
                label="Password"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 3 }}>
            <Button onClick={() => setOpen(false)} color="inherit">
              Cancel
            </Button>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              {isSubmitting ? "Signing in..." : "Sign in"}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Stack>
  );
}
