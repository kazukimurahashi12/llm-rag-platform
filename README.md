# ONBOARD AI

Management Support AI "ONBOARD-Core"


ONBOARD-Core は、ONBOARD AI プロダクトへの組み込みを想定して設計した、
マネジメント支援 AI バックエンドの MVP 兼ポートフォリオです。
単なるチャット UI ではなく、高精度（RAG）、高信頼（ガードレール）、高透明性（コスト・監査） を備えた、実運用可能な API 基盤を目指しています。

## 目的

このプロジェクトの目的は、Kotlin / Spring Boot を用いた堅牢なバックエンド開発の土台の上に、LLM を安全かつ採算が合う形で組み込むことです。

特に以下の課題を解決対象としています。

- ハルシネーションの抑制
- 1on1 や評価面談で扱う機密情報の保護
- トークン消費量と推論コストの可視化
- 監査可能な AI 利用基盤の整備

## コンセプト

- 高精度: 社内ガイドラインやマネジメント知識を RAG で参照する
- 高信頼: PII マスキング、プロンプトインジェクション対策、入力検証を組み込む
- 高透明性: token、cost、latency、audit log を記録し、運用判断につなげる


## 画面


<img width="1427" height="687" alt="スクリーンショット 2026-04-22 13 02 11" src="https://github.com/user-attachments/assets/227453f9-1ded-4c11-8dd0-a09aa0e4ee48" />

<img width="1432" height="686" alt="スクリーンショット 2026-04-22 13 02 46" src="https://github.com/user-attachments/assets/9cf567cb-3a05-46af-814c-49c8707cf88b" />

<img width="1438" height="606" alt="スクリーンショット 2026-04-22 13 03 01" src="https://github.com/user-attachments/assets/d01005c5-8fac-4381-a1a2-007ecdcba0b0" />

<img width="1440" height="699" alt="スクリーンショット 2026-04-22 13 03 08" src="https://github.com/user-attachments/assets/ccc194a8-4d6b-424f-8493-7fa367a74580" />


<img width="1435" height="695" alt="スクリーンショット 2026-04-23 13 22 53" src="https://github.com/user-attachments/assets/b55d6fd7-2b9d-46cc-a742-1dae09e2af78" />


<img width="1440" height="702" alt="スクリーンショット 2026-04-22 13 03 30" src="https://github.com/user-attachments/assets/39db1ea7-df33-4097-95e5-19600d803f37" />

<img width="1440" height="681" alt="スクリーンショット 2026-04-22 13 03 43" src="https://github.com/user-attachments/assets/8767df96-9ac2-4e63-a6f2-ea3d7cc2369a" />




## 現在の実装

現時点では、マネジメントアドバイス生成 API の最小構成を実装しています。

- `POST /v1/management/advice`
- OpenAPI 3.0 ベースで API スキーマを管理
- OpenAPI Generator により API interface / model を自動生成
- Spring Boot 3.x + Kotlin で実装
- OpenAI Responses API を呼び出して助言を生成
- usage 情報として model / token / estimated cost を返却
- バリデーションエラーおよび外部 API エラーのハンドリングを実装
- PostgreSQL + pgvector による vector 検索を実装
- ナレッジ文書登録時の chunking / embedding 生成を実装
- 再インデックスジョブの受付、状態確認、削除、リトライを実装
- Micrometer / Prometheus 向けに再インデックスジョブのメトリクスを公開

## 技術スタック

- Language / Framework: Kotlin, Spring Boot 3.x
- Build: Gradle, OpenAPI Generator
- Database: PostgreSQL, pgvector
- AI / LLM: OpenAI API
- Observability: Micrometer, Prometheus
- Cache / Rate Limit: Redis（拡張予定）

## システム構成

```text
[API Layer] -> [Service Layer] -> [AI/RAG Domain] -> [Infrastructure]
      |               |                |                |
   REST API      Business Logic    RAG/Prompt/Safety   pgvector/LLM API
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
- `usage.model`
- `usage.promptTokens`
- `usage.completionTokens`
- `usage.totalTokens`
- `usage.estimatedCostJpy`

### RAG / ナレッジ管理 API

- `GET /v1/knowledge-documents`
- `POST /v1/knowledge-documents`
- `POST /v1/knowledge-documents/reindex`
- `POST /v1/knowledge-documents/{knowledgeDocumentId}/reindex`
- `GET /v1/knowledge-documents/reindex-jobs`
- `GET /v1/knowledge-documents/reindex-jobs/{jobId}`
- `DELETE /v1/knowledge-documents/reindex-jobs/{jobId}`
- `POST /v1/knowledge-documents/reindex-jobs/{jobId}/retry`

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

- Java 17
- Node.js 24 系
- Docker / Docker Compose
- `OPENAI_API_KEY`

### Docker Compose で frontend / backend / postgres / observability をまとめて起動する

このリポジトリの `compose.yaml` では、以下のサービスをまとめて起動できます。

- `postgres`
- `backend`
- `frontend`
- `prometheus`
- `grafana`

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

4. Docker Compose で backend / frontend / postgres / prometheus / grafana を起動する

```bash
make up-build
```

5. ブラウザで確認する

```text
frontend: http://localhost:5173
backend: http://localhost:8080
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
- backend: `http://localhost:8080`
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

Flyway migration は backend 起動時に適用されます。
Prometheus は backend の `/actuator/prometheus` を scrape します。
Grafana は Prometheus datasource と `ONBOARD-Core Overview` dashboard を自動 provisioning します。

初回確認:

```bash
docker compose ps
docker compose logs backend
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

backend をローカル起動:

```bash
OPENAI_API_KEY=your_api_key ./gradlew bootRun
```

backend の標準 retrieval 設定:

- `RAG_TOP_K=3`
- `RAG_VECTOR_SEARCH_ENABLED=true`
- `RAG_MIN_SIMILARITY_SCORE=0.4`
- `RAG_RERANK_ENABLED=false`

frontend をローカル起動:

```bash
cd frontend
npm install
npm run dev
```

frontend の既定接続先:

- `VITE_API_BASE_URL=http://localhost:8080`

必要に応じて `frontend/.env` を作成し、以下を設定します。

```bash
VITE_API_BASE_URL=http://localhost:8080
VITE_APP_ENV=local
```

Makefile の主なコマンド:

```bash
make help
make up
make up-build
make down
make test
make backend-local
make frontend-local
make auth-admin
```

### ビルドと検証

backend のコンパイル:

```bash
./gradlew compileKotlin
```

backend のテスト:

```bash
./gradlew test
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
curl -X POST http://localhost:8080/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "change-me"
  }'
```

返却された `accessToken` を `Authorization: Bearer <token>` として利用します。

operator トークンを発行する場合:

```bash
curl -X POST http://localhost:8080/v1/auth/token \
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
curl -X POST http://localhost:8080/v1/management/advice \
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
curl -X POST http://localhost:8080/v1/management/advice \
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

### 監査ログ API の確認

事前に operator トークンを変数へ入れます。

```bash
export OPERATOR_TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "operator",
    "password": "change-operator"
  }' | jq -r '.accessToken')
```

監査ログ一覧を取得する:

```bash
curl "http://localhost:8080/v1/audit-logs?limit=20&offset=0" \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

監査ログ詳細を取得する:

```bash
curl http://localhost:8080/v1/audit-logs/1 \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

### Knowledge API の確認

Knowledge 一覧を取得する:

```bash
curl "http://localhost:8080/v1/knowledge-documents?limit=20&offset=0" \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

admin で文書を登録する:

```bash
curl -X POST http://localhost:8080/v1/knowledge-documents \
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
export ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "change-me"
  }' | jq -r '.accessToken')
```

全件再インデックスジョブを受け付ける:

```bash
curl -X POST http://localhost:8080/v1/knowledge-documents/reindex \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

特定文書だけ再インデックスする:

```bash
curl -X POST http://localhost:8080/v1/knowledge-documents/1/reindex \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

ジョブ状態を確認する:

```bash
curl http://localhost:8080/v1/knowledge-documents/reindex-jobs/<jobId> \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

ジョブ一覧を期間指定で確認する:

```bash
curl "http://localhost:8080/v1/knowledge-documents/reindex-jobs?status=COMPLETED&acceptedFrom=2026-04-15T00:00:00Z&acceptedTo=2026-04-15T23:59:59Z" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

ジョブ一覧を完了日時の昇順で確認する:

```bash
curl "http://localhost:8080/v1/knowledge-documents/reindex-jobs?sortBy=completedAt&sortDirection=asc" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

失敗ジョブをリトライする:

```bash
curl -X POST http://localhost:8080/v1/knowledge-documents/reindex-jobs/<jobId>/retry \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

完了済みまたは失敗済みジョブを削除する:

```bash
curl -X DELETE http://localhost:8080/v1/knowledge-documents/reindex-jobs/<jobId> \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### メトリクスの確認

Actuator は `health`, `metrics`, `prometheus` を公開しています。

メトリクス名の例:

- `knowledge.reindex.jobs.accepted`
- `knowledge.reindex.jobs.retried`
- `knowledge.reindex.jobs.deleted`
- `knowledge.reindex.jobs.completed`
- `knowledge.reindex.jobs.failed`
- `knowledge.reindex.jobs.execution`
- `knowledge.reindex.jobs.cleanup.deleted`
- `knowledge.retrieval.vector.accepted`
- `knowledge.retrieval.vector.threshold.filtered`
- `knowledge.retrieval.vector.threshold.fallback`

retrieval しきい値を調整するときは、以下を見ます。

- `knowledge.retrieval.vector.accepted`
  - 最終的に vector 検索が採用された回数
- `knowledge.retrieval.vector.threshold.filtered`
  - 類似度しきい値で何件落ちているか
- `knowledge.retrieval.vector.threshold.fallback`
  - しきい値適用後に vector 0 件となり、keyword fallback へ落ちた回数

`vector.accepted` が低く、`threshold.filtered` または `threshold.fallback` が増え続ける場合は、`rag.min-similarity-score` が厳しすぎる可能性があります。

現在の標準値は `RAG_MIN_SIMILARITY_SCORE=0.4` です。標準評価ケースでは `0.5` に上げると Precision は下がり、fallback も増えやすくなるため、まずは `0.4` を基準に調整します。

`/actuator/metrics` で一覧を確認する:

```bash
curl http://localhost:8080/actuator/metrics
```

個別メトリクスを確認する:

```bash
curl "http://localhost:8080/actuator/metrics/knowledge.reindex.jobs.accepted"
```

retrieval threshold 関連の個別メトリクスを確認する:

```bash
curl "http://localhost:8080/actuator/metrics/knowledge.retrieval.vector.accepted"
curl "http://localhost:8080/actuator/metrics/knowledge.retrieval.vector.threshold.filtered"
curl "http://localhost:8080/actuator/metrics/knowledge.retrieval.vector.threshold.fallback"
```

Prometheus 形式で取得する:

```bash
curl http://localhost:8080/actuator/prometheus
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
- dashboard: `ONBOARD-Core Overview`

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


## 補足

このリポジトリは、単なる LLM デモではなく、`AI を安全に、かつ事業運用可能な形で組み込む` ことを主眼にしたポートフォリオとして設計しています。
