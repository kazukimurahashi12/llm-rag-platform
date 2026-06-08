# AGENTS.md

このリポジトリでフロントエンド変更を行う前に確認すること:

- 作業対象は `frontend/`
- 画面構成は `docs/adr/0001-frontend-structure.md`
- JWT 認証は `docs/adr/0002-jwt-auth.md`
- API 呼び出し境界は `docs/adr/0003-api-client-boundary.md`
- UI シェル構造は `docs/adr/0004-ui-shell-boundary.md`

実行コマンド:

- `cd frontend && npm run dev`
- `cd frontend && npm run build`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run lint`
- `cd frontend && npm test`

禁止事項:

- JWT 保存形式を無断変更しない
- `frontend/src/api/client.ts` を通さず直接 HTTP 呼び出しを増やさない
- 未ログイン時に管理画面を表示しない
- AGENTS.md に長い説明を書かない。詳細は `docs/adr/` に置く

完了前チェック:

- `cd frontend && npm run build`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run lint`
- `cd frontend && npm test`
