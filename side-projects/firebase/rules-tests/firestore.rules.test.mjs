import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import { readFile } from "node:fs/promises";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  setDoc,
  updateDoc,
  where,
} from "firebase/firestore";

const PROJECT_ID = "demo-contentapp";
const DEVICE_ID = "installation-12345678";
const DEVICE_DATA = {
  fcmToken: "a".repeat(100),
  timezone: "Europe/Istanbul",
  locale: "tr-TR",
  packageName: "com.parsfilo.yasinsuresi",
  notificationsEnabled: true,
};

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: await readFile(new URL("../firestore.rules", import.meta.url), "utf8"),
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "devices", DEVICE_ID), DEVICE_DATA);
    await setDoc(doc(context.firestore(), "admins", "admin-user"), {
      role: "admin",
      enabled: true,
    });
  });
});

after(async () => {
  await testEnv?.cleanup();
});

for (const [label, contextFactory] of [
  ["unauthenticated", () => testEnv.unauthenticatedContext()],
  ["authenticated", () => testEnv.authenticatedContext("regular-user")],
]) {
  test(`${label} clients cannot create device documents`, async () => {
    const firestore = contextFactory().firestore();
    await assertFails(setDoc(doc(firestore, "devices", `${DEVICE_ID}-new`), DEVICE_DATA));
  });

  test(`${label} clients cannot update device documents`, async () => {
    const firestore = contextFactory().firestore();
    await assertFails(updateDoc(doc(firestore, "devices", DEVICE_ID), { timezone: "UTC" }));
  });

  test(`${label} clients cannot delete device documents`, async () => {
    const firestore = contextFactory().firestore();
    await assertFails(deleteDoc(doc(firestore, "devices", DEVICE_ID)));
  });
}

test("regular authenticated clients cannot read device documents", async () => {
  const firestore = testEnv.authenticatedContext("regular-user").firestore();
  await assertFails(getDoc(doc(firestore, "devices", DEVICE_ID)));
});

test("admins can read device documents", async () => {
  const firestore = testEnv.authenticatedContext("admin-user").firestore();
  const snapshot = await assertSucceeds(getDoc(doc(firestore, "devices", DEVICE_ID)));
  assert.equal(snapshot.exists(), true);
});

for (const [label, adminData] of [
  ["disabled admin", { role: "admin", enabled: false }],
  ["wrong role", { role: "viewer", enabled: true }],
  ["legacy admin without role or enabled", { email: "admin@example.test" }],
]) {
  test(`${label} cannot read devices or change scheduled events`, async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "admins", "admin-user"), adminData);
    });
    const firestore = testEnv.authenticatedContext("admin-user").firestore();
    await assertFails(getDoc(doc(firestore, "devices", DEVICE_ID)));
    await assertFails(setDoc(doc(firestore, "scheduled_events", "blocked-event"), {
      title: "must not be written",
    }));
  });
}

// #878: synthetic account-deletion ownership and retention boundaries.
// These tests operate solely on the isolated demo-contentapp Firestore emulator.
// They do not authenticate, delete, or even connect to production users.
const OWNER_UID = "delete-request-owner";
const OTHER_UID = "unrelated-account";

async function seedDeletionFixture() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await setDoc(doc(firestore, "user_messages", "owner-first"), {
      userId: OWNER_UID,
      userEmail: "owner@example.invalid",
      userName: "Owner",
      message: "Synthetic message A",
      createdAt: new Date("2026-11-01T00:00:00Z"),
    });
    await setDoc(doc(firestore, "user_messages", "owner-second"), {
      userId: OWNER_UID,
      message: "Synthetic message B",
      createdAt: new Date("2026-11-02T00:00:00Z"),
    });
    await setDoc(doc(firestore, "user_messages", "other-message"), {
      userId: OTHER_UID,
      message: "Must not erase another account",
      createdAt: new Date("2026-11-03T00:00:00Z"),
    });
    await setDoc(doc(firestore, "purchase_verifications", "owner-purchase"), {
      uid: OWNER_UID,
      productId: "synthetic-product",
    });
    await setDoc(doc(firestore, "purchase_verifications", "other-purchase"), {
      uid: OTHER_UID,
      productId: "synthetic-product",
    });
  });
}

test("deletion workflow only accesses the verified UID's messages", async () => {
  await seedDeletionFixture();
  const owner = testEnv.authenticatedContext(OWNER_UID).firestore();
  const messages = collection(owner, "user_messages");
  const scoped = query(
    messages,
    where("userId", "==", OWNER_UID),
    orderBy("createdAt", "desc"),
  );
  const own = await assertSucceeds(getDocs(scoped));
  assert.deepEqual(own.docs.map((d) => d.id), ["owner-second", "owner-first"]);

  // A broad collection listing or a forged UID selector cannot enumerate accounts.
  await assertFails(getDocs(messages));
  await assertFails(getDocs(query(messages, where("userId", "==", OTHER_UID))));
  await assertFails(getDoc(doc(owner, "user_messages", "other-message")));
  await assertFails(getDocs(query(
    collection(testEnv.unauthenticatedContext().firestore(), "user_messages"),
    where("userId", "==", OWNER_UID),
  )));
});

test("deletion request cannot forge identity or erase another account", async () => {
  await seedDeletionFixture();
  const owner = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(setDoc(doc(owner, "user_messages", "forged-owner"), {
    userId: OTHER_UID,
    message: "Impersonation attempt",
  }));
  await assertFails(updateDoc(doc(owner, "user_messages", "owner-first"), {
    userId: OTHER_UID,
  }));
  await assertFails(deleteDoc(doc(owner, "user_messages", "other-message")));

  // The user's own data can be removed without deleting another user's data.
  await assertSucceeds(deleteDoc(doc(owner, "user_messages", "owner-first")));
  await assertSucceeds(deleteDoc(doc(owner, "user_messages", "owner-second")));
  const other = testEnv.authenticatedContext(OTHER_UID).firestore();
  const otherMessage = await assertSucceeds(getDoc(doc(other, "user_messages", "other-message")));
  assert.equal(otherMessage.exists(), true);
});

test("purchase history and unlinked device records are not client-deletable", async () => {
  await seedDeletionFixture();
  const owner = testEnv.authenticatedContext(OWNER_UID).firestore();
  const admin = testEnv.authenticatedContext("admin-user").firestore();
  // Purchase verification records may require legal retention review; only
  // trusted admin server operations are authorized to change them.
  for (const firestore of [owner, admin]) {
    await assertFails(deleteDoc(doc(firestore, "purchase_verifications", "owner-purchase")));
    await assertFails(deleteDoc(doc(firestore, "devices", DEVICE_ID)));
  }
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    assert.equal((await getDoc(doc(db, "purchase_verifications", "owner-purchase"))).exists(), true);
    assert.equal((await getDoc(doc(db, "purchase_verifications", "other-purchase"))).exists(), true);
    assert.equal((await getDoc(doc(db, "devices", DEVICE_ID))).exists(), true);
  });
});
