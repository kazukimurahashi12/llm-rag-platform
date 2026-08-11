# Kotlin / Go 移行完了ログ

このドキュメントは、Kotlin / Spring Boot 版 `backend/` から Go / Echo 版 `backend-go/` への移行状況を整理したものです。
Phase B で Kotlin 実装はこのブランチから物理削除し、Go 版 backend を唯一の実行正本にしました。
削除前の Kotlin 実装は `archive/kotlin-backend-before-go-only` ブランチで参照できます。

## 現状

- OpenAPI 契約:
  - 正本: `backend-go/openapi/management-advice-api.yaml`
- generated model は `make backend-go-codegen` で再作成します。
- 通常の Compose 起動、frontend 接続、Prometheus scrape、Makefile の標準テストは Go 版 backend を対象にしています。

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
- advice response / audit log の costJpy 計算
- Kotlin と同じ chunking 設定（chunkSize=180 / overlap=40 / 空白正規化）
- Prometheus scrape 用 `/metrics`
- 標準 retrieval ケース用 seed と Go 評価 CLI

## 残件

### 1. retrieval の追加確認

- 標準 seed + keyword retrieval 条件では、Go / Kotlin の集計値と `retrievedDocumentTitles` は一致確認済み
- vector search enabled 条件でも、同一 DB / 同一 seed に対して Go / Kotlin の主要集計は概ね一致確認済み
  - `totalCases`: 12 / 12
  - `matchedCases`: 12 / 12
  - `hitRate`: 1.0 / 1.0
  - `averageRecallAtK`: 0.9583333333333334 / 0.9583333333333334
  - `averagePrecisionAtK`: 0.6388888888888888 / 0.6388888888888888
  - `averageRetrievedCount`: 2.75 / 2.75
- vector search enabled 条件では一部ケースの順位だけ差分があります
  - Go: `meanReciprocalRank = 1.0`
  - Kotlin: `meanReciprocalRank = 0.9583333333333334`
  - 差分ケースでは Go 側の方が期待文書を上位に返しており、Kotlin 削除の blocker ではありません

対応方針:
- embedding を含む比較は OpenAI API 状態に依存するため、完全同順位ではなく主要指標と期待文書 hit を削除判断基準にする

### 2. dashboard / metrics の集計差分

Go 側で以下は実装済みです。

- `vectorAcceptedRetrievals`
- `vectorThresholdFallbacks`
- `vectorThresholdFilteredChunks`
- `openAiRetryAttempts`
- `openAiTimeouts`
- `openAiFailFastCount`
- `openAiCircuitOpenTransitions`
- `openAiCircuitState`

Go 側は `/metrics` を公開し、Compose の Prometheus は Go 版 backend を scrape します。
Kotlin 削除後の運用確認では以下を見る対象にします。

- 再起動後の metrics 初期化タイミング
- Grafana dashboard の実機表示確認

### 3. reindex job の運用差分

Go 側は `knowledge_reindex_jobs` テーブルへ永続化済みで、以下も移植済みです。

- cleanup
- active job の duplicate control
- 起動時の `QUEUED / RUNNING` job 回復

残る論点は、cleanup 削除件数の外部メトリクス化や restart policy の細部調整です。

### 4. chunking 実装

Go 側の chunking は Kotlin と同じ `chunkSize=180 / overlap=40`、空白正規化、空入力 0 chunk の仕様へ寄せています。
標準 seed + keyword retrieval 条件では、retrieval 評価値も Kotlin と一致確認済みです。

### 5. error response の文言差分

Spring 側と Echo 側で、HTTP status はほぼ揃っていますが、以下は差が残る可能性があります。

- `message`
- `details`
- validation error の粒度

Go 側では validation / invalid body / 認証 / 権限 error response の contract test を追加済みです。
Kotlin 固有例外の細かな `details` 粒度への完全一致は削除判断の対象外とし、Go 側 contract を正とします。

### 6. Go 側だけの補助 endpoint

Go 側には正本 OpenAPI に含めていない補助 endpoint があります。

- `GET /health`
- `GET /version`
- `GET /v1/auth/me`

frontend はこれらの補助 endpoint を直接参照していません。
削除前確認では、`GET /health` は Go-only smoke の対象、`GET /version` と `GET /v1/auth/me` は Go 版の運用補助 API として残す判断です。
正本 OpenAPI には現時点では含めず、公開 API ではなく運用補助 API として扱います。

## 優先順位

1. Go-only 構成での継続的な smoke / regression
2. Agent orchestration 層の設計・実装

## Kotlin 削除前チェック結果

2026-08-09 時点で、Kotlin 物理削除前の確認項目は以下です。

- Grafana / Prometheus
  - Prometheus target は `backend-go:8081/metrics` で `up`
  - Grafana datasource は `http://prometheus:9090`
  - dashboard は provision 済み
- vector retrieval
  - Go / Kotlin とも `matchedCases = 12`, `hitRate = 1.0`
  - 一部順位差はあるが、Go 側の MRR が高く品質退行ではない
- 補助 endpoint
  - frontend 依存なし
  - `GET /health` は smoke で担保
  - `GET /version`, `GET /v1/auth/me` は Go 版運用補助 API として残す

## 完全リプレイス判断

Go 側は主要機能をカバーしており、Phase B で Go 版を唯一の実行正本にしました。
残っているのは「Kotlin との差分解消」ではなく、Go 版としての運用改善です。

したがって、完全リプレイスの残件は以下の性質です。

- API 網羅不足ではない
- 実装品質の差分整理
- 運用機能の仕上げ
- 契約と評価の整合性確認
