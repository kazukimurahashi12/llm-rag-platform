# backend-go

Go / Echo 版 backend の実行正本です。

現時点の目的:
- マネジメントアドバイス API と周辺運用 API を Go 版で提供する
- Compose / frontend / Prometheus の接続先として Go 版を使う

OpenAPI:
- `openapi/management-advice-api.yaml` が API 契約の正本です
- 契約を変更する場合は Go 側正本を更新し、`make backend-go-codegen` で generated model を再作成します
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
- `RAG_TOP_K`
- `RAG_VECTOR_SEARCH_ENABLED`
- `RAG_EMBEDDING_MODEL`
- `RAG_EMBEDDING_DIMENSIONS`
- `RAG_MIN_SIMILARITY_SCORE`
- `RAG_RERANK_ENABLED`
- `RAG_RERANK_CANDIDATE_MULTIPLIER`
- `RAG_GROUNDEDNESS_THRESHOLD`
- `RAG_GROUNDEDNESS_FALLBACK_ENABLED`
- `RAG_GROUNDEDNESS_FALLBACK_SCORE_THRESHOLD`
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
reindex job は `knowledge_reindex_jobs` テーブルへ永続化しており、保持期限を過ぎた `COMPLETED / FAILED` job の cleanup、同一 scope の active job 重複受付防止、起動時の `QUEUED / RUNNING` job 回復を入れています。active job が既にある場合は新規 job を増やさず、既存 job を返します。
`/v1/retrieval-evaluations/*` は ADMIN のみ実行でき、Go 側 retrieval 実装で Hit Rate / MRR / Recall@K / Precision@K を標準ケースまたは任意ケースで集計します。comparison API の `topK / minSimilarityScore / rerankEnabled` は Go 側でも有効で、rerank 有効時は候補母集団を広げて lexical score で再順位付けします。
`/v1/prompt-injection-evaluations/*` は ADMIN のみ実行でき、Go 側 guard の block / allow 精度を標準ケースまたは任意ケースで集計します。
`/v1/groundedness-evaluations/*` は ADMIN のみ実行でき、groundedness judge と fallback 方針を標準ケースまたは任意ケースで集計します。
advice 実行時の audit log は Go 側でも `audit_logs` テーブルへ保存します。PII masking は移植済みで、保存前にメールアドレス・電話番号・社員番号をマスクします。admin はマスク済み全文、operator はマスク済み短縮表示を返します。
dashboard summary には retrieval metrics も含まれ、`vectorAcceptedRetrievals / vectorThresholdFallbacks / vectorThresholdFilteredChunks` を Go 側でも返します。

`POST /v1/management/advice` は現時点では JWT 必須で、OpenAI 呼び出し、RAG retrieval、groundedness judge、retrievedDocuments 返却まで対応しています。
