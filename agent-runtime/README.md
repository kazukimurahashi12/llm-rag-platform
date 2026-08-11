# agent-runtime

OpenAI Agents SDK を使う sidecar です。

役割:
- Go backend の `POST /v1/agent/tasks` から task を受け取る
- Agents SDK の Agent / Runner で tool selection を実行する
- backend-go の既存 API を function tools として呼び出す
- finalAnswer / toolCalls / traceId を返す

MVP の tool:
- `knowledge.search`
- `dashboard.summary`
- `retrieval.evaluate`
- `groundedness.evaluate`
- `promptInjection.evaluate`
- `advice.generate`

書き込み tool は未登録です。

起動:

```bash
npm install
npm run dev
```

既定ポート:
- `8091`

環境変数:
- `AGENT_RUNTIME_PORT`
- `BACKEND_GO_BASE_URL`
- `OPENAI_API_KEY`
- `AGENT_DEFAULT_MODEL`
- `AGENT_REQUEST_TIMEOUT_MS`
- `AGENT_MAX_TURNS`
