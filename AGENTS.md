# AGENTS.md

## Repository Map

- `backend-go/`: Go / Echo API正本。RAG、advice、evaluation、audit、agent bridgeを持つ。
- `frontend/`: React / Vite / TypeScript UI。
- `agent-runtime/`: TypeScriptのOpenAI Agents SDK sidecar。
- `docs/adr/`: 設計判断の記録。
- `.codex/`: Codex repo-local hooks。
- `scripts/harness/`: AI Harnessの共通検証コマンド。

## Required Checks

- 高速検査: `make fast-check`
- 強検証: `make verify`
- 既存の総合検証: `make test`

`PostToolUse` は `make fast-check`、`Stop` / `pre-push` / CI は `make verify` を使う。

## Rules

- BackendはGoを正本とする。Kotlin backendを復活させない。
- OpenAPI生成物を手で編集しない。契約変更時は `make backend-go-codegen` を使う。
- Hook、Lefthook、CIで検証ロジックを重複実装しない。
- 大きなE2E、Architecture Rule、独自linterはP0では追加しない。
- ADRに残すべき設計判断をREADMEだけに閉じ込めない。

## Done

- 変更後に `make fast-check` が通る。
- タスク完了前に `make verify` が通る。
- 失敗したHook feedbackを読んで修正し、再検証する。
