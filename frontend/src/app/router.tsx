import { createBrowserRouter, Navigate, Outlet } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";
import { getStoredAuthSession } from "../lib/auth";
import { AdvicePage } from "../pages/AdvicePage";
import { AuditLogsPage } from "../pages/AuditLogsPage";
import { DashboardPage } from "../pages/DashboardPage";
import { EvaluationPage } from "../pages/EvaluationPage";
import { KnowledgePage } from "../pages/KnowledgePage";
import { LoginPage } from "../pages/LoginPage";
import { ReindexJobsPage } from "../pages/ReindexJobsPage";

function RequireAuth() {
  if (!getStoredAuthSession()) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}

function GuestOnly() {
  if (getStoredAuthSession()) {
    return <Navigate to="/advice" replace />;
  }
  return <Outlet />;
}

export const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    children: [{ path: "/login", element: <LoginPage /> }],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        path: "/",
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/advice" replace /> },
          { path: "dashboard", element: <DashboardPage /> },
          { path: "evaluation", element: <EvaluationPage /> },
          { path: "advice", element: <AdvicePage /> },
          { path: "knowledge", element: <KnowledgePage /> },
          { path: "reindex-jobs", element: <ReindexJobsPage /> },
          { path: "audit-logs", element: <AuditLogsPage /> },
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
