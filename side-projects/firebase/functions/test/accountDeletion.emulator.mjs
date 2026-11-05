/**
 * #878: isolated Firebase Auth + Firestore operator-sequence rehearsal.
 *
 * This is a TEST HARNESS, not a production deletion entry point. It must never
 * start unless both emulators are bound to localhost and the demo project is
 * selected. No service-account key, real user, network provider or mail call.
 */
import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const PROJECT = "demo-contentapp";
if (
  process.env.GCLOUD_PROJECT !== PROJECT ||
  process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099" ||
  process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080"
) {
  throw new Error("Account deletion rehearsal requires localhost Auth + Firestore demo emulators");
}

const app = initializeApp({ projectId: PROJECT });
const auth = getAuth(app);
const db = getFirestore(app);
const ownerUid = "synthetic-deletion-owner-878";
const otherUid = "synthetic-other-owner-878";
const ownerEmail = "synthetic-owner-878@example.invalid";
const retainedUid = "synthetic-retained-owner-878";
const retainedEmail = "synthetic-retained-878@example.invalid";
const retryUid = "synthetic-retry-owner-878";
const retryEmail = "synthetic-retry-878@example.invalid";


async function seedSyntheticAccounts() {
  await auth.createUser({ uid: ownerUid, email: ownerEmail });
  await auth.createUser({ uid: otherUid, email: "synthetic-other-878@example.invalid" });
  await auth.createUser({ uid: retainedUid, email: retainedEmail });
  await auth.createUser({ uid: retryUid, email: retryEmail });
  const writes = [
    db.collection("user_messages").doc("owner-a").set({ userId: ownerUid, message: "fixture A" }),
    db.collection("user_messages").doc("owner-b").set({ userId: ownerUid, message: "fixture B" }),
    db.collection("user_messages").doc("other").set({ userId: otherUid, message: "other user" }),
    db.collection("user_messages").doc("retained").set({ userId: retainedUid, message: "synthetic retained user" }),
    db.collection("user_messages").doc("retry-a").set({ userId: retryUid, message: "first retry fixture" }),
    db.collection("user_messages").doc("retry-b").set({ userId: retryUid, message: "second retry fixture" }),
    db.collection("purchase_verifications").doc("owner").set({ uid: ownerUid, status: "test" }),
    db.collection("purchase_verifications").doc("other").set({ uid: otherUid, status: "test" }),
    db.collection("purchase_verifications").doc("retained").set({ uid: retainedUid, status: "test" }),
    db.collection("purchase_verifications").doc("retry").set({ uid: retryUid, status: "test" }),
    db.collection("devices").doc("not-linked").set({ installationId: "not-linked", packageName: "test" }),
  ];
  await Promise.all(writes);
}

async function rehearseDeletion({
  verifiedEmail,
  verifiedUid,
  ownerVerified,
  crossAppScopeConfirmed,
  purchaseDisposition,
  retentionBasis,
  retentionUntil,
  failAfterMessageDeletes,
}) {
  // Fail-closed preflight BEFORE any query, purchase change or Auth deletion.
  if (!ownerVerified || !crossAppScopeConfirmed || !verifiedEmail || !verifiedUid) {
    throw new Error("verified email, UID and cross-app scope required");
  }
  if (purchaseDisposition !== "erase" && purchaseDisposition !== "documented-retention") {
    throw new Error("purchase disposition requires an explicit review");
  }
  if (purchaseDisposition === "documented-retention" && (
    !retentionBasis?.trim() ||
    !/^\d{4}-\d{2}-\d{2}$/.test(retentionUntil ?? "") ||
    Number.isNaN(Date.parse(`${retentionUntil}T00:00:00Z`)) ||
    new Date(`${retentionUntil}T00:00:00Z`).toISOString().slice(0, 10) !== retentionUntil
  )) {
    throw new Error("documented retention requires a basis and valid ISO end date");
  }
  const user = await auth.getUserByEmail(verifiedEmail);
  if (user.uid !== verifiedUid) throw new Error("resolved UID did not match verified scope");

  const messages = await db.collection("user_messages").where("userId", "==", user.uid).get();
  const purchases = await db.collection("purchase_verifications").where("uid", "==", user.uid).get();
  const order = [];
  for (const doc of messages.docs) {
    await doc.ref.delete();
    order.push("message");
    // Test-only failure injection: simulate interruption after a completed write.
    if (failAfterMessageDeletes === order.length) {
      throw new Error("synthetic interruption before purchase and Auth deletion");
    }
  }
  if (purchaseDisposition === "erase") {
    for (const doc of purchases.docs) {
      await doc.ref.delete();
      order.push("purchase");
    }
  } else {
    order.push("purchase-retained");
  }
  // Deliberately never guess devices/{installationId} from an Auth UID.
  await auth.deleteUser(user.uid);
  order.push("auth-last");
  return { uid: user.uid, order };
}

before(async () => {
  await seedSyntheticAccounts();
});

after(async () => {
  await deleteApp(app);
});

test("operator rehearsal refuses missing identity, scope or retention decision", async () => {
  for (const input of [
    { verifiedEmail: ownerEmail, verifiedUid: ownerUid, ownerVerified: false, crossAppScopeConfirmed: true, purchaseDisposition: "erase" },
    { verifiedEmail: ownerEmail, verifiedUid: ownerUid, ownerVerified: true, crossAppScopeConfirmed: false, purchaseDisposition: "erase" },
    { verifiedEmail: ownerEmail, verifiedUid: ownerUid, ownerVerified: true, crossAppScopeConfirmed: true, purchaseDisposition: null },
    { verifiedEmail: ownerEmail, verifiedUid: ownerUid, ownerVerified: true, crossAppScopeConfirmed: true, purchaseDisposition: "documented-retention" },
    { verifiedEmail: ownerEmail, verifiedUid: ownerUid, ownerVerified: true, crossAppScopeConfirmed: true, purchaseDisposition: "documented-retention", retentionBasis: "synthetic hold", retentionUntil: "not-a-date" },
    { verifiedEmail: ownerEmail, verifiedUid: ownerUid, ownerVerified: true, crossAppScopeConfirmed: true, purchaseDisposition: "documented-retention", retentionBasis: "synthetic hold", retentionUntil: "2026-02-30" },
    { verifiedEmail: ownerEmail, verifiedUid: otherUid, ownerVerified: true, crossAppScopeConfirmed: true, purchaseDisposition: "erase" },
    { verifiedEmail: ownerEmail, verifiedUid: "", ownerVerified: true, crossAppScopeConfirmed: true, purchaseDisposition: "erase" },
  ]) {
    await assert.rejects(rehearseDeletion(input));
  }
  assert.equal((await auth.getUser(ownerUid)).uid, ownerUid);
  assert.equal((await db.collection("user_messages").where("userId", "==", ownerUid).get()).size, 2);
  assert.equal((await db.collection("purchase_verifications").where("uid", "==", ownerUid).get()).size, 1);
});

test("verified synthetic requester erases scoped data BEFORE Auth, preserves unrelated records", async () => {
  const result = await rehearseDeletion({
    verifiedEmail: ownerEmail,
    verifiedUid: ownerUid,
    ownerVerified: true,
    crossAppScopeConfirmed: true,
    purchaseDisposition: "erase",
  });
  assert.equal(result.uid, ownerUid);
  assert.deepEqual(result.order, ["message", "message", "purchase", "auth-last"]);
  await assert.rejects(auth.getUser(ownerUid), (error) => error.code === "auth/user-not-found");
  assert.equal((await db.collection("user_messages").where("userId", "==", ownerUid).get()).size, 0);
  assert.equal((await db.collection("purchase_verifications").where("uid", "==", ownerUid).get()).size, 0);
  assert.equal((await auth.getUser(otherUid)).uid, otherUid);
  assert.equal((await db.collection("user_messages").where("userId", "==", otherUid).get()).size, 1);
  assert.equal((await db.collection("purchase_verifications").where("uid", "==", otherUid).get()).size, 1);
  assert.equal((await db.collection("devices").doc("not-linked").get()).exists, true);
});

test("documented purchase retention preserves the hold while Auth is deleted last", async () => {
  // The hold values are synthetic fixtures, NOT a real retention law or term.
  const result = await rehearseDeletion({
    verifiedEmail: retainedEmail,
    verifiedUid: retainedUid,
    ownerVerified: true,
    crossAppScopeConfirmed: true,
    purchaseDisposition: "documented-retention",
    retentionBasis: "synthetic fixture: pending statutory accounting review",
    retentionUntil: "2099-01-01",
  });
  assert.equal(result.uid, retainedUid);
  assert.deepEqual(result.order, ["message", "purchase-retained", "auth-last"]);
  await assert.rejects(auth.getUser(retainedUid), (error) => error.code === "auth/user-not-found");
  assert.equal((await db.collection("user_messages").where("userId", "==", retainedUid).get()).size, 0);
  assert.equal((await db.collection("purchase_verifications").where("uid", "==", retainedUid).get()).size, 1);
  assert.equal((await auth.getUser(otherUid)).uid, otherUid);
  assert.equal((await db.collection("user_messages").where("userId", "==", otherUid).get()).size, 1);
  assert.equal((await db.collection("devices").doc("not-linked").get()).exists, true);
});

test("partial Firestore failure preserves Auth and retry safely resumes scoped deletion", async () => {
  const request = {
    verifiedEmail: retryEmail,
    verifiedUid: retryUid,
    ownerVerified: true,
    crossAppScopeConfirmed: true,
    purchaseDisposition: "erase",
  };
  await assert.rejects(
    rehearseDeletion({ ...request, failAfterMessageDeletes: 1 }),
    /synthetic interruption/,
  );
  // One message was erased; remaining records and the Auth identity survive.
  assert.equal((await auth.getUser(retryUid)).uid, retryUid);
  assert.equal((await db.collection("user_messages").where("userId", "==", retryUid).get()).size, 1);
  assert.equal((await db.collection("purchase_verifications").where("uid", "==", retryUid).get()).size, 1);
  assert.equal((await auth.getUser(otherUid)).uid, otherUid);

  // The same verified request can be retried without touching unrelated UIDs.
  const resumed = await rehearseDeletion(request);
  assert.deepEqual(resumed.order, ["message", "purchase", "auth-last"]);
  await assert.rejects(auth.getUser(retryUid), (error) => error.code === "auth/user-not-found");
  assert.equal((await db.collection("user_messages").where("userId", "==", retryUid).get()).size, 0);
  assert.equal((await db.collection("purchase_verifications").where("uid", "==", retryUid).get()).size, 0);
  assert.equal((await db.collection("user_messages").where("userId", "==", otherUid).get()).size, 1);
  assert.equal((await db.collection("purchase_verifications").where("uid", "==", otherUid).get()).size, 1);
  assert.equal((await db.collection("devices").doc("not-linked").get()).exists, true);
});
