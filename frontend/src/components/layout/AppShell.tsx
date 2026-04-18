import AddModeratorIcon from "@mui/icons-material/AddModerator";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import DashboardRoundedIcon from "@mui/icons-material/DashboardRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import FactCheckRoundedIcon from "@mui/icons-material/FactCheckRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import {
  Avatar,
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
  { label: "Dashboard", path: "/dashboard", icon: <DashboardRoundedIcon /> },
  { label: "Advice", path: "/advice", icon: <AutoAwesomeIcon /> },
  { label: "Knowledge", path: "/knowledge", icon: <DescriptionRoundedIcon /> },
  { label: "Reindex Jobs", path: "/reindex-jobs", icon: <RefreshRoundedIcon /> },
  { label: "Audit Logs", path: "/audit-logs", icon: <FactCheckRoundedIcon /> },
];

const sidebarWidth = 252;

export function AppShell() {
  const location = useLocation();
  const currentPage = navigationItems.find((item) => location.pathname.startsWith(item.path))?.label ?? "Advice";

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
            borderRight: "1px solid rgba(66, 128, 86, 0.14)",
            background: "rgba(245, 250, 246, 0.88)",
            backdropFilter: "blur(18px)",
          },
        }}
      >
        <Toolbar sx={{ px: 3, py: 2.5 }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Avatar sx={{ bgcolor: "primary.main", width: 44, height: 44 }}>
              <AddModeratorIcon />
            </Avatar>
            <Box>
              <Typography variant="h6">ONBOARD-Core</Typography>
              <Typography color="text.secondary" variant="body2">
                Management Support AI
              </Typography>
            </Box>
          </Stack>
        </Toolbar>
        <Divider />
        <Box sx={{ px: 2.5, py: 2 }}>
          <Chip color="primary" label={`Environment: ${env.environmentLabel}`} variant="filled" />
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
                  bgcolor: "rgba(65, 168, 98, 0.14)",
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
                  Operational visibility
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
                  bgcolor: "rgba(65, 168, 98, 0.06)",
                }}
              >
                <SearchRoundedIcon fontSize="small" color="action" />
                <InputBase placeholder="Search screens, jobs, or documents" sx={{ width: "100%" }} />
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
