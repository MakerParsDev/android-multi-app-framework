import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import { readFile } from "node:fs/promises";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  deleteDoc,
  doc,
  getDoc,
  setDoc,
  updateDoc,
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
