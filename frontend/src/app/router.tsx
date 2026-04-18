import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";
import { AdvicePage } from "../pages/AdvicePage";
import { AuditLogsPage } from "../pages/AuditLogsPage";
import { DashboardPage } from "../pages/DashboardPage";
import { KnowledgePage } from "../pages/KnowledgePage";
import { ReindexJobsPage } from "../pages/ReindexJobsPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/advice" replace /> },
      { path: "dashboard", element: <DashboardPage /> },
      { path: "advice", element: <AdvicePage /> },
      { path: "knowledge", element: <KnowledgePage /> },
      { path: "reindex-jobs", element: <ReindexJobsPage /> },
      { path: "audit-logs", element: <AuditLogsPage /> },
    ],
  },
]);
