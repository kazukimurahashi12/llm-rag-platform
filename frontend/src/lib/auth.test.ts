import { afterEach, describe, expect, it } from "vitest";
import { getStoredAuthSession, saveAuthSession } from "./auth";

describe("auth session", () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it("saves and loads a valid session", () => {
    saveAuthSession({
      username: "admin",
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      roles: ["ADMIN"],
    });

    expect(getStoredAuthSession()).toEqual({
      username: "admin",
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      roles: ["ADMIN"],
    });
  });

  it("returns null for expired session", () => {
    saveAuthSession({
      username: "admin",
      accessToken: "token",
      expiresAt: "2000-01-01T00:00:00Z",
      roles: ["ADMIN"],
    });

    expect(getStoredAuthSession()).toBeNull();
  });
});
