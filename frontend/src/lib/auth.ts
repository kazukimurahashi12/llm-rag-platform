const AUTH_STORAGE_KEY = "onboard-core-auth";

export interface StoredCredentials {
  username: string;
  password: string;
}

export function getStoredCredentials(): StoredCredentials | null {
  const stored = window.localStorage.getItem(AUTH_STORAGE_KEY);
  if (!stored) {
    return null;
  }

  try {
    const parsed = JSON.parse(stored) as StoredCredentials;
    if (!parsed.username || !parsed.password) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveCredentials(credentials: StoredCredentials) {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(credentials));
}

export function clearCredentials() {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}

export function buildBasicAuthHeader(credentials: StoredCredentials): string {
  return `Basic ${window.btoa(`${credentials.username}:${credentials.password}`)}`;
}
