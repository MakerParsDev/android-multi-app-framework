import { describe, expect, it, vi } from "vitest";
import {
  fetchAdminFunctionJson,
  formatDeviceCount,
  isServiceGitDrift,
  normalizePackages,
  parseEvent,
  parseTargetTimezonesInput,
  parseTestPushDataInput,
  summarizeApiError,
} from "../src/helpers";

describe("admin API contracts", () => {
  it("does not render missing coverage as a real zero", () => {
    expect(formatDeviceCount(undefined)).toBe("—");
    expect(formatDeviceCount(NaN)).toBe("—");
    expect(formatDeviceCount(-1)).toBe("—");
    expect(formatDeviceCount(0)).toBe("0");
    expect(formatDeviceCount(125)).toBe("125");
  });

  it("only compares the health SHA to the explicitly configured service SHA", () => {
    const pagesSha = "b95c72006bde0e0c781f0b8c38ec74b62f950ebd";
    const workerSha = "72eab1b3c74c002254c2904c92ce45926cb4e403";
    expect(isServiceGitDrift(workerSha)).toBe(false);
    expect(isServiceGitDrift(workerSha, "")).toBe(false);
    expect(isServiceGitDrift(workerSha, workerSha)).toBe(false);
    expect(isServiceGitDrift(workerSha, pagesSha)).toBe(true);
    expect(isServiceGitDrift(null, pagesSha)).toBe(false);
  });

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

  it("preserves backend error contracts and sends credentials for Cloudflare Access", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: "Denied by contract" }), {
        status: 403,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(fetchAdminFunctionJson({ endpoint: "https://example.test/admin" }))
      .rejects.toThrow("Denied by contract");
    expect(fetchMock).toHaveBeenCalledWith(
      "https://example.test/admin",
      expect.objectContaining({
        method: "GET",
        credentials: "include",
      }),
    );
    expect(summarizeApiError({ error: "Exact" }, "Fallback")).toBe("Exact");
    vi.unstubAllGlobals();
  });

  it("parses ISO-8601 timestamp strings returned by the admin-api Worker (Firestore REST, not the SDK Timestamp type)", () => {
    const record = parseEvent("event-1", {
      createdAt: "2026-08-12T10:00:00.000Z",
      updatedAt: "not-a-date",
    });
    expect(record.createdAt).toBeInstanceOf(Date);
    expect(record.createdAt?.getTime()).toBe(Date.parse("2026-08-12T10:00:00.000Z"));
    expect(record.updatedAt).toBeNull();
  });
});
