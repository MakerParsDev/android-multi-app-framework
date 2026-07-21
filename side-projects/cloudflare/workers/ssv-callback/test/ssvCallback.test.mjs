import test from "node:test";
import assert from "node:assert/strict";
import worker from "../.test-dist/index.js";
import { assertHealthPayload } from "../../../../contracts/health-contract.mjs";

const GIT_SHA = "abcdef1234567890abcdef1234567890abcdef12";
const env = {
  SERVICE_ENVIRONMENT: "test",
  SERVICE_GIT_SHA: GIT_SHA,
  SERVICE_BUILD_TIMESTAMP: "2026-07-21T00:00:00.000Z",
  SSV_DEDUP: {},
};
const ctx = { waitUntil() {}, passThroughOnException() {} };

test("health exposes traceable build metadata", async () => {
  const response = await worker.fetch(new Request("https://ssv.test/health"), env, ctx);
  assert.equal(response.status, 200);
  assertHealthPayload(await response.json(), {
    service: "contentapp-ssv-callback",
    gitSha: GIT_SHA,
  });
});

test("SSV route rejects unsupported method and incomplete callback", async () => {
  assert.equal((await worker.fetch(new Request("https://ssv.test/ssv", { method: "POST" }), env, ctx)).status, 405);
  assert.equal((await worker.fetch(new Request("https://ssv.test/ssv"), env, ctx)).status, 400);
  assert.equal((await worker.fetch(new Request("https://ssv.test/missing"), env, ctx)).status, 404);
});
