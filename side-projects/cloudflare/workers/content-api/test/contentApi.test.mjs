import test from "node:test";
import assert from "node:assert/strict";
import worker from "../.test-dist/index.js";
import { assertHealthPayload } from "../../../../contracts/health-contract.mjs";

const GIT_SHA = "1234567890abcdef1234567890abcdef12345678";
const env = {
  OTHER_APPS_JSON_URL: "https://example.test/apps.json",
  GOOGLE_RECAPTCHA_SECRET_KEY: "secret",
  SERVICE_ENVIRONMENT: "test",
  SERVICE_GIT_SHA: GIT_SHA,
  SERVICE_BUILD_TIMESTAMP: "2026-07-21T00:00:00.000Z",
  AUDIO_BUCKET: {},
};

function request(path, init = {}) {
  return new Request(`https://content.test${path}`, {
    headers: { "CF-Connecting-IP": `test-${path}-${Math.random()}` },
    ...init,
  });
}

test("health exposes traceable build metadata", async () => {
  const response = await worker.fetch(request("/health"), env);
  assert.equal(response.status, 200);
  assertHealthPayload(await response.json(), {
    service: "contentapp-content-api",
    gitSha: GIT_SHA,
  });
});

test("recaptcha contract rejects unsupported method and origin", async () => {
  const method = await worker.fetch(request("/api/recaptcha-verify"), env);
  assert.equal(method.status, 405);
  const origin = await worker.fetch(request("/api/recaptcha-verify", {
    method: "POST",
    headers: {
      "CF-Connecting-IP": "recaptcha-origin",
      "content-type": "application/json",
      origin: "https://attacker.example",
    },
    body: JSON.stringify({ token: "a".repeat(40) }),
  }), env);
  assert.equal(origin.status, 403);
});

test("audio traversal and unknown routes are rejected", async () => {
  assert.equal((await worker.fetch(request("/api/audio/..%2Fsecret"), env)).status, 400);
  assert.equal((await worker.fetch(request("/missing"), env)).status, 404);
});
