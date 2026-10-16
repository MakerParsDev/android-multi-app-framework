import assert from "node:assert/strict";
import { after, afterEach, before, mock, test } from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const admin = require("firebase-admin");

const ALLOWLIST_EMAIL = "bootstrap@example.invalid";
process.env.ADMIN_ALLOWED_EMAILS = ALLOWLIST_EMAIL;
const { resolveAdminAccess } = require("../lib/adminAuth.js");

let firestore;
let ownsFirebaseApp = false;

before(() => {
  if (admin.apps.length === 0) {
    admin.initializeApp({ projectId: "demo-admin-auth-tests" });
    ownsFirebaseApp = true;
  }
  firestore = admin.firestore();
});

afterEach(() => {
  mock.restoreAll();
});

after(async () => {
  if (ownsFirebaseApp) await admin.app().delete();
});

function stubAdminDocument(record) {
  const writes = [];
  mock.method(firestore, "collection", (collectionName) => {
    assert.equal(collectionName, "admins");
    return {
      doc(uid) {
        assert.equal(uid, "test-admin");
        return {
          async get() {
            return record === null
              ? { exists: false }
              : { exists: true, data: () => record };
          },
          async set(data, options) {
            writes.push({ data, options });
          },
        };
      },
    };
  });
  return writes;
}

test("enabled admin role grants access without changing its document", async () => {
  const writes = stubAdminDocument({ role: "admin", enabled: true });
  const result = await resolveAdminAccess("test-admin", ALLOWLIST_EMAIL);
  assert.equal(result.authorized, true);
  assert.equal(result.source, "firestore");
  assert.equal(writes.length, 0);
});

test("disabled admin stays revoked even when its email is allowlisted", async () => {
  const writes = stubAdminDocument({ role: "admin", enabled: false });
  const result = await resolveAdminAccess("test-admin", ALLOWLIST_EMAIL);
  assert.equal(result.authorized, false);
  assert.equal(result.source, "firestore");
  assert.equal(writes.length, 0);
});

test("wrong role stays revoked even when its email is allowlisted", async () => {
  const writes = stubAdminDocument({ role: "viewer", enabled: true });
  const result = await resolveAdminAccess("test-admin", ALLOWLIST_EMAIL);
  assert.equal(result.authorized, false);
  assert.equal(writes.length, 0);
});

test("legacy records lacking required fields are denied", async () => {
  stubAdminDocument({ email: ALLOWLIST_EMAIL });
  const result = await resolveAdminAccess("test-admin", ALLOWLIST_EMAIL);
  assert.equal(result.authorized, false);
});

test("unknown, non-allowlisted users are denied", async () => {
  const writes = stubAdminDocument(null);
  const result = await resolveAdminAccess("test-admin", "outsider@example.invalid");
  assert.equal(result.authorized, false);
  assert.equal(result.source, "none");
  assert.equal(writes.length, 0);
});

test("missing allowlisted admin documents retain the intended bootstrap path", async () => {
  const writes = stubAdminDocument(null);
  const result = await resolveAdminAccess("test-admin", ALLOWLIST_EMAIL);
  assert.equal(result.authorized, true);
  assert.equal(result.source, "allowlist");
  assert.equal(writes.length, 1);
  assert.equal(writes[0].data.role, "admin");
  assert.equal(writes[0].data.enabled, true);
  assert.deepEqual(writes[0].options, { merge: true });
});

