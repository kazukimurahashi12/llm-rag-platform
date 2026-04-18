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
import { FormEvent, useMemo, useState } from "react";
import { clearCredentials, getStoredCredentials, saveCredentials } from "../../lib/auth";

export function AuthToolbar() {
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState(getStoredCredentials()?.username ?? "admin");
  const [password, setPassword] = useState(getStoredCredentials()?.password ?? "change-me");
  const [message, setMessage] = useState<string | null>(null);
  const credentials = getStoredCredentials();

  const label = useMemo(() => credentials?.username ?? "Anonymous", [credentials?.username]);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    saveCredentials({ username, password });
    setMessage("Credentials stored for protected endpoints.");
    setOpen(false);
  };

  const handleLogout = () => {
    clearCredentials();
    setMessage("Stored credentials removed.");
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
            Basic auth for audit and admin screens
          </Typography>
        </div>
      </Stack>
      <Button startIcon={<LockOpenRoundedIcon />} variant="outlined" onClick={() => setOpen(true)}>
        Credentials
      </Button>
      <Button startIcon={<LogoutRoundedIcon />} color="inherit" onClick={handleLogout}>
        Clear
      </Button>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <form onSubmit={handleSubmit}>
          <DialogTitle>Basic authentication</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
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
            <Button type="submit" variant="contained">
              Save
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Stack>
  );
}
