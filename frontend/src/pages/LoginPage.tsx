import LockOpenRoundedIcon from "@mui/icons-material/LockOpenRounded";
import { Box, Paper, Stack, Typography } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { SignInForm } from "../components/shared/SignInForm";

export function LoginPage() {
  const navigate = useNavigate();

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        px: 3,
        py: 6,
        bgcolor: "#ffffff",
      }}
    >
      <Paper
        sx={{
          width: "100%",
          maxWidth: 460,
          px: { xs: 3, sm: 5 },
          py: { xs: 4, sm: 5 },
          borderRadius: 5,
        }}
      >
        <Stack spacing={3}>
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={1.25} alignItems="center">
              <Box
                sx={{
                  width: 44,
                  height: 44,
                  display: "grid",
                  placeItems: "center",
                  borderRadius: 3,
                  bgcolor: "rgba(127, 255, 0, 0.14)",
                  color: "primary.dark",
                }}
              >
                <LockOpenRoundedIcon />
              </Box>
              <Typography variant="h4">ONBOARD-AI</Typography>
            </Stack>
            <Typography variant="body1" color="text.secondary">
              利用を開始するにはサインインしてください。未ログイン時は管理画面を表示しません。
            </Typography>
          </Stack>
          <SignInForm onSuccess={() => navigate("/advice", { replace: true })} />
        </Stack>
      </Paper>
    </Box>
  );
}
