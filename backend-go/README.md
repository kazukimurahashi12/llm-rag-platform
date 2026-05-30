# backend-go

Go / Echo 版 backend の並行実装用ディレクトリです。

現時点の目的:
- Kotlin / Spring Boot backend を壊さずに、Go 版を段階的に作る
- まずは起動基盤、設定、health endpoint から作る
- 後続で advice、RAG、OpenAI 連携を移植する

OpenAPI:
- `openapi/management-advice-api.yaml` は Kotlin 版 backend の OpenAPI をコピーしたスナップショットです
- 正本は `backend/src/main/resources/openapi/management-advice-api.yaml` です
- Kotlin 側の契約を変更した場合は、Go 側スナップショットも同期が必要です
- generated model を更新するときは `go generate ./internal/api` または root で `make backend-go-codegen` を使います
- advice prompt は `prompts/management-coach-v1.0.txt` に外出ししています

起動:

```bash
cd backend-go
go run ./cmd/server
```

既定ポート:
- `8081`

環境変数:
- `APP_ENV`
- `PORT`
- `APP_JWT_SECRET`
- `APP_JWT_EXPIRATION_SECONDS`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_SSLMODE`
- `OPENAI_API_KEY`
- `OPENAI_DEFAULT_MODEL`
- `OPENAI_TIMEOUT_SECONDS`
- `OPENAI_CONNECT_TIMEOUT_SECONDS`
- `OPENAI_READ_TIMEOUT_SECONDS`
- `OPENAI_RETRY_MAX_ATTEMPTS`
- `OPENAI_RETRY_INITIAL_BACKOFF_MILLIS`
- `OPENAI_RETRY_MAX_BACKOFF_MILLIS`
- `OPENAI_CIRCUIT_BREAKER_FAILURE_THRESHOLD_PERCENT`
- `OPENAI_CIRCUIT_BREAKER_MINIMUM_CALLS`
- `OPENAI_CIRCUIT_BREAKER_WINDOW_SIZE`
- `OPENAI_CIRCUIT_BREAKER_OPEN_SECONDS`
- `OPENAI_CIRCUIT_BREAKER_HALF_OPEN_MAX_CALLS`
- `AUDIT_ADMIN_USERNAME`
- `AUDIT_ADMIN_PASSWORD`
- `AUDIT_OPERATOR_USERNAME`
- `AUDIT_OPERATOR_PASSWORD`
- `ADVICE_PROMPT_TEMPLATE_PATH`

現在の endpoint:
- `GET /health`
- `GET /version`
- `POST /v1/auth/token`
- `GET /v1/auth/me`
- `POST /v1/management/advice`
- `GET /v1/knowledge-documents`
- `POST /v1/knowledge-documents`
- `PUT /v1/knowledge-documents/{knowledgeDocumentId}`
- `POST /v1/knowledge-documents/reindex`
- `POST /v1/knowledge-documents/{knowledgeDocumentId}/reindex`
- `GET /v1/knowledge-documents/reindex-jobs`
- `GET /v1/knowledge-documents/reindex-jobs/{jobId}`
- `POST /v1/knowledge-documents/reindex-jobs/{jobId}/retry`
- `DELETE /v1/knowledge-documents/reindex-jobs/{jobId}`
- `GET /v1/prompt-injection-evaluations/default`
- `POST /v1/prompt-injection-evaluations`
- `GET /v1/groundedness-evaluations/default`
- `POST /v1/groundedness-evaluations`
- `GET /v1/retrieval-evaluations/default`
- `POST /v1/retrieval-evaluations`
- `POST /v1/retrieval-evaluations/comparisons`
- `GET /v1/audit-logs`
- `GET /v1/audit-logs/{auditLogId}`
- `GET /v1/dashboard/summary`

`GET /health` は PostgreSQL 疎通も確認し、`db` フィールドに `UP / DOWN` を返します。
`POST /v1/management/advice` には最小の prompt injection guard を入れており、日本語/英語の典型パターンと表記揺れ正規化で危険入力を block します。
OpenAI 呼び出し層には timeout / retry / circuit breaker を入れており、retry は `TIMEOUT / TRANSPORT / 429 / 502 / 503 / 504` の一時障害だけを対象にします。circuit breaker が OPEN の間は fail-fast で `503` を返します。
`/v1/knowledge-documents` は Go 側の最小 CRUD として、一覧・登録・更新まで対応しています。登録/更新時は固定長 chunk を再生成し、vector search 有効時は embedding も同期再作成します。
reindex job は現時点では Go プロセス内メモリで管理しています。API 契約は揃えていますが、Kotlin 側のような永続化 job 管理・cleanup はまだ未移植です。
`/v1/retrieval-evaluations/*` は ADMIN のみ実行でき、Go 側 retrieval 実装で Hit Rate / MRR / Recall@K / Precision@K を標準ケースまたは任意ケースで集計します。comparison API は `topK / minSimilarityScore / rerankEnabled` を受けますが、現時点の Go 側では `rerankEnabled` は no-op です。
`/v1/prompt-injection-evaluations/*` は ADMIN のみ実行でき、Go 側 guard の block / allow 精度を標準ケースまたは任意ケースで集計します。
`/v1/groundedness-evaluations/*` は ADMIN のみ実行でき、groundedness judge と fallback 方針を標準ケースまたは任意ケースで集計します。
advice 実行時の audit log は Go 側でも `audit_logs` テーブルへ保存します。現時点では Kotlin 側の PII masking は未移植なので、admin/operator で同一内容を返します。

`POST /v1/management/advice` は現時点では JWT 必須で、OpenAI 呼び出し、RAG retrieval、groundedness judge、retrievedDocuments 返却まで対応しています。
