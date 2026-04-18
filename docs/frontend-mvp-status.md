# ONBOARD-Core Frontend MVP Status

## Implemented on 2026-04-18

The repository now includes a new `frontend/` application for the ONBOARD-Core MVP.

Implemented scope:

- React + Vite + TypeScript frontend scaffold
- MUI-based app shell with:
  - header
  - sidebar
  - environment badge
  - credential entry for Basic auth
- Page routes for:
  - `Dashboard`
  - `Advice`
  - `Knowledge`
  - `Reindex Jobs`
  - `Audit Logs`
- Shared API client using:
  - Axios
  - React Query
  - env-based API base URL
  - stored Basic auth header injection
- `Advice` page MVP:
  - form input
  - advice generation
  - usage display
  - retrieved document display
  - loading / empty / error states
- `Audit Logs` page:
  - list view
  - model filter
  - detail dialog
- `Knowledge` page:
  - list view
  - document registration form
  - full and single-document reindex actions
- `Reindex Jobs` page:
  - list view
  - status filter
  - retry action
  - delete action
  - polling
- Minimum `Dashboard` placeholder
- Backend CORS support for `http://localhost:5173`
- Docker Compose integration for:
  - `frontend`
  - `backend`
  - `postgres`

## Added Files

Primary additions:

- `frontend/`
- `docs/frontend-mvp-implementation-plan.md`
- `docs/frontend-mvp-status.md`
- `src/main/kotlin/com/example/llmragplatform/config/WebCorsConfig.kt`

Updated files:

- `.gitignore`
- `README.md`
- `compose.yaml`
- `src/main/kotlin/com/example/llmragplatform/config/SecurityConfig.kt`

## Verified Commands

Frontend:

```bash
cd frontend
npm install
npm run build
```

Backend:

```bash
./gradlew compileKotlin
```

Compose:

```bash
docker compose config
```

## Current Notes

- The frontend aligns with the current backend reindex endpoints under:
  - `/v1/knowledge-documents/reindex-jobs`
- Basic auth credentials are entered from the UI and stored in browser local storage.
- Default backend credentials are defined in `application.yaml`:
  - admin: `admin / change-me`
  - operator: `operator / change-operator`

## Next Recommended Work

- Update root `README.md` with frontend startup steps
- Reduce bundle size using route-level code splitting
- Add richer dashboard metrics backed by actual API data
