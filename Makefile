SHELL := /bin/bash

.PHONY: help bootstrap env frontend-env check-openai install-frontend \
	up up-build down down-volumes logs ps \
	postgres backend-go agent-runtime frontend backend-go-build agent-runtime-build frontend-build \
	test test-go build build-frontend build-agent-runtime backend-go-codegen \
	backend-go-retrieval-eval smoke-go backend-go-local agent-runtime-local frontend-local auth-admin auth-operator


# 初回クローン時に .env 作成と frontend 依存関係インストールをまとめて行う
bootstrap: env frontend-env install-frontend
	@echo "初回セットアップが完了しました。"
	@echo "次の手順:"
	@echo "  1. .env を開いて OPENAI_API_KEY を設定"
	@echo "  2. make up-build を実行"

# backend 用の .env を example から作成
env:
	@if [ ! -f .env ]; then cp .env.example .env; echo ".env.example から .env を作成しました"; else echo ".env は既に存在します"; fi

# frontend 用の .env を example から作成
frontend-env:
	@if [ ! -f frontend/.env ]; then cp frontend/.env.example frontend/.env; echo "frontend/.env.example から frontend/.env を作成しました"; else echo "frontend/.env は既に存在します"; fi

# frontend と agent-runtime の npm 依存関係をインストールする
install-frontend:
	cd frontend && npm install
	cd agent-runtime && npm install

# Docker 起動前に OPENAI_API_KEY が設定済みかを確認する
check-openai:
	@if [ ! -f .env ]; then echo ".env がありません。make env を実行してください"; exit 1; fi
	@if ! grep -q '^OPENAI_API_KEY=' .env; then echo ".env に OPENAI_API_KEY がありません"; exit 1; fi
	@if grep -q '^OPENAI_API_KEY=$$' .env || grep -q '^OPENAI_API_KEY=your_openai_api_key$$' .env; then echo "このターゲットを実行する前に .env の OPENAI_API_KEY を設定してください"; exit 1; fi

# Docker Compose のサービスを現在のイメージでバックグラウンド起動
up: check-openai
	docker compose up -d

# Docker Compose のサービスを再ビルドしてバックグラウンド起動
up-build: check-openai
	docker compose up -d --build

# Docker Compose のサービスを停止して削除
down:
	docker compose down

# Docker Compose のサービス停止に加えて volume も削除
down-volumes:
	docker compose down -v

# Docker Compose の全サービスログを追尾表示
logs:
	docker compose logs -f

# Docker Compose のコンテナ状態を一覧表示
ps:
	docker compose ps

# PostgreSQL コンテナだけを起動
postgres:
	docker compose up -d postgres

# Go 版 backend コンテナだけを起動
backend-go:
	docker compose up -d backend-go

# OpenAI Agents SDK sidecar コンテナだけを起動
agent-runtime:
	docker compose up -d agent-runtime

# frontend コンテナだけを起動
frontend:
	docker compose up -d frontend

# Go 版 backend の Docker イメージを再ビルドして起動
backend-go-build:
	docker compose up -d --build backend-go

# OpenAI Agents SDK sidecar の Docker イメージを再ビルドして起動
agent-runtime-build:
	docker compose up -d --build agent-runtime

# Go 版 backend の OpenAPI 生成コードを再作成
backend-go-codegen:
	cd backend-go && go generate ./internal/api

# Go 版 backend の標準 retrieval seed を投入し、keyword retrieval 評価を実行
backend-go-retrieval-eval:
	cd backend-go && go run ./cmd/retrieval-evaluation

# Go-only 構成の主要 API を smoke test する
smoke-go:
	bash scripts/go-only-smoke.sh

# frontend の Docker イメージを再ビルドして起動
frontend-build:
	docker compose up -d --build frontend

# Go backend test と frontend / agent-runtime build をまとめて実行
test: test-go build-agent-runtime build-frontend

# Go backend のテストを実行
test-go:
	cd backend-go && go test ./...

# frontend の build を実行
build: build-frontend

# agent-runtime の build を実行
build-agent-runtime:
	cd agent-runtime && npm run build

# frontend の本番 build を実行
build-frontend:
	cd frontend && npm run build

# Go backend をローカルプロセスとして起動
backend-go-local: check-openai
	set -a && source .env && set +a && cd backend-go && go run ./cmd/server

# OpenAI Agents SDK sidecar をローカルプロセスとして起動
agent-runtime-local: check-openai
	set -a && source .env && set +a && cd agent-runtime && npm run dev

# frontend をローカル開発サーバーで起動
frontend-local:
	cd frontend && npm run dev

# admin ユーザーの JWT を発行
auth-admin:
	curl -X POST http://localhost:8081/v1/auth/token \
		-H 'Content-Type: application/json' \
		-d '{"username":"admin","password":"change-me"}'

# operator ユーザーの JWT を発行
auth-operator:
	curl -X POST http://localhost:8081/v1/auth/token \
		-H 'Content-Type: application/json' \
		-d '{"username":"operator","password":"change-operator"}'
