import test from "node:test";
import { createHealthPayload } from "../lib/healthCheck.js";
import { assertHealthPayload } from "../../../contracts/health-contract.mjs";

test("Firebase health payload includes git SHA and build timestamp", () => {
  const payload = createHealthPayload({ FUNCTIONS_ENVIRONMENT: "test" });
  assertHealthPayload(payload, { service: "firebase-functions" });
});
