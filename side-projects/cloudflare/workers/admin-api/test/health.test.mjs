import assert from "node:assert/strict";
import { test } from "node:test";

import { handleHealthCheck } from "../.test-dist/health.js";

const metadata = {
  environment: "production",
  gitSha: "ef6bdf66499874269a26337f35c549a83cd19078",
  builtAt: "2026-07-20T16:21:49.994Z",
  workerVersion: {
    id: "fa9b0f15-d196-4cbb-988e-07b64d055df5",
    tag: "production",
    timestamp: "2026-07-20T16:21:49.994Z",
  },
};

async function responseJson(response) {
  return JSON.parse(await response.text());
}

test("GET health exposes traceable production metadata", async () => {
  const response = handleHealthCheck(new Request("https://example.test/health"), metadata);
  const body = await responseJson(response);

  assert.equal(response.status, 200);
  assert.equal(body.ok, true);
  assert.equal(body.service, "cloudflare-admin-api");
  assert.equal(body.environment, "production");
  assert.equal(body.gitSha, metadata.gitSha);
  assert.equal(body.builtAt, metadata.builtAt);
  assert.equal(body.workerVersionId, metadata.workerVersion.id);
  assert.equal(body.workerVersionTag, metadata.workerVersion.tag);
  assert.equal(body.workerVersionTimestamp, metadata.workerVersion.timestamp);
  assert.equal(body.region, "global");
  assert.match(body.timestamp, /^\d{4}-\d{2}-\d{2}T/);
});

test("POST health is supported and optional metadata is normalized", async () => {
  const response = handleHealthCheck(
    new Request("https://example.test/health", { method: "POST" }),
    { environment: "  production  ", gitSha: "  abc1234  ", builtAt: "  2026-07-21T00:00:00Z  " },
  );
  const body = await responseJson(response);

  assert.equal(response.status, 200);
  assert.equal(body.environment, "production");
  assert.equal(body.gitSha, "abc1234");
  assert.equal(body.builtAt, "2026-07-21T00:00:00Z");
  assert.equal(body.workerVersionId, null);
  assert.equal(body.workerVersionTag, null);
  assert.equal(body.workerVersionTimestamp, null);
});

test("health reports unknown metadata without inventing a version", async () => {
  const response = handleHealthCheck(new Request("https://example.test/health"), {});
  const body = await responseJson(response);

  assert.equal(body.environment, "unknown");
  assert.equal(body.gitSha, "unknown");
  assert.equal(body.builtAt, "unknown");
  assert.equal(body.workerVersionId, null);
});

test("health rejects unsupported methods", async () => {
  const response = handleHealthCheck(
    new Request("https://example.test/health", { method: "DELETE" }),
    metadata,
  );

  assert.equal(response.status, 405);
  assert.deepEqual(await responseJson(response), { error: "Method not allowed" });
});
