# 0003 API Client Boundary

## Status
Accepted

## Context
認証ヘッダ、共通エラー処理、401 時の session 破棄を各画面へ分散させると破綻しやすい。

## Decision
HTTP 呼び出しは `frontend/src/api/client.ts` を共通入口とする。

- 共通の base URL を使う
- Bearer token の付与を一元化する
- 401 時の session 破棄を一元化する
- 各 API 関数は `src/api/*.ts` に置く

## Consequences
各ページや各コンポーネントで直接 `fetch` や独自 Axios instance を乱立させない。
