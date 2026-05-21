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

`POST /v1/management/advice` は現時点では JWT 必須の最小実装です。
OpenAI 呼び出しは動きますが、RAG / groundedness judge / retrievedDocuments は未移植です。
