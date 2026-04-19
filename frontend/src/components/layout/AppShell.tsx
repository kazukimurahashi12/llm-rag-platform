import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import DashboardRoundedIcon from "@mui/icons-material/DashboardRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import FactCheckRoundedIcon from "@mui/icons-material/FactCheckRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import {
  Box,
  Chip,
  Divider,
  Drawer,
  InputBase,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  Toolbar,
  Typography,
} from "@mui/material";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { env } from "../../env";
import { AuthToolbar } from "../shared/AuthToolbar";

const navigationItems = [
  { label: "ダッシュボード", path: "/dashboard", icon: <DashboardRoundedIcon /> },
  { label: "助言生成", path: "/advice", icon: <AutoAwesomeIcon /> },
  { label: "ナレッジ", path: "/knowledge", icon: <DescriptionRoundedIcon /> },
  { label: "再インデックス", path: "/reindex-jobs", icon: <RefreshRoundedIcon /> },
  { label: "監査ログ", path: "/audit-logs", icon: <FactCheckRoundedIcon /> },
];

const sidebarWidth = 252;

export function AppShell() {
  const location = useLocation();
  const currentPage = navigationItems.find((item) => location.pathname.startsWith(item.path))?.label ?? "助言生成";

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <Drawer
        variant="permanent"
        sx={{
          width: sidebarWidth,
          flexShrink: 0,
          "& .MuiDrawer-paper": {
            width: sidebarWidth,
            boxSizing: "border-box",
            borderRight: "1px solid rgba(127, 255, 0, 0.16)",
            background: "#ffffff",
          },
        }}
      >
        <Toolbar sx={{ px: 3, py: 2.5 }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Box>
              <Typography variant="h6">ONBOARD-AI</Typography>
            </Box>
          </Stack>
        </Toolbar>
        <Divider />
        <Box sx={{ px: 2.5, py: 2 }}>
          <Chip color="primary" label={`環境: ${env.environmentLabel}`} variant="filled" />
        </Box>
        <List sx={{ px: 1.5 }}>
          {navigationItems.map((item) => (
            <ListItemButton
              key={item.path}
              component={NavLink}
              to={item.path}
              sx={{
                mb: 0.75,
                borderRadius: 3,
                color: "text.secondary",
                "&.active": {
                  bgcolor: "rgba(127, 255, 0, 0.14)",
                  color: "primary.dark",
                },
              }}
            >
              <ListItemIcon sx={{ color: "inherit", minWidth: 40 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, px: 4, py: 3 }}>
        <Stack spacing={3}>
          <Paper
            sx={{
              px: 3,
              py: 2,
              borderRadius: 5,
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
            }}
          >
            <Stack direction="row" spacing={2} alignItems="center">
              <Box>
                <Typography variant="overline" color="primary.main">
                  運用可視化
                </Typography>
                <Typography variant="h5">{currentPage}</Typography>
              </Box>
              <Paper
                component="label"
                sx={{
                  px: 1.5,
                  py: 0.75,
                  display: "flex",
                  alignItems: "center",
                  gap: 1,
                  minWidth: 260,
                  borderRadius: 999,
                  bgcolor: "#ffffff",
                }}
              >
                <SearchRoundedIcon fontSize="small" color="action" />
                <InputBase placeholder="画面・ジョブ・文書を検索" sx={{ width: "100%" }} />
              </Paper>
            </Stack>
            <AuthToolbar />
          </Paper>
          <Outlet />
        </Stack>
      </Box>
    </Box>
  );
}

function LogoMark() {
  return (
    <Box
      sx={{
        width: 44,
        height: 44,
        display: "grid",
        placeItems: "center",
        borderRadius: 3,
        bgcolor: "#ffffff",
        border: "1px solid rgba(127, 255, 0, 0.2)",
        boxShadow: "0 8px 18px rgba(0, 0, 0, 0.04)",
      }}
    >
      <svg width="30" height="30" viewBox="0 0 30 30" fill="none" aria-hidden="true">
        <rect x="3" y="3" width="24" height="24" rx="8" fill="#FBFFF5" />
        <path
          d="M8 20V10h3.2l3.8 5.2 3.8-5.2H22V20h-2.9v-5.3l-3.1 4.3h-2l-3.1-4.3V20H8Z"
          fill="#7FFF00"
        />
        <path
          d="M19.8 7.8c0-1.55 1.25-2.8 2.8-2.8s2.8 1.25 2.8 2.8-1.25 2.8-2.8 2.8-2.8-1.25-2.8-2.8Z"
          fill="#1A1F16"
        />
        <circle cx="22.6" cy="7.8" r="1.15" fill="#7FFF00" />
      </svg>
    </Box>
  );
}
