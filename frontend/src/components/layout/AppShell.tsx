import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import AssessmentRoundedIcon from "@mui/icons-material/AssessmentRounded";
import DashboardRoundedIcon from "@mui/icons-material/DashboardRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import FactCheckRoundedIcon from "@mui/icons-material/FactCheckRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import {
  Box,
  Chip,
  Divider,
  Drawer,
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
  { label: "Retrieval 評価", path: "/evaluation", icon: <AssessmentRoundedIcon /> },
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
            <Typography variant="h5">{currentPage}</Typography>
            <AuthToolbar />
          </Paper>
          <Outlet />
        </Stack>
      </Box>
    </Box>
  );
}
