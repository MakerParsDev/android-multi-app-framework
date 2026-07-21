import assert from "node:assert/strict";

const GIT_SHA = /^[0-9a-f]{7,40}$/i;

export function assertHealthPayload(payload, expected) {
  assert.equal(typeof payload, "object");
  assert.notEqual(payload, null);
  assert.equal(payload.ok, true);
  assert.equal(payload.service, expected.service);
  assert.match(payload.gitSha, GIT_SHA);
  if (expected.gitSha) assert.equal(payload.gitSha, expected.gitSha);
  assert.equal(typeof payload.environment, "string");
  assert.notEqual(payload.environment.trim(), "");
  assert.notEqual(payload.environment, "unknown");
  assert.equal(typeof payload.builtAt, "string");
  assert.equal(Number.isNaN(Date.parse(payload.builtAt)), false);
}
