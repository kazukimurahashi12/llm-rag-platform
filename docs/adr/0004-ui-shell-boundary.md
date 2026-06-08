# 0004 UI Shell Boundary

## Status
Accepted

## Context
未ログイン時に管理画面を見せると、保護画面への誤遷移や 401 ノイズが増える。

## Decision
表示構造は以下で固定する。

- 未ログイン時: `/login` のみ表示
- ログイン後: `AppShell` 配下の各画面を表示
- ヘッダーとサイドバーは `AppShell` で共通管理

## Consequences
各ページで独自ヘッダーを増やさない。未ログイン時に管理画面本体を表示しない。
