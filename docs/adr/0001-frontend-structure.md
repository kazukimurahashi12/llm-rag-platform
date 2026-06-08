# 0001 Frontend Structure

## Status
Accepted

## Context
現行フロントエンドは Vite + React + TypeScript + React Router + MUI で構成されている。

## Decision
以下の責務分離を維持する。

- `src/pages`: 画面単位のコンポーネント
- `src/components`: 再利用 UI
- `src/api`: API 呼び出し
- `src/lib`: 認証や整形などの補助ロジック
- `src/app`: ルーター、テーマ、Provider
- `src/components/layout/AppShell.tsx`: ログイン後の共通殻

## Consequences
画面追加時も既存構造へ寄せる。ルーティング方式や AppShell 中心の構造は無断で変更しない。
