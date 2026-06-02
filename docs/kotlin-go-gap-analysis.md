# Kotlin / Go 差分一覧

このドキュメントは、Kotlin / Spring Boot 版 `backend/` と Go / Echo 版 `backend-go/` の実装差分を整理したものです。
完全リプレイス前に残っている論点を、API 契約差分ではなく実装差分として管理します。

## 現状

- OpenAPI 正本:
  - `backend/src/main/resources/openapi/management-advice-api.yaml`
- Go 側 snapshot:
  - `backend-go/openapi/management-advice-api.yaml`
- 2026-06-01 時点では、OpenAPI ファイル差分は解消済みです。

## 揃っているもの

- `POST /v1/auth/token`
- `POST /v1/management/advice`
- `GET /v1/audit-logs`
- `GET /v1/audit-logs/{auditLogId}`
- `GET /v1/dashboard/summary`
- `GET /v1/retrieval-evaluations/default`
- `POST /v1/retrieval-evaluations`
- `POST /v1/retrieval-evaluations/comparisons`
- `GET /v1/prompt-injection-evaluations/default`
- `POST /v1/prompt-injection-evaluations`
- `GET /v1/groundedness-evaluations/default`
- `POST /v1/groundedness-evaluations`
- `GET /v1/knowledge-documents`
- `POST /v1/knowledge-documents`
- `PUT /v1/knowledge-documents/{knowledgeDocumentId}`
- knowledge reindex job API 一式

また、以下も Go 側へ移植済みです。

- JWT 認証
- pgvector retrieval + keyword fallback
- groundedness judge
- prompt injection guard
- PII masking
- OpenAI timeout / retry / circuit breaker
- dashboard の OpenAI metrics
- dashboard の retrieval metrics

## 残件

### 1. retrieval の挙動差分

- Go 側で `rerankEnabled` は実装済み
- ただし Kotlin 側と完全に同じ評価結果になる保証はまだない
- 差分候補:
  - keyword 抽出の細部
  - chunking 差分
  - lexical rerank score の効き方

対応方針:
- 同一ケースで Kotlin / Go の comparison API を並べて、主要 variant の数値差分を継続確認する

### 2. dashboard の集計差分

Go 側で以下は実装済みです。

- `vectorAcceptedRetrievals`
- `vectorThresholdFallbacks`
- `vectorThresholdFilteredChunks`
- `openAiRetryAttempts`
- `openAiTimeouts`
- `openAiFailFastCount`
- `openAiCircuitOpenTransitions`
- `openAiCircuitState`

ただし以下はまだ差分候補です。

- Kotlin 側 Micrometer 指標との完全一致
- 再起動後の metrics 初期化タイミング
- Prometheus / Grafana への外部公開方式

### 3. reindex job の運用差分

Go 側は `knowledge_reindex_jobs` テーブルへ永続化済みで、以下も移植済みです。

- cleanup
- active job の duplicate control
- 起動時の `QUEUED / RUNNING` job 回復

残る論点は、cleanup 削除件数の外部メトリクス化や restart policy の細部調整です。

### 4. chunking 実装差分

Go 側の chunking は固定長ベースです。

- file:
  - `backend-go/internal/knowledge/manage.go`

Kotlin 側の chunking と完全一致まではまだ確認していません。
この差分は retrieval 品質にも影響します。

### 5. error response の文言差分

Spring 側と Echo 側で、HTTP status はほぼ揃っていますが、以下は差が残る可能性があります。

- `message`
- `details`
- validation error の粒度

frontend は現状動いていますが、完全置換前に contract test で潰す価値があります。

### 6. Go 側だけの補助 endpoint

Go 側には Kotlin OpenAPI にない補助 endpoint があります。

- `GET /health`
- `GET /version`
- `GET /v1/auth/me`

これらは運用上は便利ですが、正本 OpenAPI には含めていません。
必要なら Kotlin 側も含めて共通契約へ昇格させるか、補助 API として扱いを明記する必要があります。

## 優先順位

1. retrieval comparison の Kotlin / Go 差分確認
2. error response 差分の contract test 化
3. chunking 差分の評価
4. 補助 endpoint の契約扱い整理

## 完全リプレイス判断

2026-06-01 時点では、Go 側は主要機能をほぼカバーしています。
残っているのは「API がない」より「同じ API の挙動や運用完成度を詰める」フェーズです。

したがって、完全リプレイスの残件は以下の性質です。

- API 網羅不足ではない
- 実装品質の差分整理
- 運用機能の仕上げ
- 契約と評価の整合性確認
