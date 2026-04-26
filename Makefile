SHELL := /bin/bash

.PHONY: help bootstrap env frontend-env check-openai install-frontend \
	up up-build down down-volumes logs ps \
	postgres backend frontend backend-build frontend-build \
	test test-backend build build-frontend \
	backend-local frontend-local auth-admin auth-operator


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

# frontend の npm 依存関係をインストールする
install-frontend:
	cd frontend && npm install

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

# backend コンテナだけを起動
backend:
	docker compose up -d backend

# frontend コンテナだけを起動
frontend:
	docker compose up -d frontend

# backend の Docker イメージを再ビルドして起動
backend-build: check-openai
	docker compose up -d --build backend

# frontend の Docker イメージを再ビルドして起動
frontend-build:
	docker compose up -d --build frontend

# backend test と frontend build をまとめて実行
test: test-backend build-frontend

# backend のテストを実行
test-backend:
	./gradlew test

# frontend の build を実行
build: build-frontend

# frontend の本番 build を実行
build-frontend:
	cd frontend && npm run build

# backend をローカルプロセスとして起動
backend-local: check-openai
	set -a && source .env && set +a && RAG_VECTOR_SEARCH_ENABLED=$${RAG_VECTOR_SEARCH_ENABLED:-true} ./gradlew bootRun

# frontend をローカル開発サーバーで起動
frontend-local:
	cd frontend && npm run dev

# admin ユーザーの JWT を発行
auth-admin:
	curl -X POST http://localhost:8080/v1/auth/token \
		-H 'Content-Type: application/json' \
		-d '{"username":"admin","password":"change-me"}'

# operator ユーザーの JWT を発行
auth-operator:
	curl -X POST http://localhost:8080/v1/auth/token \
		-H 'Content-Type: application/json' \
		-d '{"username":"operator","password":"change-operator"}'
