import test from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { generateCombinedCloudEvent } = require("firebase-functions-test/lib/cloudevent/generate.js");

test("generateCombinedCloudEvent merges partial CloudEvent without ts-deepmerge runtime crash", () => {
  const partial = {
    type: "google.cloud.pubsub.topic.v1.messagePublished",
    source: "//pubsub.googleapis.com/projects/test-project/topics/test-topic",
    data: { message: { data: "test-payload" } },
  };

  const event = generateCombinedCloudEvent(null, partial);
  assert.ok(event, "CloudEvent should be generated");
  assert.equal(event.type, partial.type);
  assert.equal(event.source, partial.source);
  assert.deepEqual(event.data, partial.data);
});
