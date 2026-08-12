import assert from "node:assert/strict";
import { afterEach, test, mock } from "node:test";
import { webcrypto, generateKeyPairSync } from "node:crypto";

import {
  handleAdminListScheduledEvents,
  handleAdminSaveScheduledEvent,
  handleAdminDeleteScheduledEvent,
  handleAdminPreviewTargetDevices,
  handleAdminLookupDevice,
  handleAdminListRecentDevices,
} from "../.test-dist/index.js";

const TEAM_DOMAIN = "makerpars.cloudflareaccess.com";
const AUD = "8f6e1c2b9a7d4e3f0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a";
const CERTS_URL = `https://${TEAM_DOMAIN}/cdn-cgi/access/certs`;
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const PROJECT_ID = "test-project";
const ADMIN_EMAIL = "oaslananka@gmail.com";
const DOCUMENTS_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

function base64Url(input) {
  return Buffer.from(input)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function createSigner(kid = "key-1") {
  const pair = await webcrypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  );
  const jwk = await webcrypto.subtle.exportKey("jwk", pair.publicKey);
  return {
    kid,
    jwk: { kty: jwk.kty, n: jwk.n, e: jwk.e, kid },
    async sign(payloadOverrides = {}) {
      const nowSeconds = Math.floor(Date.now() / 1000);
      const header = { alg: "RS256", typ: "JWT", kid };
      const payload = {
        iss: `https://${TEAM_DOMAIN}`,
        aud: [AUD],
        email: ADMIN_EMAIL,
        exp: nowSeconds + 3600,
        iat: nowSeconds - 60,
        ...payloadOverrides,
      };
      const encodedHeader = base64Url(JSON.stringify(header));
      const encodedPayload = base64Url(JSON.stringify(payload));
      const signingInput = `${encodedHeader}.${encodedPayload}`;
      const signature = await webcrypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        pair.privateKey,
        new TextEncoder().encode(signingInput),
      );
      return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
    },
  };
}

// RSA keygen is expensive; the service account key content is never actually
// validated (the mocked token endpoint accepts the signed assertion without
// inspecting it), so a single key pair is reused across every test below.
const SERVICE_ACCOUNT_JSON = JSON.stringify({
  client_email: "test-sa@example.iam.gserviceaccount.com",
  private_key: generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  }).privateKey,
  token_uri: TOKEN_URL,
});

function baseEnv(overrides = {}) {
  return {
    ADMIN_ACCESS_TEAM_DOMAIN: TEAM_DOMAIN,
    ADMIN_ACCESS_AUD: AUD,
    FIREBASE_PROJECT_ID: PROJECT_ID,
    FIREBASE_SERVICE_ACCOUNT_JSON: SERVICE_ACCOUNT_JSON,
    ...overrides,
  };
}

function jsonRequest(url, { token, body } = {}) {
  const headers = new Headers({ "content-type": "application/json" });
  if (token !== undefined) headers.set("Cf-Access-Jwt-Assertion", token);
  return new Request(url, {
    method: "POST",
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function responseJson(response) {
  return JSON.parse(await response.text());
}

/**
 * Mocks the global fetch used transitively by verifyAccessRequest,
 * getGoogleAccessToken, and the Firestore REST helpers, routing by URL/method
 * instead of hitting real Cloudflare Access / Google Cloud endpoints.
 */
function mockFetch({ certsKeys = [], firestore = {} } = {}) {
  const calls = [];
  mock.method(globalThis, "fetch", async (url, init = {}) => {
    const method = init.method ?? "GET";
    const urlStr = String(url);
    calls.push({ url: urlStr, method, body: init.body });

    if (urlStr === CERTS_URL) {
      return new Response(JSON.stringify({ keys: certsKeys }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    }
    if (urlStr === TOKEN_URL) {
      return new Response(JSON.stringify({ access_token: "fake-access-token", expires_in: 3600 }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    }
    if (urlStr === `${DOCUMENTS_BASE}:runQuery`) {
      return firestore.runQuery
        ? firestore.runQuery(urlStr, init)
        : new Response(JSON.stringify([]), { status: 200 });
    }
    if (urlStr.startsWith(`${DOCUMENTS_BASE}/scheduled_events?`) && method === "GET") {
      return firestore.list
        ? firestore.list(urlStr, init)
        : new Response(JSON.stringify({ documents: [] }), { status: 200 });
    }
    if (urlStr.startsWith(`${DOCUMENTS_BASE}/scheduled_events/`) && method === "PATCH") {
      return firestore.patch
        ? firestore.patch(urlStr, init)
        : new Response(JSON.stringify({}), { status: 200 });
    }
    if (urlStr.startsWith(`${DOCUMENTS_BASE}/scheduled_events/`) && method === "DELETE") {
      return firestore.del
        ? firestore.del(urlStr, init)
        : new Response(JSON.stringify({}), { status: 200 });
    }
    if (urlStr.startsWith(`${DOCUMENTS_BASE}/devices/`) && method === "GET") {
      return firestore.getDevice
        ? firestore.getDevice(urlStr, init)
        : new Response(JSON.stringify({}), { status: 404 });
    }
    throw new Error(`Unexpected fetch in test: ${method} ${urlStr}`);
  });
  return { calls };
}

afterEach(() => {
  mock.reset();
});

// ---------------------------------------------------------------------------
// handleAdminListScheduledEvents
// ---------------------------------------------------------------------------

test("adminListScheduledEvents: 401 when Cf-Access-Jwt-Assertion header is missing", async () => {
  mockFetch();
  const response = await handleAdminListScheduledEvents(
    jsonRequest("https://admin-api.parsfilo.com/adminListScheduledEvents"),
    baseEnv(),
  );
  assert.equal(response.status, 401);
  assert.deepEqual(await responseJson(response), { error: "Missing Cloudflare Access token" });
});

test("adminListScheduledEvents: 401 when the Access token fails verification", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({ certsKeys: [] }); // no matching kid -> verifyAccessRequest throws

  const response = await handleAdminListScheduledEvents(
    jsonRequest("https://admin-api.parsfilo.com/adminListScheduledEvents", { token }),
    baseEnv(),
  );
  assert.equal(response.status, 401);
  assert.deepEqual(await responseJson(response), { error: "Invalid Cloudflare Access token" });
});

test("adminListScheduledEvents: returns events mapped from Firestore documents", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      list: () =>
        new Response(
          JSON.stringify({
            documents: [
              {
                name: `projects/${PROJECT_ID}/databases/(default)/documents/scheduled_events/event-1`,
                fields: {
                  status: { stringValue: "scheduled" },
                  localDeliveryTime: { stringValue: "09:00" },
                  targetTimezones: { arrayValue: { values: [{ stringValue: "Europe/Istanbul" }] } },
                },
              },
            ],
          }),
          { status: 200 },
        ),
    },
  });

  const response = await handleAdminListScheduledEvents(
    jsonRequest("https://admin-api.parsfilo.com/adminListScheduledEvents", { token }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), {
    events: [
      {
        id: "event-1",
        status: "scheduled",
        localDeliveryTime: "09:00",
        targetTimezones: ["Europe/Istanbul"],
      },
    ],
  });
});

// ---------------------------------------------------------------------------
// handleAdminSaveScheduledEvent
// ---------------------------------------------------------------------------

test("adminSaveScheduledEvent: 401 when Cf-Access-Jwt-Assertion header is missing", async () => {
  mockFetch();
  const response = await handleAdminSaveScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminSaveScheduledEvent", { body: { id: "event-1" } }),
    baseEnv(),
  );
  assert.equal(response.status, 401);
  assert.deepEqual(await responseJson(response), { error: "Missing Cloudflare Access token" });
});

test("adminSaveScheduledEvent: 400 when id is missing", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({ certsKeys: [signer.jwk] });

  const response = await handleAdminSaveScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminSaveScheduledEvent", {
      token,
      body: { status: "scheduled" },
    }),
    baseEnv(),
  );
  assert.equal(response.status, 400);
  assert.deepEqual(await responseJson(response), { error: "id is required" });
});

test("adminSaveScheduledEvent: create stamps createdBy/updatedBy from the verified admin, never the client", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  let patchedBody = null;
  let patchedUrl = null;
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      patch: (url, init) => {
        patchedUrl = url;
        patchedBody = JSON.parse(init.body);
        return new Response(JSON.stringify({}), { status: 200 });
      },
    },
  });

  const response = await handleAdminSaveScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminSaveScheduledEvent", {
      token,
      body: {
        id: "event-1",
        isCreate: true,
        status: "scheduled",
        createdBy: "attacker@example.com",
        updatedBy: "attacker@example.com",
      },
    }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), { id: "event-1" });
  assert.ok(patchedUrl.startsWith(`${DOCUMENTS_BASE}/scheduled_events/event-1`));
  assert.equal(patchedBody.fields.createdBy.stringValue, ADMIN_EMAIL);
  assert.equal(patchedBody.fields.updatedBy.stringValue, ADMIN_EMAIL);
  assert.equal(patchedBody.fields.status.stringValue, "scheduled");
  assert.deepEqual(patchedBody.fields.sentTimezones.arrayValue.values, []);
  assert.deepEqual(patchedBody.fields.lastResetAt, { nullValue: null });
  assert.deepEqual(patchedBody.fields.lastDispatchedAt, { nullValue: null });
  assert.ok(typeof patchedBody.fields.createdAt.timestampValue === "string" || typeof patchedBody.fields.createdAt.stringValue === "string");
});

test("adminSaveScheduledEvent: update stamps only updatedBy and omits create-only fields", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  let patchedBody = null;
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      patch: (_url, init) => {
        patchedBody = JSON.parse(init.body);
        return new Response(JSON.stringify({}), { status: 200 });
      },
    },
  });

  const response = await handleAdminSaveScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminSaveScheduledEvent", {
      token,
      body: { id: "event-1", status: "paused", updatedBy: "attacker@example.com" },
    }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.equal(patchedBody.fields.updatedBy.stringValue, ADMIN_EMAIL);
  assert.equal(patchedBody.fields.createdBy, undefined);
  assert.equal(patchedBody.fields.createdAt, undefined);
  assert.equal(patchedBody.fields.sentTimezones, undefined);
});

test("adminSaveScheduledEvent: returns 500 when the Firestore write fails", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      patch: () => new Response("boom", { status: 500 }),
    },
  });

  const response = await handleAdminSaveScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminSaveScheduledEvent", {
      token,
      body: { id: "event-1", status: "scheduled" },
    }),
    baseEnv(),
  );

  assert.equal(response.status, 500);
  assert.deepEqual(await responseJson(response), { error: "Failed to save event" });
});

// ---------------------------------------------------------------------------
// handleAdminDeleteScheduledEvent
// ---------------------------------------------------------------------------

test("adminDeleteScheduledEvent: 401 when Cf-Access-Jwt-Assertion header is missing", async () => {
  mockFetch();
  const response = await handleAdminDeleteScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminDeleteScheduledEvent", { body: { id: "event-1" } }),
    baseEnv(),
  );
  assert.equal(response.status, 401);
});

test("adminDeleteScheduledEvent: 400 when id is missing", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({ certsKeys: [signer.jwk] });

  const response = await handleAdminDeleteScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminDeleteScheduledEvent", { token, body: {} }),
    baseEnv(),
  );
  assert.equal(response.status, 400);
  assert.deepEqual(await responseJson(response), { error: "id is required" });
});

test("adminDeleteScheduledEvent: deletes the document and returns ok", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  let deletedUrl = null;
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      del: (url) => {
        deletedUrl = url;
        return new Response(null, { status: 200 });
      },
    },
  });

  const response = await handleAdminDeleteScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminDeleteScheduledEvent", {
      token,
      body: { id: "event-1" },
    }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), { ok: true });
  assert.equal(deletedUrl, `${DOCUMENTS_BASE}/scheduled_events/event-1`);
});

test("adminDeleteScheduledEvent: returns 500 when the Firestore delete fails", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      del: () => new Response("boom", { status: 500 }),
    },
  });

  const response = await handleAdminDeleteScheduledEvent(
    jsonRequest("https://admin-api.parsfilo.com/adminDeleteScheduledEvent", {
      token,
      body: { id: "event-1" },
    }),
    baseEnv(),
  );

  assert.equal(response.status, 500);
  assert.deepEqual(await responseJson(response), { error: "Failed to delete event" });
});

// ---------------------------------------------------------------------------
// handleAdminPreviewTargetDevices
// ---------------------------------------------------------------------------

test("adminPreviewTargetDevices: 401 when Cf-Access-Jwt-Assertion header is missing", async () => {
  mockFetch();
  const response = await handleAdminPreviewTargetDevices(
    jsonRequest("https://admin-api.parsfilo.com/adminPreviewTargetDevices", { body: { packages: [] } }),
    baseEnv(),
  );
  assert.equal(response.status, 401);
});

test("adminPreviewTargetDevices: counts only devices with notificationsEnabled, per package, via queryDevicesByPackage", async () => {
  const signer = await createSigner();
  const token = await signer.sign();

  function devicesDoc(id, packageName, notificationsEnabled) {
    return {
      document: {
        name: `projects/${PROJECT_ID}/databases/(default)/documents/devices/${id}`,
        fields: {
          packageName: { stringValue: packageName },
          notificationsEnabled: { booleanValue: notificationsEnabled },
          fcmToken: { stringValue: "token" },
          locale: { stringValue: "tr-TR" },
          updatedAt: { timestampValue: new Date().toISOString() },
        },
      },
    };
  }

  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      runQuery: (_url, init) => {
        const { structuredQuery } = JSON.parse(init.body);
        const packageName = structuredQuery.where.fieldFilter.value.stringValue;
        if (packageName === "com.parsfilo.yasinsuresi") {
          return new Response(
            JSON.stringify([
              devicesDoc("d1", packageName, true),
              devicesDoc("d2", packageName, true),
              devicesDoc("d3", packageName, false),
            ]),
            { status: 200 },
          );
        }
        if (packageName === "com.parsfilo.kible") {
          return new Response(JSON.stringify([devicesDoc("d4", packageName, true)]), { status: 200 });
        }
        return new Response(JSON.stringify([]), { status: 200 });
      },
    },
  });

  const response = await handleAdminPreviewTargetDevices(
    jsonRequest("https://admin-api.parsfilo.com/adminPreviewTargetDevices", {
      token,
      body: { packages: ["com.parsfilo.yasinsuresi", "com.parsfilo.kible"] },
    }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), {
    total: 3,
    byPackage: {
      "com.parsfilo.yasinsuresi": 2,
      "com.parsfilo.kible": 1,
    },
  });
});

// ---------------------------------------------------------------------------
// handleAdminLookupDevice
// ---------------------------------------------------------------------------

test("adminLookupDevice: 401 when Cf-Access-Jwt-Assertion header is missing", async () => {
  mockFetch();
  const response = await handleAdminLookupDevice(
    jsonRequest("https://admin-api.parsfilo.com/adminLookupDevice", { body: { id: "device-1" } }),
    baseEnv(),
  );
  assert.equal(response.status, 401);
  assert.deepEqual(await responseJson(response), { error: "Missing Cloudflare Access token" });
});

test("adminLookupDevice: 401 when the Access token fails verification", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({ certsKeys: [] }); // no matching kid -> verifyAccessRequest throws

  const response = await handleAdminLookupDevice(
    jsonRequest("https://admin-api.parsfilo.com/adminLookupDevice", { token, body: { id: "device-1" } }),
    baseEnv(),
  );
  assert.equal(response.status, 401);
  assert.deepEqual(await responseJson(response), { error: "Invalid Cloudflare Access token" });
});

test("adminLookupDevice: 400 when id is missing", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({ certsKeys: [signer.jwk] });

  const response = await handleAdminLookupDevice(
    jsonRequest("https://admin-api.parsfilo.com/adminLookupDevice", { token, body: {} }),
    baseEnv(),
  );
  assert.equal(response.status, 400);
  assert.deepEqual(await responseJson(response), { error: "id is required" });
});

test("adminLookupDevice: returns { device: null } when the document does not exist", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  let requestedUrl = null;
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      getDevice: (url) => {
        requestedUrl = url;
        return new Response(JSON.stringify({ error: { code: 404, message: "not found" } }), { status: 404 });
      },
    },
  });

  const response = await handleAdminLookupDevice(
    jsonRequest("https://admin-api.parsfilo.com/adminLookupDevice", { token, body: { id: "missing-device" } }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), { device: null });
  assert.equal(requestedUrl, `${DOCUMENTS_BASE}/devices/missing-device`);
});

test("adminLookupDevice: returns the parsed device for a found document", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      getDevice: () =>
        new Response(
          JSON.stringify({
            name: `projects/${PROJECT_ID}/databases/(default)/documents/devices/device-1`,
            fields: {
              packageName: { stringValue: "com.parsfilo.yasinsuresi" },
              fcmToken: { stringValue: "token-abc" },
              notificationsEnabled: { booleanValue: true },
              locale: { stringValue: "tr-TR" },
              updatedAt: { timestampValue: "2026-08-01T00:00:00.000Z" },
            },
          }),
          { status: 200 },
        ),
    },
  });

  const response = await handleAdminLookupDevice(
    jsonRequest("https://admin-api.parsfilo.com/adminLookupDevice", { token, body: { id: "device-1" } }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), {
    device: {
      id: "device-1",
      packageName: "com.parsfilo.yasinsuresi",
      fcmToken: "token-abc",
      notificationsEnabled: true,
      locale: "tr-TR",
      updatedAt: "2026-08-01T00:00:00.000Z",
    },
  });
});

test("adminLookupDevice: URL-encodes the id when building the Firestore document path", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  let requestedUrl = null;
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      getDevice: (url) => {
        requestedUrl = url;
        return new Response(JSON.stringify({ error: { code: 404 } }), { status: 404 });
      },
    },
  });

  await handleAdminLookupDevice(
    jsonRequest("https://admin-api.parsfilo.com/adminLookupDevice", { token, body: { id: "device/with/slash" } }),
    baseEnv(),
  );

  assert.equal(requestedUrl, `${DOCUMENTS_BASE}/devices/device%2Fwith%2Fslash`);
});

// ---------------------------------------------------------------------------
// handleAdminListRecentDevices
// ---------------------------------------------------------------------------

test("adminListRecentDevices: 401 when Cf-Access-Jwt-Assertion header is missing", async () => {
  mockFetch();
  const response = await handleAdminListRecentDevices(
    jsonRequest("https://admin-api.parsfilo.com/adminListRecentDevices"),
    baseEnv(),
  );
  assert.equal(response.status, 401);
  assert.deepEqual(await responseJson(response), { error: "Missing Cloudflare Access token" });
});

test("adminListRecentDevices: 401 when the Access token fails verification", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({ certsKeys: [] }); // no matching kid -> verifyAccessRequest throws

  const response = await handleAdminListRecentDevices(
    jsonRequest("https://admin-api.parsfilo.com/adminListRecentDevices", { token }),
    baseEnv(),
  );
  assert.equal(response.status, 401);
  assert.deepEqual(await responseJson(response), { error: "Invalid Cloudflare Access token" });
});

test("adminListRecentDevices: sends a runQuery request ordered by updatedAt desc with limit 50, and returns the parsed array", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  let sentStructuredQuery = null;
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      runQuery: (_url, init) => {
        sentStructuredQuery = JSON.parse(init.body).structuredQuery;
        return new Response(
          JSON.stringify([
            {
              document: {
                name: `projects/${PROJECT_ID}/databases/(default)/documents/devices/device-1`,
                fields: {
                  packageName: { stringValue: "com.parsfilo.yasinsuresi" },
                  updatedAt: { timestampValue: "2026-08-10T00:00:00.000Z" },
                },
              },
            },
            {
              document: {
                name: `projects/${PROJECT_ID}/databases/(default)/documents/devices/device-2`,
                fields: {
                  packageName: { stringValue: "com.parsfilo.kible" },
                  updatedAt: { timestampValue: "2026-08-09T00:00:00.000Z" },
                },
              },
            },
          ]),
          { status: 200 },
        );
      },
    },
  });

  const response = await handleAdminListRecentDevices(
    jsonRequest("https://admin-api.parsfilo.com/adminListRecentDevices", { token }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(sentStructuredQuery, {
    from: [{ collectionId: "devices" }],
    orderBy: [{ field: { fieldPath: "updatedAt" }, direction: "DESCENDING" }],
    limit: 50,
  });
  assert.deepEqual(await responseJson(response), {
    devices: [
      {
        id: "device-1",
        packageName: "com.parsfilo.yasinsuresi",
        updatedAt: "2026-08-10T00:00:00.000Z",
      },
      {
        id: "device-2",
        packageName: "com.parsfilo.kible",
        updatedAt: "2026-08-09T00:00:00.000Z",
      },
    ],
  });
});

test("adminListRecentDevices: returns an empty array when there are no devices", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockFetch({
    certsKeys: [signer.jwk],
    firestore: {
      runQuery: () => new Response(JSON.stringify([]), { status: 200 }),
    },
  });

  const response = await handleAdminListRecentDevices(
    jsonRequest("https://admin-api.parsfilo.com/adminListRecentDevices", { token }),
    baseEnv(),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await responseJson(response), { devices: [] });
});
