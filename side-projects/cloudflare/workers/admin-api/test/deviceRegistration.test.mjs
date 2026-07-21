import assert from "node:assert/strict";
import { test } from "node:test";

import {
  DeviceRegistrationDependencyUnavailableError,
  handleDeviceRegistration,
} from "../.test-dist/deviceRegistration.js";

const APP_ID = "1:462747480927:android:bca425acab83705d11bcea";
const VALID_INSTALLATION_ID = "installation-12345678";
const VALID_FCM_TOKEN = "a".repeat(100);
const NOW = 1_800_000_000_000;

function validPayload(overrides = {}) {
  return {
    installationId: VALID_INSTALLATION_ID,
    fcmToken: VALID_FCM_TOKEN,
    packageName: "com.parsfilo.yasinsuresi",
    locale: "tr-TR",
    timezone: "Europe/Istanbul",
    notificationsEnabled: true,
    appVersion: "2.4.1",
    deviceModel: "Android Device",
    reason: "token_refresh",
    syncedAtEpochMs: NOW - 1000,
    tokenHash: "b".repeat(64),
    hasToken: true,
    lastAttemptAtEpochMs: NOW - 2000,
    lastSuccessAtEpochMs: NOW - 1000,
    lastFailureReason: null,
    adRuntimeWindowStartAtEpochMs: NOW - 10_000,
    adRuntimeLastUpdatedAtEpochMs: NOW - 500,
    adRuntimeFunnelCounts: { banner: { requested: 4, shown: 2, ignored: 0 } },
    adRuntimeSuppressReasonCounts: { consent_missing: 3, ignored: 0 },
    ...overrides,
  };
}

function jsonRequest(body, { method = "POST", appCheckToken = "valid-app-check", contentType = "application/json" } = {}) {
  const headers = new Headers();
  if (contentType) headers.set("content-type", contentType);
  if (appCheckToken) headers.set("x-firebase-appcheck", appCheckToken);
  return new Request("https://example.test/registerDevice", {
    method,
    headers,
    body: method === "GET" ? undefined : JSON.stringify(body),
  });
}

function createDependencies(overrides = {}) {
  const writes = [];
  return {
    writes,
    dependencies: {
      requireAppCheck: true,
      nowEpochMs: () => NOW,
      verifyAppCheck: async (token) => {
        assert.equal(token, "valid-app-check");
        return { appId: APP_ID };
      },
      upsertDevice: async (installationId, record) => {
        writes.push({ installationId, record });
        return true;
      },
      ...overrides,
    },
  };
}

async function responseJson(response) {
  return JSON.parse(await response.text());
}

test("rejects unsupported methods and non-JSON requests", async () => {
  const { dependencies } = createDependencies();

  const methodResponse = await handleDeviceRegistration(
    jsonRequest(null, { method: "GET" }),
    dependencies,
  );
  const typeResponse = await handleDeviceRegistration(
    jsonRequest(validPayload(), { contentType: "text/plain" }),
    dependencies,
  );

  assert.equal(methodResponse.status, 405);
  assert.equal(typeResponse.status, 415);
});

test("requires and verifies Firebase App Check by default", async () => {
  const missing = createDependencies();
  const missingResponse = await handleDeviceRegistration(
    jsonRequest(validPayload(), { appCheckToken: "" }),
    missing.dependencies,
  );

  const invalid = createDependencies({
    verifyAppCheck: async () => {
      throw new Error("invalid signature");
    },
  });
  const invalidResponse = await handleDeviceRegistration(
    jsonRequest(validPayload()),
    invalid.dependencies,
  );

  const unavailable = createDependencies({
    verifyAppCheck: async () => {
      throw new DeviceRegistrationDependencyUnavailableError();
    },
  });
  const unavailableResponse = await handleDeviceRegistration(
    jsonRequest(validPayload()),
    unavailable.dependencies,
  );

  assert.equal(missingResponse.status, 401);
  assert.equal(invalidResponse.status, 401);
  assert.equal(unavailableResponse.status, 503);
  assert.equal(missing.writes.length, 0);
  assert.equal(invalid.writes.length, 0);
  assert.equal(unavailable.writes.length, 0);
});

test("rejects malformed required identifiers", async () => {
  const cases = [
    { installationId: "short" },
    { fcmToken: "short" },
    { packageName: "not a package" },
  ];

  for (const overrides of cases) {
    const { dependencies, writes } = createDependencies();
    const response = await handleDeviceRegistration(
      jsonRequest(validPayload(overrides)),
      dependencies,
    );
    assert.equal(response.status, 400, JSON.stringify(overrides));
    assert.equal(writes.length, 0);
  }
});

test("sanitizes and upserts the complete approved device record", async () => {
  const { dependencies, writes } = createDependencies();
  const response = await handleDeviceRegistration(
    jsonRequest(validPayload({
      appVersion: "v".repeat(200),
      deviceModel: "d".repeat(200),
      reason: "r".repeat(200),
      lastFailureReason: "f".repeat(200),
      adRuntimeFunnelCounts: {
        banner: { requested: 4.9, shown: 2, ignored: 0, negative: -3 },
        "bad key!": { shown: 1 },
      },
      adRuntimeSuppressReasonCounts: { consent_missing: 3.8, ignored: 0, negative: -2 },
    })),
    dependencies,
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), { success: true });
  assert.equal(writes.length, 1);
  assert.equal(writes[0].installationId, VALID_INSTALLATION_ID);
  assert.deepEqual(writes[0].record, {
    fcmToken: VALID_FCM_TOKEN,
    timezone: "Europe/Istanbul",
    locale: "tr-TR",
    packageName: "com.parsfilo.yasinsuresi",
    notificationsEnabled: true,
    appVersion: "v".repeat(160),
    deviceModel: "d".repeat(160),
    reason: "r".repeat(160),
    syncedAtEpochMs: NOW - 1000,
    tokenHash: "b".repeat(64),
    hasToken: true,
    lastRegistrationAttemptAt: NOW - 2000,
    lastRegistrationSuccessAt: NOW - 1000,
    lastRegistrationFailureReason: "f".repeat(160),
    adRuntimeWindowStartAt: NOW - 10_000,
    adRuntimeLastUpdatedAt: NOW - 500,
    adRuntimeFunnelCounts: { banner: { requested: 4, shown: 2 } },
    adRuntimeSuppressReasonCounts: { consent_missing: 3 },
    appCheckAppId: APP_ID,
    updatedAt: new Date(NOW).toISOString(),
  });
});

test("returns service unavailable when the server write fails", async () => {
  const { dependencies, writes } = createDependencies({
    upsertDevice: async (installationId, record) => {
      writes.push({ installationId, record });
      return false;
    },
  });

  const response = await handleDeviceRegistration(jsonRequest(validPayload()), dependencies);

  assert.equal(response.status, 503);
  assert.equal(writes.length, 1);
});
