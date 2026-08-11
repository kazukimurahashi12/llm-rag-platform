# OpenAI Agents SDK Sidecar MVP 実装ログ

このドキュメントは、Go-only 化後に追加した OpenAI Agents SDK sidecar 前提の Agent MVP 実装内容を整理したものです。

## 目的

既存の Go backend にある RAG / 評価 / dashboard / advice API を、Agent が使う tool として扱えるようにする。

Go backend は引き続き API 正本とし、Agent orchestration は OpenAI Agents SDK を使う `agent-runtime` sidecar に分離する。

## 構成

```text
frontend
  -> backend-go
      POST /v1/agent/tasks
        -> agent-runtime
            OpenAI Agents SDK
            -> backend-go API tools
```

サービス:

- `backend-go`
  - Go / Echo backend
  - JWT 認証と role check を担当
  - `POST /v1/agent/tasks` を公開
  - agent-runtime へ task を bridge する
- `agent-runtime`
  - Node.js / TypeScript sidecar
  - OpenAI Agents SDK で Agent / tools / runner を管理
  - backend-go API を HTTP tool として呼び出す

## 追加した API

### `POST /v1/agent/tasks`

Go backend 側の公開 endpoint。

認証:

- JWT 必須
- `ADMIN` または `OPERATOR` role 必須

request:

```json
{
  "input": "dashboard summary を確認し、現在の状態を短く日本語で要約して",
  "maxTurns": 3
}
```

response:

```json
{
  "taskId": "agent-task-...",
  "status": "COMPLETED",
  "finalAnswer": "...",
  "toolCalls": [
    {
      "toolName": "dashboard.summary",
      "status": "COMPLETED",
      "latencyMs": 17,
      "inputSummary": "GET /v1/dashboard/summary",
      "outputSummary": "..."
    }
  ],
  "traceId": "resp_..."
}
```

### `POST /agent/tasks`

agent-runtime 側の内部 endpoint。

Go backend からのみ呼び出す前提。
Go backend で検証済みの Bearer token を受け取り、backend-go tools の HTTP 呼び出しに引き継ぐ。

### `GET /health`

agent-runtime の health check。

```json
{
  "status": "UP"
}
```

## 登録した tools

MVP では read-only tools と advice 生成だけを登録している。

| Tool | backend-go API | 用途 |
| --- | --- | --- |
| `knowledge.search` | `GET /v1/knowledge-documents` | 利用可能なナレッジ文書の確認 |
| `dashboard.summary` | `GET /v1/dashboard/summary` | 利用状況・品質・reindex 状態の確認 |
| `retrieval.evaluate` | `GET /v1/retrieval-evaluations/default` | retrieval 品質の確認 |
| `groundedness.evaluate` | `GET /v1/groundedness-evaluations/default` | groundedness / fallback 方針の確認 |
| `promptInjection.evaluate` | `GET /v1/prompt-injection-evaluations/default` | prompt injection guard の精度確認 |
| `advice.generate` | `POST /v1/management/advice` | 既存 advice API による助言生成 |

MVP で未登録の write tools:

- `knowledge.create`
- `knowledge.update`
- `reindex.submit`

これらは confirmation / approval / idempotency / stronger audit が必要なため、次フェーズ扱い。

## 追加・変更ファイル

主な追加:

- `agent-runtime/`
  - `package.json`
  - `tsconfig.json`
  - `Dockerfile`
  - `src/index.ts`
  - `src/agent.ts`
  - `src/backendClient.ts`
  - `src/config.ts`
  - `README.md`
- `backend-go/internal/agent/client.go`
- `backend-go/internal/http/agent.go`

主な変更:

- `backend-go/openapi/management-advice-api.yaml`
- `backend-go/internal/api/types.gen.go`
- `backend-go/internal/config/config.go`
- `backend-go/internal/http/server.go`
- `compose.yaml`
- `Makefile`
- `.env.example`
- `scripts/go-only-smoke.sh`
- `README.md`
- `backend-go/README.md`

## 設定値

Go backend:

- `AGENT_RUNTIME_BASE_URL`
- `AGENT_RUNTIME_TIMEOUT_SECONDS`
- `AGENT_MAX_TURNS`

agent-runtime:

- `AGENT_RUNTIME_PORT`
- `BACKEND_GO_BASE_URL`
- `OPENAI_API_KEY`
- `AGENT_DEFAULT_MODEL`
- `AGENT_REQUEST_TIMEOUT_MS`
- `AGENT_MAX_TURNS`

compose 既定:

- `backend-go`: `8081`
- `agent-runtime`: `8091`
- `BACKEND_GO_BASE_URL`: `http://backend-go:8081`
- `AGENT_RUNTIME_BASE_URL`: `http://agent-runtime:8091`

## 安全制御

現時点で入れている制御:

- Go backend 側で JWT 認証
- `ADMIN / OPERATOR` role check
- sidecar には Go backend で検証済み Bearer token を渡す
- tool は allowlist 登録方式
- write tools は未登録
- `maxTurns` を `1..10` に制限
- sidecar HTTP call に timeout を設定
- Agent instructions で write operation / credential / tool policy override を拒否

今後強化する制御:

- Agent task / tool call の DB 永続化
- tool input/output の masking
- tool 実行前 guardrails
- human approval
- idempotency key
- write tool policy

## 検証結果

実施済み:

```text
make test                         OK
make smoke-go                     OK
docker compose config --quiet     OK
npm audit --json frontend         vulnerabilities: 0
npm audit --json agent-runtime    vulnerabilities: 0
git diff --check                  OK
docker compose up -d --build      OK
```

実 Agent task も確認済み。

実行例:

```text
POST /v1/agent/tasks
input: dashboard summary を確認し、現在の状態を短く日本語で要約して
maxTurns: 3
```

結果:

```text
status: COMPLETED
toolCalls: dashboard.summary
traceId: resp_...
```

## 現時点の到達点

Phase 0 と Phase 1 の最小範囲は完了。

- Phase 0: sidecar 導入準備
  - `agent-runtime` scaffold
  - Docker / compose
  - health endpoint
  - Go backend から sidecar への bridge
- Phase 1: Agent MVP
  - `POST /v1/agent/tasks`
  - OpenAI Agents SDK Agent
  - backend-go API tools
  - toolCalls response
  - traceId response
  - smoke / docs

## 次フェーズ

### Phase 2: Agent 実行ログ

- `agent_tasks` table
- `agent_tool_calls` table
- task status
- finalAnswer 保存
- tool call input/output summary 保存
- latency / traceId 保存

### Phase 3: Frontend Agent UI

- Agent task 入力画面
- toolCalls timeline
- traceId 表示
- failure reason 表示
- Agent metrics

### Phase 4: Guardrails / Safety 強化

- tool 実行前 guardrails
- prompt injection guard の Agent tool input 適用
- sensitive output masking
- role-based tool policy 強化
- max tool calls

### Phase 5: 書き込み tool 対応

- `knowledge.create`
- `knowledge.update`
- `reindex.submit`
- confirmation flow
- human approval
- idempotency key
- stronger audit
