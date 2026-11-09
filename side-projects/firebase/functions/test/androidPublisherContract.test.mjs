import assert from "node:assert/strict";
import { createServer } from "node:http";
import test from "node:test";
import { google } from "googleapis";

test("Android Publisher v3 SDK preserves purchase verification HTTP contracts", async (t) => {
  const basePath = "/androidpublisher/v3/applications/com.example.billing";
  const requests = [];
  const server = createServer((req, res) => {
    const url = new URL(req.url, "http://127.0.0.1");
    requests.push({ method: req.method, path: url.pathname, key: url.searchParams.get("key") });
    res.setHeader("content-type", "application/json");

    if (req.method === "GET" && url.pathname ===
        `${basePath}/purchases/products/mock_item/tokens/mock-inapp-token`) {
      res.writeHead(200);
      res.end(JSON.stringify({ purchaseState: 0, acknowledgementState: 1 }));
    } else if (req.method === "GET" && url.pathname ===
        `${basePath}/purchases/subscriptionsv2/tokens/mock-sub-token`) {
      res.writeHead(200);
      res.end(JSON.stringify({
        subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
        acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        lineItems: [{ expiryTime: "2026-12-01T00:00:00Z", autoRenewingPlan: { autoRenewEnabled: true } }],
      }));
    } else if (req.method === "GET" && url.pathname ===
        `${basePath}/purchases/products/mock_item/tokens/rejected-token`) {
      res.writeHead(403);
      res.end(JSON.stringify({ error: { code: 403, message: "Synthetic permission denied" } }));
    } else {
      res.writeHead(404);
      res.end(JSON.stringify({ error: { code: 404, message: "Unexpected synthetic route" } }));
    }
  });
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  t.after(() => new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  }));

  // Loopback only: no real Google credentials or Play requests.
  const client = google.androidpublisher({ version: "v3", auth: "synthetic-test-only-key" });
  const options = { rootUrl: `http://127.0.0.1:${server.address().port}/`, timeout: 3000, retry: false };

  await t.test("in-app purchase GET decodes purchased and acknowledged states", async () => {
    const result = await client.purchases.products.get({
      packageName: "com.example.billing",
      productId: "mock_item",
      token: "mock-inapp-token",
    }, options);
    assert.equal(result.status, 200);
    assert.equal(result.data.purchaseState, 0);
    assert.equal(result.data.acknowledgementState, 1);
  });

  await t.test("subscription v2 GET decodes active subscription state", async () => {
    const result = await client.purchases.subscriptionsv2.get({
      packageName: "com.example.billing",
      token: "mock-sub-token",
    }, options);
    assert.equal(result.status, 200);
    assert.equal(result.data.subscriptionState, "SUBSCRIPTION_STATE_ACTIVE");
    assert.equal(result.data.lineItems[0].autoRenewingPlan.autoRenewEnabled, true);
  });

  await t.test("HTTP 403 is rejected rather than accepted as a purchase", async () => {
    await assert.rejects(
      client.purchases.products.get({
        packageName: "com.example.billing",
        productId: "mock_item",
        token: "rejected-token",
      }, options),
      (error) => error.response?.status === 403,
    );
  });

  await t.test("missing token fails before making an HTTP request", async () => {
    const before = requests.length;
    await assert.rejects(
      client.purchases.subscriptionsv2.get({ packageName: "com.example.billing" }, options),
      /Missing required parameters/,
    );
    assert.equal(requests.length, before);
  });

  assert.deepEqual(requests.map(({ method, path }) => [method, path]), [
    ["GET", `${basePath}/purchases/products/mock_item/tokens/mock-inapp-token`],
    ["GET", `${basePath}/purchases/subscriptionsv2/tokens/mock-sub-token`],
    ["GET", `${basePath}/purchases/products/mock_item/tokens/rejected-token`],
  ]);
  assert.ok(requests.every(({ key }) => key === "synthetic-test-only-key"));
});
