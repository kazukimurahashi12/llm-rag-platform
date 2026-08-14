# AI Harness P0 Implementation Log

## Summary

このrepositoryに Minimum Viable AI Harness を導入した。

目的は、AI Agent がコードを変更した直後にrepository側で高速検査を実行し、失敗時は診断をAgentへ返して自己修正させ、タスク完了時には強い検証を通過しない限り完了できない状態を作ること。

P0では Playwright E2E、高度なarchitecture rule、独自metrics基盤は対象外とした。

## Implemented Scope

- `AGENTS.md`
- Codex repo-local hooks
  - `PostToolUse`
  - `Stop`
- 共通検証入口
  - `make fast-check`
  - `make verify`
- Frontend fast / strong verification
  - `oxfmt`
  - `oxlint`
  - `oxlint --type-aware --type-check`
  - `tsc -b && vite build`
- Backend Go verification
  - `gofmt`
  - `go vet`
  - `golangci-lint`
  - `go test`
- Lefthook
  - `pre-commit`: `make fast-check`
  - `pre-push`: `make verify`
- GitHub Actions CI
  - `make verify`
  - `HARNESS_STRICT_GOLANGCI=1`
- ADR
  - `docs/adr/0001-ai-harness-p0.md`

## Main Files

- `AGENTS.md`
- `.codex/hooks.json`
- `.codex/hooks/post_tool_use_fast_check.py`
- `.codex/hooks/stop_verify.py`
- `scripts/harness/common.sh`
- `scripts/harness/fast-check.sh`
- `scripts/harness/verify.sh`
- `lefthook.yml`
- `.github/workflows/ci.yml`
- `.oxlintrc.json`
- `frontend/.oxfmtrc.json`
- `docs/adr/0001-ai-harness-p0.md`

## Hook Flow

### PostToolUse

Trigger:

```text
apply_patch | Edit | Write
```

Flow:

```text
Agent edits files
  -> PostToolUse
  -> make fast-check
  -> feedback returned to Agent
  -> Agent fixes diagnostics
  -> PostToolUse runs again
```

`PostToolUse` executes:

```bash
/usr/bin/python3 "$(git rev-parse --show-toplevel)/.codex/hooks/post_tool_use_fast_check.py"
```

On failure, the hook returns `decision: block` with diagnostics.

### Stop

Flow:

```text
Agent tries to finish
  -> Stop
  -> make verify
  -> PASS: final response allowed
  -> FAIL: Agent continues fixing
```

`Stop` executes:

```bash
/usr/bin/python3 "$(git rev-parse --show-toplevel)/.codex/hooks/stop_verify.py"
```

On failure, the hook returns `decision: block`.

## Verification Commands

### Fast Check

```bash
make fast-check
```

Responsibilities:

- Changed frontend JS/TS files:
  - `oxfmt`
  - `oxlint`
- Changed Go files:
  - `gofmt -w`
  - changed-package `go vet`

This command intentionally avoids full build and full test execution.

### Verify

```bash
make verify
```

Responsibilities:

- Frontend:
  - `npm run format:check`
  - `npm run lint`
  - `npm run lint:typecheck`
  - `npm run build`
- Agent runtime:
  - `npm run build`
- Backend:
  - `gofmt` check
  - `go vet ./...`
  - `golangci-lint run ./...`
  - `go test ./...`

CI runs `make verify` with:

```bash
HARNESS_STRICT_GOLANGCI=1
```

## Measured Results

Local verification results after implementation:

```text
make fast-check
PASS
~0.78s to 1.23s

make verify
PASS
~8.68s to 11.51s

HARNESS_STRICT_GOLANGCI=1 make verify
PASS after CI lint fixes
~14.88s

npx lefthook run pre-commit --all-files
PASS
~1.00s to 2.66s

npx lefthook run pre-push --all-files
PASS
~10.39s to 13.27s
```

GitHub Actions:

```text
Run 1: failure
Reason: golangci-lint caught existing Go lint issues

Run 2: success
Commit: 73d51b0 Fix CI lint gate issues
URL: https://github.com/kazukimurahashi12/llm-rag-platform/actions/runs/31785680155
```

## Feedback Loop Proof

The feedback loop was tested with intentional failures.

### PostToolUse Failure

Temporary frontend syntax error:

```text
const harnessProbe =
```

Result:

```text
PostToolUse returned decision: block
Diagnostic: Unexpected token
```

After removing the temporary error, PostToolUse returned:

```text
AI Harness fast-check passed
```

### Stop Failure

Temporary frontend type error:

```text
apiBaseUrl: missingHarnessSymbol
```

Result:

```text
Stop returned decision: block
Diagnostic: TS2304 Cannot find name 'missingHarnessSymbol'
```

After restoring the original expression, Stop returned:

```json
{"continue": true, "systemMessage": "AI Harness verify passed."}
```

## CI Lint Fixes

The first CI run failed because strict `golangci-lint` caught existing Go issues:

- unchecked `response.Body.Close()`
- unchecked `rows.Close()`
- unnecessary `fmt.Sprintf`
- empty staticcheck branch

These were fixed in:

```text
73d51b0 Fix CI lint gate issues
```

The harness now also sets a repo-local `GOLANGCI_LINT_CACHE` to avoid sandbox cache write issues.

## Current State

Completed:

- Codex hook scripts exist and return Agent-readable feedback.
- Hook scripts were manually invoked with representative payloads.
- Lefthook is installed locally.
- `pre-commit` and `pre-push` both pass.
- GitHub Actions CI passes.
- Working tree was clean after push.

Manual confirmation still required in each Codex UI/session:

- `PostToolUse` hook checkbox is enabled.
- `Stop` hook checkbox is enabled.
- Trust state is `Trusted`.

## P1 Candidates

Recommended next work:

1. Playwright smoke E2E and CI integration.
2. Frontend unit test framework.
3. Agent task evaluation cases.
4. AI Harness JSON / Markdown report output.
5. `golangci-lint` version pin strategy.
6. Recurring mistakes promotion into lint / test / architecture rules.
7. Harness metrics baseline.
