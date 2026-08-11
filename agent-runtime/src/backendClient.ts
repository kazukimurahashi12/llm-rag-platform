import type { Config } from "./config.js";

export type AgentToolCall = {
  toolName: string;
  status: "COMPLETED" | "FAILED";
  latencyMs: number;
  inputSummary?: string;
  outputSummary?: string;
  errorMessage?: string;
};

export type ToolContext = {
  authorization: string;
  toolCalls: AgentToolCall[];
  config: Config;
};

export async function callBackendTool(
  context: ToolContext,
  toolName: string,
  request: {
    method: "GET" | "POST";
    path: string;
    body?: unknown;
  },
): Promise<unknown> {
  const startedAt = Date.now();
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), context.config.requestTimeoutMs);
  const headers: Record<string, string> = {
    authorization: context.authorization,
    accept: "application/json",
  };
  if (request.body !== undefined) {
    headers["content-type"] = "application/json";
  }

  try {
    const response = await fetch(new URL(request.path, context.config.backendBaseUrl), {
      method: request.method,
      headers,
      body: request.body === undefined ? undefined : JSON.stringify(request.body),
      signal: controller.signal,
    });
    const text = await response.text();
    const payload = parseJSON(text);
    if (!response.ok) {
      throw new Error(`backend ${response.status}: ${summarize(payload ?? text)}`);
    }

    context.toolCalls.push({
      toolName,
      status: "COMPLETED",
      latencyMs: Date.now() - startedAt,
      inputSummary: `${request.method} ${request.path}`,
      outputSummary: summarize(payload),
    });
    return payload;
  } catch (error) {
    context.toolCalls.push({
      toolName,
      status: "FAILED",
      latencyMs: Date.now() - startedAt,
      inputSummary: `${request.method} ${request.path}`,
      errorMessage: error instanceof Error ? error.message : String(error),
    });
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export function summarize(value: unknown): string {
  const text = typeof value === "string" ? value : JSON.stringify(value);
  if (!text) {
    return "";
  }
  return text.length > 500 ? `${text.slice(0, 500)}...` : text;
}

function parseJSON(text: string): unknown {
  if (text.trim() === "") {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
