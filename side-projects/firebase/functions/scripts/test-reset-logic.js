const assert = require("node:assert/strict");

const {
  recurrencePeriodMillis,
  isRecurrenceResetDue,
} = require("../lib/dispatchNotifications.js");
const {
  parseDeliveryTime,
  getMatchingTimezones,
} = require("../lib/utils/timezone.js");

const DAY_MS = 24 * 60 * 60 * 1000;
const WEEK_MS = 7 * DAY_MS;

assert.equal(recurrencePeriodMillis("daily"), DAY_MS);
assert.equal(recurrencePeriodMillis("weekly:friday"), WEEK_MS);
assert.equal(recurrencePeriodMillis("monthly"), null);

assert.equal(isRecurrenceResetDue("daily", undefined, DAY_MS), false);
assert.equal(isRecurrenceResetDue("daily", 0, DAY_MS - 1), false);
assert.equal(isRecurrenceResetDue("daily", 0, DAY_MS), true);

assert.equal(isRecurrenceResetDue("weekly:monday", 0, WEEK_MS - 1), false);
assert.equal(isRecurrenceResetDue("weekly:monday", 0, WEEK_MS), true);

assert.deepEqual(parseDeliveryTime("21:30"), { hour: 21, minute: 30 });
assert.throws(() => parseDeliveryTime("24:00"));
assert.throws(() => parseDeliveryTime("21:60"));

const fixedNow = new Date("2026-01-01T18:30:00.000Z"); // Europe/Istanbul local: 21:30
const exactMatches = getMatchingTimezones("21:30", fixedNow, 60);
assert.equal(exactMatches.includes("Europe/Istanbul"), true);

const windowedMatches = getMatchingTimezones("21:00", fixedNow, 60);
assert.equal(windowedMatches.includes("Europe/Istanbul"), true);

const missedMatches = getMatchingTimezones("21:45", fixedNow, 60);
assert.equal(missedMatches.includes("Europe/Istanbul"), false);

console.log("reset logic tests passed");
