import assert from "node:assert/strict";
import { test } from "node:test";
import { createSummaryResponseCache } from "../.test-dist/summaryResponseCache.js";

const response = (value, status = 200) =>
  new Response(JSON.stringify({ value }), { status, headers: { "content-type": "application/json" } });

test("serves authorized summary data from a bounded TTL cache", async () => {
  let time = 1000;
  let loads = 0;
  const cached = createSummaryResponseCache({ now: () => time, maxEntries: 2 });
  const load = async () => response(++loads);
  const first = await cached("analytics", 600, load);
  assert.deepEqual(await first.json(), { value: 1 });
  const second = await cached("analytics", 600, load);
  assert.deepEqual(await second.json(), { value: 1 });
  assert.equal(second.headers.get("cache-control"), "no-store");
  assert.equal(loads, 1);
  time += 601;
  assert.deepEqual(await (await cached("analytics", 600, load)).json(), { value: 2 });
  assert.equal(loads, 2);
});

test("coalesces simultaneous requests for the same summary", async () => {
  let calls = 0;
  const cached = createSummaryResponseCache();
  const load = async () => {
    calls += 1;
    await Promise.resolve();
    return response("ok");
  };
  const results = await Promise.all(Array.from({ length: 8 }, () => cached("revenue", 600, load)));
  assert.equal(calls, 1);
  for (const result of results) assert.deepEqual(await result.json(), { value: "ok" });
});

test("does not cache quota errors or unexpected failures", async () => {
  let calls = 0;
  const cached = createSummaryResponseCache();
  const load = async () => response(++calls === 1 ? "quota" : "recovered", calls === 1 ? 503 : 200);
  assert.equal((await cached("analytics", 1000, load)).status, 503);
  assert.deepEqual(await (await cached("analytics", 1000, load)).json(), { value: "recovered" });
  assert.equal(calls, 2);
});

test("evicts oldest entries and keeps different requests isolated", async () => {
  let calls = 0;
  const cached = createSummaryResponseCache({ maxEntries: 2 });
  const load = async () => response(++calls);
  await cached("a", 10_000, load);
  await cached("b", 10_000, load);
  await cached("c", 10_000, load);
  assert.deepEqual(await (await cached("a", 10_000, load)).json(), { value: 4 });
  assert.equal(calls, 4);
});

test("does not cache failed loaders and allows a later retry", async () => {
  const cached = createSummaryResponseCache();
  let calls = 0;
  await assert.rejects(cached("a", 10_000, async () => { calls++; throw Error("temporary"); }));
  assert.equal((await cached("a", 10_000, async () => { calls++; return response("ok"); })).status, 200);
  assert.equal(calls, 2);
});
