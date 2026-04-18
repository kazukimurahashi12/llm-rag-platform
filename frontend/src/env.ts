export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
  environmentLabel: import.meta.env.VITE_APP_ENV ?? "local",
};
