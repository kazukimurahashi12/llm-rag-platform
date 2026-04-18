const AUTH_STORAGE_KEY = "onboard-core-auth-session";
export const AUTH_CHANGED_EVENT = "onboard-core-auth-changed";

export interface StoredAuthSession {
  username: string;
  accessToken: string;
  expiresAt: string;
  roles: string[];
}

function isExpired(expiresAt: string): boolean {
  const timestamp = Date.parse(expiresAt);
  return Number.isNaN(timestamp) || timestamp <= Date.now();
}

function notifyAuthChanged() {
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export function getStoredAuthSession(): StoredAuthSession | null {
  const stored = window.localStorage.getItem(AUTH_STORAGE_KEY);
  if (!stored) {
    return null;
  }

  try {
    const parsed = JSON.parse(stored) as StoredAuthSession;
    if (!parsed.username || !parsed.accessToken || !parsed.expiresAt || !Array.isArray(parsed.roles)) {
      window.localStorage.removeItem(AUTH_STORAGE_KEY);
      return null;
    }
    if (isExpired(parsed.expiresAt)) {
      window.localStorage.removeItem(AUTH_STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function saveAuthSession(session: StoredAuthSession) {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
  notifyAuthChanged();
}

export function clearAuthSession() {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
  notifyAuthChanged();
}
