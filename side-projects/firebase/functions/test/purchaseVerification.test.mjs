import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { inspect } from "node:util";
import { after, before, mock, test } from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const admin = require("firebase-admin");
const { google } = require("googleapis");
const logger = require("firebase-functions/logger");
const { verifyPurchase } = require("../lib/verifyPurchase.js");

let createdApp = false;
let playResponse;
let playError;
let requests;
let writes;
let errorLogs;

before(() => {
  if (admin.apps.length === 0) {
    admin.initializeApp({ projectId: "demo-purchase-verification-tests" });
    createdApp = true;
  }

  const auth = admin.auth();
  const appCheck = admin.appCheck();
  const db = admin.firestore();
  requests = [];
  writes = [];
  errorLogs = [];
  mock.method(logger, "error", (...args) => errorLogs.push(args));

  mock.method(auth, "verifyIdToken", async (token) => {
    assert.equal(token, "fake-id-token");
    return { uid: "fake-user" };
  });
  mock.method(appCheck, "verifyToken", async (token) => {
    assert.equal(token, "fake-appcheck-token");
    return {};
  });
  mock.method(db, "collection", (collection) => {
    assert.equal(collection, "purchase_verifications");
    return {
      doc(id) {
        return {
          async set(data, options) {
            writes.push({ id, data, options });
          },
        };
      },
    };
  });
  mock.method(google, "androidpublisher", ({ version }) => {
    assert.equal(version, "v3");
    return {
      purchases: {
        subscriptionsv2: {
          async get(params) {
            requests.push({ kind: "subscription", params });
            if (playError) throw playError;
            return { data: playResponse };
          },
        },
        products: {
          async get(params) {
            requests.push({ kind: "inapp", params });
            if (playError) throw playError;
            return { data: playResponse };
          },
        },
      },
    };
  });
});

after(async () => {
  mock.restoreAll();
  if (createdApp) await admin.app().delete();
});

function fakeRequest(overrides = {}) {
  const headers = {
    "content-type": "application/json",
    authorization: "Bearer fake-id-token",
    "x-firebase-appcheck": "fake-appcheck-token",
    ...overrides.headers,
  };
  return {
    method: overrides.method ?? "POST",
    headers,
    get(key) {
      return headers[key.toLowerCase()];
    },
    body: {
      packageName: "com.example.test",
      productId: "reklamsiz_kullanim",
      purchaseToken: "fake-purchase-token",
      purchaseType: "subs",
      ...overrides.body,
    },
  };
}

async function invoke(request) {
  const response = {
    status(code) { this.code = code; return this; },
    json(data) { this.payload = data; return this; },
    setHeader() {},
    getHeader() { return undefined; },
    header() { return this; },
    end() {},
    on() {},
    writeHead() {},
  };
  await verifyPurchase(request, response);
  return response;
}

test("Google Play v186 purchase verifier preserves fail-closed premium entitlement", async (t) => {
  await t.test("active subscription is verified, hashed and persisted through subscriptionsv2", async () => {
    playResponse = {
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
      lineItems: [{ expiryTime: "2030-01-01T00:00:00Z", autoRenewingPlan: { autoRenewEnabled: true } }],
    };
    const response = await invoke(fakeRequest());
    assert.equal(response.code, 200);
    assert.equal(response.payload.verified, true);
    assert.equal(response.payload.purchaseState, "SUBSCRIPTION_STATE_ACTIVE");
    assert.equal(requests.at(-1).kind, "subscription");
    assert.deepEqual(requests.at(-1).params, {
      packageName: "com.example.test",
      token: "fake-purchase-token",
    });
    assert.equal(writes.at(-1).data.verified, true);
    assert.equal(
      writes.at(-1).data.tokenHash,
      createHash("sha256").update("fake-purchase-token").digest("hex"),
    );
    assert.equal("purchaseToken" in writes.at(-1).data, false);
  });

  await t.test("expired subscription is not premium", async () => {
    playResponse = {
      subscriptionState: "SUBSCRIPTION_STATE_EXPIRED",
      lineItems: [{ expiryTime: "2020-01-01T00:00:00Z" }],
    };
    const response = await invoke(fakeRequest());
    assert.equal(response.code, 200);
    assert.equal(response.payload.verified, false);
    assert.equal(writes.at(-1).data.verified, false);
  });

  await t.test("valid in-app purchase uses products.get", async () => {
    playResponse = { purchaseState: 0, acknowledgementState: 1 };
    const response = await invoke(fakeRequest({ body: { purchaseType: "inapp" } }));
    assert.equal(response.code, 200);
    assert.equal(response.payload.verified, true);
    assert.equal(requests.at(-1).kind, "inapp");
    assert.deepEqual(requests.at(-1).params, {
      packageName: "com.example.test",
      productId: "reklamsiz_kullanim",
      token: "fake-purchase-token",
    });
  });

  await t.test("cancelled in-app purchase is not premium", async () => {
    playResponse = { purchaseState: 1, acknowledgementState: 0 };
    const response = await invoke(fakeRequest({ body: { purchaseType: "inapp" } }));
    assert.equal(response.code, 200);
    assert.equal(response.payload.verified, false);
  });

  await t.test("missing user auth and missing App Check fail before contacting Google Play", async () => {
    const beforeRequests = requests.length;
    const beforeWrites = writes.length;
    const unauthenticated = await invoke(fakeRequest({ headers: { authorization: undefined } }));
    assert.equal(unauthenticated.code, 401);
    const missingAppCheck = await invoke(fakeRequest({ headers: { "x-firebase-appcheck": undefined } }));
    assert.equal(missingAppCheck.code, 401);
    assert.equal(requests.length, beforeRequests);
    assert.equal(writes.length, beforeWrites);
  });

  await t.test("malformed payload fails without Google Play verification", async () => {
    const beforeRequests = requests.length;
    const response = await invoke(fakeRequest({ body: { packageName: "invalid" } }));
    assert.equal(response.code, 400);
    assert.equal(requests.length, beforeRequests);
  });

  await t.test("Play API error denies entitlement and never persists verified status", async () => {
    const beforeWrites = writes.length;
    const beforeErrorLogs = errorLogs.length;
    const unsafeUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.example.test/purchases/subscriptionsv2/tokens/fake-purchase-token";
    playError = new Error(`synthetic Play API failure: ${unsafeUrl}`);
    playError.response = { config: { url: unsafeUrl }, status: 403 };
    try {
      const response = await invoke(fakeRequest());
      assert.equal(response.code, 502);
      assert.equal(response.payload.verified, false);
      assert.equal(writes.length, beforeWrites);
      assert.equal(errorLogs.length, beforeErrorLogs + 1);
      assert.equal(errorLogs.at(-1)[0], "verifyPurchase failed");
      assert.deepEqual(Object.keys(errorLogs.at(-1)[1]).sort(), [
        "packageName", "productId", "purchaseType", "uid",
      ]);
      assert.equal(
        inspect(errorLogs.at(-1), { depth: 8, showHidden: true }).includes("fake-purchase-token"),
        false,
      );
    } finally {
      playError = undefined;
    }
  });
});
