# ADR 0001: AI Harness P0

## Status

Accepted

## Context

このrepositoryはGo backend、React frontend、TypeScript agent runtimeを持つ。AI Agentが編集した直後に軽量検査を返し、タスク完了前に強検証を通す共通の品質ゲートが必要になった。

## Decision

P0ではCodex repo-local hooks、Makefile、Lefthook、CIを同じ検証入口へ接続する。

- `PostToolUse` は `make fast-check` を実行し、診断をAgentへfeedbackする。
- `Stop` は `make verify` を実行し、失敗時はAgentを修正へ戻す。
- `fast-check` は変更直後の高速検査、`verify` は完了前の強検証に分離する。
- Frontendは `oxfmt` と `oxlint` を採用する。`oxlint --type-aware --type-check` は現repoで通ることを確認したため強検証に含める。
- BackendはGo正本なので `gofmt`、`go vet`、`golangci-lint`、`go test` を使う。Kotlin toolingは追加しない。

## Consequences

Hook、Git hook、CIで検証ロジックを重複させず、`make fast-check` / `make verify` を再利用できる。Playwright E2E、高度なarchitecture rule、metrics基盤はP0の非ゴールとし、P1以降で扱う。

ローカル環境の `golangci-lint` がGo targetより古いGoでbuildされている場合は互換性エラーになる。この場合、ローカルでは警告に留め、CIでは `HARNESS_STRICT_GOLANGCI=1` と最新のgolangci-lint setupで強制する。
