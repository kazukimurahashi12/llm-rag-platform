export type Config = {
  port: number;
  backendBaseUrl: string;
  defaultModel: string;
  requestTimeoutMs: number;
  maxTurns: number;
};

export function loadConfig(): Config {
  return {
    port: readInt("AGENT_RUNTIME_PORT", 8091),
    backendBaseUrl: readString("BACKEND_GO_BASE_URL", "http://localhost:8081"),
    defaultModel: readString("AGENT_DEFAULT_MODEL", "gpt-4o-mini"),
    requestTimeoutMs: readInt("AGENT_REQUEST_TIMEOUT_MS", 30000),
    maxTurns: readInt("AGENT_MAX_TURNS", 5),
  };
}

function readString(key: string, fallback: string): string {
  const value = process.env[key];
  return value && value.trim() !== "" ? value : fallback;
}

function readInt(key: string, fallback: number): number {
  const value = process.env[key];
  if (!value) {
    return fallback;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}
