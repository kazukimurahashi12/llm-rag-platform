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

現在の endpoint:
- `GET /health`
- `GET /version`
- `POST /v1/auth/token`
- `GET /v1/auth/me`
- `POST /v1/management/advice`

`POST /v1/management/advice` は現時点では JWT 必須の空実装です。
レスポンス shape は Kotlin 版に寄せていますが、advice / groundedness / retrievedDocuments はダミー値です。
