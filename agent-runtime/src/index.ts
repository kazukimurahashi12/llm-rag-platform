import { createServer, type IncomingMessage, type ServerResponse } from "node:http";

import { runAgentTask, type AgentTaskRequest } from "./agent.js";
import { loadConfig } from "./config.js";

const config = loadConfig();

const server = createServer(async (request, response) => {
  try {
    if (request.method === "GET" && request.url === "/health") {
      return writeJSON(response, 200, { status: "UP" });
    }

    if (request.method === "POST" && request.url === "/agent/tasks") {
      const body = await readJSON(request);
      const parsed = parseAgentTaskRequest(body);
      if (!parsed.ok) {
        return writeJSON(response, 400, { status: 400, message: parsed.error });
      }
      const result = await runAgentTask(config, parsed.value);
      return writeJSON(response, result.status === "COMPLETED" ? 200 : 502, result);
    }

    return writeJSON(response, 404, { status: 404, message: "not found" });
  } catch (error) {
    return writeJSON(response, 500, {
      status: 500,
      message: "agent runtime failed",
      details: [error instanceof Error ? error.message : String(error)],
    });
  }
});

server.listen(config.port, () => {
  console.log(`agent-runtime listening on :${config.port}`);
});

function parseAgentTaskRequest(value: unknown): { ok: true; value: AgentTaskRequest } | { ok: false; error: string } {
  if (!value || typeof value !== "object") {
    return { ok: false, error: "request body must be an object" };
  }
  const record = value as Record<string, unknown>;
  if (typeof record.input !== "string" || record.input.trim() === "") {
    return { ok: false, error: "input is required" };
  }
  if (typeof record.authorization !== "string" || !record.authorization.startsWith("Bearer ")) {
    return { ok: false, error: "authorization bearer token is required" };
  }
  if (record.maxTurns !== undefined && (typeof record.maxTurns !== "number" || record.maxTurns < 1)) {
    return { ok: false, error: "maxTurns must be a positive number" };
  }
  return {
    ok: true,
    value: {
      input: record.input,
      authorization: record.authorization,
      maxTurns: record.maxTurns,
    },
  };
}

async function readJSON(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  const text = Buffer.concat(chunks).toString("utf8");
  if (text.trim() === "") {
    return {};
  }
  return JSON.parse(text);
}

function writeJSON(response: ServerResponse, statusCode: number, value: unknown): void {
  response.writeHead(statusCode, { "content-type": "application/json" });
  response.end(JSON.stringify(value));
}
