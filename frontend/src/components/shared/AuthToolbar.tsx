import LogoutRoundedIcon from "@mui/icons-material/LogoutRounded";
import { Avatar, Button, Stack, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { AUTH_CHANGED_EVENT, clearAuthSession, getStoredAuthSession } from "../../lib/auth";

export function AuthToolbar() {
  const [authSession, setAuthSession] = useState(getStoredAuthSession());

  useEffect(() => {
    const syncSession = () => {
      const session = getStoredAuthSession();
      setAuthSession(session);
    };
    window.addEventListener(AUTH_CHANGED_EVENT, syncSession);
    return () => window.removeEventListener(AUTH_CHANGED_EVENT, syncSession);
  }, []);

  const label = useMemo(() => authSession?.username ?? "未サインイン", [authSession?.username]);

  const handleLogout = () => {
    clearAuthSession();
    window.location.assign("/login");
  };

  return (
    <Stack direction="row" spacing={1.5} alignItems="center">
      <Stack direction="row" spacing={1.25} alignItems="center">
        <Avatar sx={{ bgcolor: "rgba(65, 168, 98, 0.16)", color: "primary.dark" }}>{label[0]?.toUpperCase()}</Avatar>
        <div>
          <Typography fontWeight={700}>{label}</Typography>
        </div>
      </Stack>
      <Button startIcon={<LogoutRoundedIcon />} color="inherit" onClick={handleLogout}>
        サインアウト
      </Button>
    </Stack>
  );
}
