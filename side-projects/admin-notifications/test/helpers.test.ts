import { describe, expect, it, vi } from "vitest";
import {
  fetchAdminFunctionJson,
  normalizePackages,
  parseTargetTimezonesInput,
  parseTestPushDataInput,
  summarizeApiError,
} from "../src/helpers";

describe("admin API contracts", () => {
  it("normalizes package and timezone inputs deterministically", () => {
    expect(normalizePackages(["com.b", "com.a", "com.b"])).toEqual(["com.a", "com.b"]);
    expect(normalizePackages(["com.a", "*"])).toEqual(["*"]);
    expect(parseTargetTimezonesInput("Europe/Istanbul, UTC, Europe/Istanbul")).toEqual([
      "Europe/Istanbul",
      "UTC",
    ]);
  });

  it("parses data-only push key-value input", () => {
    expect(parseTestPushDataInput("screen=home\nsource=admin")).toEqual({
      data: { screen: "home", source: "admin" },
      error: null,
    });
    expect(parseTestPushDataInput("broken-line").error).toContain("key=value");
  });

  it("preserves backend error contracts and bearer auth", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: "Denied by contract" }), {
        status: 403,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(fetchAdminFunctionJson({ endpoint: "https://example.test/admin", idToken: "token" }))
      .rejects.toThrow("Denied by contract");
    expect(fetchMock).toHaveBeenCalledWith(
      "https://example.test/admin",
      expect.objectContaining({
        method: "GET",
        headers: { Authorization: "Bearer token" },
      }),
    );
    expect(summarizeApiError({ error: "Exact" }, "Fallback")).toBe("Exact");
    vi.unstubAllGlobals();
  });
});
