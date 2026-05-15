# backend-go

Go / Echo 版 backend の並行実装用ディレクトリです。

現時点の目的:
- Kotlin / Spring Boot backend を壊さずに、Go 版を段階的に作る
- まずは起動基盤、設定、health endpoint から作る
- 後続で advice、RAG、OpenAI 連携を移植する

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
