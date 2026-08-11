# ONBOARD Guide API

Management Support AI "ONBOARD Guide API"

マネジメント支援 AI バックエンドの ポートフォリオです。
単なるチャット UI ではなく、RAG、ガードレール、コスト・監査 を備えた、実運用可能なAPI基盤を目指しています。

## コンセプト

マネージャーが、1on1・評価面談・オンボーディング支援などで「このメンバーにどう関わるべきか」を相談したいときに使うAIバックエンドです。
社内ナレッジをRAGで参照しながら、根拠つきで実務的な助言を返し、あわせて監査・コスト・検索品質も管理できます。

- 社内ガイドラインやマネジメント知識を RAG で参照する
- PII マスキング、プロンプトインジェクション対策、入力検証を組み込む
- token、cost、latency、audit log を記録し、運用判断につなげる
- ACE モデル（Ability / Culture / Expectation）で相談とナレッジを構造化する


## 目的

このプロジェクトの目的は、Go / Echo を主 backend とした運用可能な API 基盤の上に、
LLM を安全かつ採算が合う形で組み込むことです。

特に以下の課題を解決対象としています。

- トークン消費量と推論コストの可視化
- 監査可能な AI 利用基盤の整備
- ハルシネーションの抑制
- 1on1 や評価面談で扱う機密情報の保護

## 画面


<img width="1427" height="687" alt="スクリーンショット 2026-04-22 13 02 11" src="https://github.com/user-attachments/assets/227453f9-1ded-4c11-8dd0-a09aa0e4ee48" />

<img width="1437" height="690" alt="スクリーンショット 2026-05-05 19 03 19" src="https://github.com/user-attachments/assets/a5fe0c7e-9f9d-4fdd-b6a2-24b7456c2036" />


<img width="1438" height="606" alt="スクリーンショット 2026-04-22 13 03 01" src="https://github.com/user-attachments/assets/d01005c5-8fac-4381-a1a2-007ecdcba0b0" />

<img width="1440" height="699" alt="スクリーンショット 2026-04-22 13 03 08" src="https://github.com/user-attachments/assets/ccc194a8-4d6b-424f-8493-7fa367a74580" />


<img width="1435" height="695" alt="スクリーンショット 2026-04-23 13 22 53" src="https://github.com/user-attachments/assets/b55d6fd7-2b9d-46cc-a742-1dae09e2af78" />


<img width="1440" height="702" alt="スクリーンショット 2026-04-22 13 03 30" src="https://github.com/user-attachments/assets/39db1ea7-df33-4097-95e5-19600d803f37" />

<img width="1440" height="681" alt="スクリーンショット 2026-04-22 13 03 43" src="https://github.com/user-attachments/assets/8767df96-9ac2-4e63-a6f2-ea3d7cc2369a" />




## 現在の実装

現時点では、Go 版 backend を正本としてマネジメントアドバイス生成 API と周辺運用 API を実装しています。

- `POST /v1/management/advice`
- OpenAPI 3.0 ベースで API スキーマを管理
- Go 側 OpenAPI 正本から oapi-codegen により model を自動生成
- Go / Echo で実装
- OpenAI Agents SDK sidecar による Agent MVP を実装
- OpenAI Responses API を呼び出して助言を生成
- OpenAI 呼び出し層に timeout / retry / circuit breaker を入れ、一時障害時は fail-fast で明確なエラーを返す
- usage 情報として model / token / estimated cost を返却
- バリデーションエラーおよび外部 API エラーのハンドリングを実装
- PostgreSQL + pgvector による vector 検索を実装
- ナレッジ文書登録時の chunking / embedding 生成を実装
- ナレッジ文書へ `aceCategory` を付与し、ACE 分析結果を advice response へ返却
- query を ACE 分類し、同カテゴリ文書を retrieval で軽く優先する boost を実装
- advice 生成後に groundedness を自動評価し、score / status / reason を返却
- groundedness が低い場合は、断定的な回答を返さず保守的な fallback 応答へ切り替える
- groundedness を audit log と dashboard に保存・集計する
- retrieval / prompt injection / groundedness の標準評価ケースを持ち、評価 API で回帰確認できる
- Agent が既存 RAG / dashboard / evaluation / advice API を tool として利用できる
- prompt injection guard は日本語パターン追加と正規化強化まで実装済みで、単語単独の文脈判定やスコアリングは今後の改善項目
- 再インデックスジョブの受付、状態確認、削除、リトライを実装
- Prometheus 向けに Go backend のメトリクスを公開
- 今後の実装予定として、冪等キー（Idempotency-Key）の導入を検討（拡張予定）

## 技術スタック

- Language / Framework: Go, Echo
- Agent Runtime: Node.js, TypeScript, OpenAI Agents SDK
- Build: Go modules, oapi-codegen, npm
- Database: PostgreSQL, pgvector
- AI / LLM: OpenAI API
- Observability: Prometheus, Grafana
- Cache / Rate Limit: Redis（拡張予定）

## システム構成

```text
frontend
  -> backend-go
      -> service / RAG / audit / evaluation
      -> agent-runtime
          -> OpenAI Agents SDK
          -> backend-go tools
```

## API

### `POST /v1/management/advice`

マネージャーの相談内容を入力として、実践的で心理的安全性に配慮したアドバイスを返します。

入力例:

```json
{
  "memberContext": {
    "situation": "最近、週報の提出が遅れがちで、チームへの共有が漏れている。",
    "targetGoal": "モチベーションを下げずに、報告の重要性を理解させたい。"
  },
  "setting": {
    "tone": "empathetic",
    "model": "gpt-4o-mini"
  }
}
```

返却値:

- `advice`
- `aceAnalysis.primaryCategory`
- `aceAnalysis.reason`
- `groundednessEvaluation.groundednessScore`
- `groundednessEvaluation.status`
- `groundednessEvaluation.reason`
- `groundednessEvaluation.fallbackApplied`
- `usage.model`
- `usage.promptTokens`
- `usage.completionTokens`
- `usage.totalTokens`
- `usage.estimatedCostJpy`

`groundednessEvaluation.status=LOW_GROUNDEDNESS` かつ `fallbackApplied=true` の場合は、根拠不足と判断して保守的な定型応答へ切り替えます。

## groundedness fallback 方針

- advice 生成後に、取得根拠と回答を使って LLM-as-a-judge で groundedness を採点します
- `rag.groundedness-threshold` 未満なら `LOW_GROUNDEDNESS` と判定します
- `rag.groundedness-fallback-enabled=true` の場合、低信頼の回答本文はそのまま返さず、ナレッジ見直しや再インデックスを促す保守的な fallback 応答へ置き換えます
- groundedness の `score / status / reason / fallbackApplied` は response と `audit_logs` に残し、dashboard では平均値と fallback 件数を集計します

主な設定値:

- `RAG_GROUNDEDNESS_THRESHOLD`
- `RAG_GROUNDEDNESS_FALLBACK_ENABLED`

### RAG / ナレッジ管理 API

- `GET /v1/knowledge-documents`
- `POST /v1/knowledge-documents`
- `PUT /v1/knowledge-documents/{knowledgeDocumentId}`
- `POST /v1/knowledge-documents/reindex`
- `POST /v1/knowledge-documents/{knowledgeDocumentId}/reindex`
- `GET /v1/knowledge-documents/reindex-jobs`
- `GET /v1/knowledge-documents/reindex-jobs/{jobId}`
- `DELETE /v1/knowledge-documents/reindex-jobs/{jobId}`
- `POST /v1/knowledge-documents/reindex-jobs/{jobId}/retry`

### 評価 API

- `GET /v1/retrieval-evaluations/default`
- `POST /v1/retrieval-evaluations/comparisons`
- `GET /v1/prompt-injection-evaluations/default`
- `GET /v1/groundedness-evaluations/default`

Go backend に embed している標準評価ケースでは、取得根拠に沿った回答と、根拠不足で fallback すべき回答を混在させて評価できます。

Go 版 backend では、標準 retrieval ケース用の seed を投入して keyword retrieval 評価を実行できます。

```bash
make backend-go-retrieval-eval
```

### Agent API

- `POST /v1/agent/tasks`

Agent API は Go backend が JWT 認証を検証した上で、OpenAI Agents SDK sidecar の `agent-runtime` へ task を委譲します。
MVP では read-only tools と advice 生成だけを許可しています。

利用する tool:

- `knowledge.search`
- `dashboard.summary`
- `retrieval.evaluate`
- `groundedness.evaluate`
- `promptInjection.evaluate`
- `advice.generate`

書き込み tool は MVP では無効です。

## Go 正本化

現在は Go 版 backend を唯一の実行正本としています。通常の Compose 起動、frontend 接続、Prometheus scrape、Makefile の標準テストは Go 版を対象にしています。

Kotlin 版から Go 版への移行履歴は、[kotlin-go-gap-analysis.md](/Users/kazuki/Documents/GitHub/kazukimurahashi12/llm-rag-platform/docs/kotlin-go-gap-analysis.md) にまとめています。

同一 seed を使った標準 retrieval 評価では、Go 版単体で回帰確認できます。

Knowledge 文書は `aceCategory` を持ちます。

- `ABILITY`: スキル習得、知識学習、業務理解
- `CULTURE`: 文化適応、報連相、コミュニケーション
- `EXPECTATION`: 役割期待、目標整合、評価観点

`PUT /v1/knowledge-documents/{knowledgeDocumentId}` では、title / content / accessScope / allowedUsernames / aceCategory を更新できます。更新時は対象文書の chunk を全再生成し、vector 検索が有効な場合は embedding も同期で再作成します。

ジョブ一覧 API は以下の絞り込みに対応しています。

- `status`
- `knowledgeDocumentId`
- `acceptedFrom`
- `acceptedTo`
- `completedFrom`
- `completedTo`

ジョブ一覧 API は以下のソートにも対応しています。

- `sortBy=acceptedAt|completedAt`
- `sortDirection=asc|desc`

## 設計判断

このプロジェクトでは、以下の設計判断を重視しています。

- API 契約を OpenAPI で先に定義し、実装との乖離を防ぐ
- LLM 呼び出しを service layer に隔離し、将来的なモデル差し替えを容易にする
- 監査、コスト、レート制御、安全性を MVP の早い段階から設計に含める
- RAG や guardrail を後付けではなく、最初からレイヤとして扱う

## 主要データモデル

想定している主要テーブルは以下です。

- `knowledge_documents`: `content`, `embedding`, `metadata`
- `audit_logs`: `user_id`, `prompt`, `response`, `latency_ms`
- `token_usages`: `request_id`, `model`, `total_tokens`, `cost_jpy`

## 評価指標

このプロジェクトでは、単に動くことではなく、以下の定量指標で改善できる状態を目指します。

- Groundedness
- Answer correctness
- Latency
- Cost per request
- Model ごとの cost / performance 比較

## 起動と確認

### 前提

- Go 1.26
- Node.js 24 系
- Docker / Docker Compose
- `OPENAI_API_KEY`

### Docker Compose で frontend / backend / postgres / observability をまとめて起動する

このリポジトリの `compose.yaml` では、以下のサービスをまとめて起動できます。

- `postgres`
- `backend-go`
- `agent-runtime`
- `frontend`
- `prometheus`
- `grafana`

`backend-go` は Go / Echo 版を `8081` で起動します。
`agent-runtime` は OpenAI Agents SDK sidecar を `8091` で起動します。

このリポジトリを初めて clone して、ローカルで起動できる状態にする最短手順:

1. リポジトリを clone して移動する

```bash
git clone <repository-url>
cd llm-rag-platform
```

2. 初回セットアップを実行する

```bash
make bootstrap
```

3. `.env` を開いて `OPENAI_API_KEY` を設定する

```bash
OPENAI_API_KEY=your_openai_api_key
```

4. Docker Compose で backend-go / agent-runtime / frontend / postgres / prometheus / grafana を起動する

```bash
make up-build
```

5. ブラウザで確認する

```text
frontend: http://localhost:5173
backend-go: http://localhost:8081
agent-runtime: http://localhost:8091
prometheus: http://localhost:9090
grafana: http://localhost:3000
```

初回クローン時の推奨手順:

```bash
make bootstrap
```

その後、`.env` の `OPENAI_API_KEY` を設定してから起動します。

```bash
make up-build
```

起動:

```bash
OPENAI_API_KEY=your_api_key docker compose up --build
```

バックグラウンド起動:

```bash
OPENAI_API_KEY=your_api_key docker compose up -d --build
```

起動後のアクセス先:

- frontend: `http://localhost:5173`
- backend-go: `http://localhost:8081`
- postgres: `localhost:5432`
- prometheus: `http://localhost:9090`
- grafana: `http://localhost:3000`

compose で使う既定値:

- DB 名: `rag_db`
- DB ユーザー名: `postgres`
- DB パスワード: `postgres`
- admin: `admin / change-me`
- operator: `operator / change-operator`
- grafana: `admin / admin`

DB migration は Go backend 起動時に適用済みスキーマを前提にします。既存 Compose では PostgreSQL volume を利用します。
Prometheus は Go 版 backend の `/metrics` を scrape します。
Grafana は Prometheus datasource と `ONBOARD Guide API Overview` dashboard を自動 provisioning します。

初回確認:

```bash
docker compose ps
docker compose logs backend-go
docker compose logs frontend
docker compose logs postgres
docker compose logs prometheus
docker compose logs grafana
```

停止:

```bash
docker compose down
```

データも削除:

```bash
docker compose down -v
```

### ローカル個別起動

PostgreSQL だけ compose で起動:

```bash
docker compose up -d postgres
```

Go backend をローカル起動:

```bash
make backend-go-local
```

backend の標準 retrieval 設定:

- `RAG_TOP_K=3`
- `RAG_VECTOR_SEARCH_ENABLED=true`
- `RAG_MIN_SIMILARITY_SCORE=0.4`
- `RAG_RERANK_ENABLED=false`

ACE 対応後の retrieval では、query をルールベースで `ABILITY / CULTURE / EXPECTATION` に分類し、同じ `aceCategory` を持つナレッジ文書へ軽い boost をかけます。

frontend をローカル起動:

```bash
cd frontend
npm install
npm run dev
```

frontend の既定接続先:

- `VITE_API_BASE_URL=http://localhost:8081`

必要に応じて `frontend/.env` を作成し、以下を設定します。

```bash
VITE_API_BASE_URL=http://localhost:8081
VITE_APP_ENV=local
```

Makefile の主なコマンド:

```bash
make help
make up
make up-build
make down
make test
make backend-go-local
make frontend-local
make auth-admin
```

### ビルドと検証

Go backend のテスト:

```bash
cd backend-go && go test ./...
```

frontend のビルド:

```bash
cd frontend
npm install
npm run build
```

### 認証

`Advice` は現状そのまま利用できますが、以下は JWT Bearer 認証が必要です。

- `Audit Logs`
- `Knowledge`
- `Reindex Jobs`

まずトークンを発行します。

```bash
curl -X POST http://localhost:8081/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "change-me"
  }'
```

返却された `accessToken` を `Authorization: Bearer <token>` として利用します。

operator トークンを発行する場合:

```bash
curl -X POST http://localhost:8081/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "operator",
    "password": "change-operator"
  }'
```

フロントエンド右上の `Credentials` には以下を入力します。

- admin: `admin / change-me`
- operator: `operator / change-operator`

フロントエンドは入力された資格情報を使って `/v1/auth/token` から Bearer トークンを取得し、以後の保護 API 呼び出しへ付与します。

### Advice API の確認

未認証でも `Advice` は呼び出せます。

```bash
curl -X POST http://localhost:8081/v1/management/advice \
  -H 'Content-Type: application/json' \
  -d '{
    "memberContext": {
      "situation": "週報の提出が遅れている",
      "targetGoal": "1on1で改善したい"
    },
    "setting": {
      "tone": "empathetic",
      "model": "gpt-4o-mini"
    }
  }'
```

Bearer トークン付きでも呼び出せます。認証付きで呼ぶと、文書 ACL に応じて参照可能なナレッジの範囲が変わります。

```bash
curl -X POST http://localhost:8081/v1/management/advice \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $OPERATOR_TOKEN" \
  -d '{
    "memberContext": {
      "situation": "運用担当向けの相談",
      "targetGoal": "社内ガイドラインを踏まえて助言したい"
    },
    "setting": {
      "tone": "empathetic",
      "model": "gpt-4o-mini"
    }
  }'
```

`retrievedDocuments` は caller の権限に応じて変わります。

- 未認証: `SHARED` の文書だけ参照
- `OPERATOR`: `SHARED` に加え、`allowedUsernames` に含まれる文書を参照
- `ADMIN`: すべての文書を参照

`aceAnalysis` には、相談内容の主要 ACE カテゴリと、その判断理由が返ります。

### 監査ログ API の確認

事前に operator トークンを変数へ入れます。

```bash
export OPERATOR_TOKEN=$(curl -s -X POST http://localhost:8081/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "operator",
    "password": "change-operator"
  }' | jq -r '.accessToken')
```

監査ログ一覧を取得する:

```bash
curl "http://localhost:8081/v1/audit-logs?limit=20&offset=0" \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

監査ログ詳細を取得する:

```bash
curl http://localhost:8081/v1/audit-logs/1 \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

### Knowledge API の確認

Knowledge 一覧を取得する:

```bash
curl "http://localhost:8081/v1/knowledge-documents?limit=20&offset=0" \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

admin で文書を登録する:

```bash
curl -X POST http://localhost:8081/v1/knowledge-documents \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "title": "週報運用ガイド",
    "content": "週報は毎週金曜までに提出し、1on1で振り返る。",
    "accessScope": "SHARED",
    "allowedUsernames": []
  }'
```

### 再インデックスの確認

事前に admin トークンを変数へ入れます。

```bash
export ADMIN_TOKEN=$(curl -s -X POST http://localhost:8081/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "change-me"
  }' | jq -r '.accessToken')
```

全件再インデックスジョブを受け付ける:

```bash
curl -X POST http://localhost:8081/v1/knowledge-documents/reindex \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

特定文書だけ再インデックスする:

```bash
curl -X POST http://localhost:8081/v1/knowledge-documents/1/reindex \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

ジョブ状態を確認する:

```bash
curl http://localhost:8081/v1/knowledge-documents/reindex-jobs/<jobId> \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

ジョブ一覧を期間指定で確認する:

```bash
curl "http://localhost:8081/v1/knowledge-documents/reindex-jobs?status=COMPLETED&acceptedFrom=2026-04-15T00:00:00Z&acceptedTo=2026-04-15T23:59:59Z" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

ジョブ一覧を完了日時の昇順で確認する:

```bash
curl "http://localhost:8081/v1/knowledge-documents/reindex-jobs?sortBy=completedAt&sortDirection=asc" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

失敗ジョブをリトライする:

```bash
curl -X POST http://localhost:8081/v1/knowledge-documents/reindex-jobs/<jobId>/retry \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

完了済みまたは失敗済みジョブを削除する:

```bash
curl -X DELETE http://localhost:8081/v1/knowledge-documents/reindex-jobs/<jobId> \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### メトリクスの確認

Go 版 backend は Prometheus scrape 用に `/metrics` を公開しています。

メトリクス名の例:

- `knowledge_reindex_jobs_accepted_total`
- `knowledge_reindex_jobs_retried_total`
- `knowledge_reindex_jobs_deleted_total`
- `knowledge_reindex_jobs_completed_total`
- `knowledge_reindex_jobs_failed_total`
- `knowledge_reindex_jobs_execution_seconds_sum`
- `knowledge_reindex_jobs_cleanup_deleted_total`
- `knowledge_retrieval_vector_accepted_total`
- `knowledge_retrieval_vector_threshold_filtered_chunks_total`
- `knowledge_retrieval_vector_threshold_fallback_total`

retrieval しきい値を調整するときは、以下を見ます。

- `knowledge_retrieval_vector_accepted_total`
  - 最終的に vector 検索が採用された回数
- `knowledge_retrieval_vector_threshold_filtered_chunks_total`
  - 類似度しきい値で何件落ちているか
- `knowledge_retrieval_vector_threshold_fallback_total`
  - しきい値適用後に vector 0 件となり、keyword fallback へ落ちた回数

`knowledge_retrieval_vector_accepted_total` が低く、threshold 関連の metric が増え続ける場合は、`RAG_MIN_SIMILARITY_SCORE` が厳しすぎる可能性があります。

現在の標準値は `RAG_MIN_SIMILARITY_SCORE=0.4` です。標準評価ケースでは `0.5` に上げると Precision は下がり、fallback も増えやすくなるため、まずは `0.4` を基準に調整します。

Prometheus 形式で取得する:

```bash
curl http://localhost:8081/metrics
```

### Prometheus / Grafana の確認

Prometheus の scrape target を確認する:

```bash
open http://localhost:9090/targets
```

Prometheus で retrieval metric を確認する:

```promql
knowledge_retrieval_vector_accepted_total
knowledge_retrieval_vector_threshold_fallback_total
```

Grafana にアクセスする:

```bash
open http://localhost:3000
```

ログイン:

- user: `admin`
- password: `admin`

Grafana には以下が自動で作成されます。

- datasource: `Prometheus`
- dashboard: `ONBOARD Guide API Overview`

dashboard では以下を確認できます。

- Retrieval activity
- Reindex job events
- Reindex completed / failed count
- Reindex execution duration
- HTTP latency

CLI で確認する場合:

```bash
curl http://localhost:9090/api/v1/targets
curl http://localhost:3000/api/health
```
