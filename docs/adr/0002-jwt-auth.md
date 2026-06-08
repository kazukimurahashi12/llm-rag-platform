# 0002 JWT Auth

## Status
Accepted

## Context
保護 API は Basic 認証ではなく JWT Bearer 認証へ移行済み。

## Decision
認証は以下で統一する。

- トークン取得: `POST /v1/auth/token`
- 保存先: `localStorage`
- 保存項目: `username`, `accessToken`, `expiresAt`, `roles`
- 401 応答時: 保存済み session を破棄する

認証状態の実装基点は `frontend/src/lib/auth.ts` と `frontend/src/api/client.ts` とする。

## Consequences
保存キーや保存形式は互換性に影響するため無断変更しない。Bearer 以外の認証方式を混在させない。
