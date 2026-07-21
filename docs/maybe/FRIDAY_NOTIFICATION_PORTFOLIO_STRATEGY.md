# Friday Notification Portfolio Strategy

## Goal

Every Friday, send one respectful Friday greeting notification to active users.

The system must solve two problems:

1. Delivery time must follow the recipient's local Friday timing.
2. A person with multiple installed apps must not receive duplicate Friday notifications from multiple apps.

This design assumes the current notification infrastructure remains in Firebase Functions + Firestore and extends it with recipient-level dedupe and prayer-aware timing.

---

## Product Rules

### Core behavior

1. A recipient should receive at most one Friday greeting per calendar week.
2. Delivery should happen close to the recipient's local Friday midday window.
3. If the user has multiple installed apps, only one app should be chosen as the sender for that week.
4. Only recently active and notification-enabled devices should be eligible.

### Recommended delivery policy

1. Primary strategy: `prayer_aware`
2. Fallback strategy: `timezone_only`
3. Recipient dedupe scope: `portfolio`
4. Maximum sends per recipient per week: `1`
5. Default fallback local time: `11:45`
6. Active user window: `30 days`

---

## Recipient Identity Strategy

### Why app-level targeting is wrong

If the same person installed 3 or 4 apps, app-level scheduling sends 3 or 4 separate Friday notifications. That is the exact behavior we want to avoid.

### Recipient key priority

The scheduler should build a single `recipientKey` using the following order:

1. `userId` if the user is authenticated
2. `portfolioDeviceIdHash` if available
3. `installationId` as the last fallback

### Recommended shared identifier

Use a privacy-preserving portfolio device identifier:

`portfolioDeviceIdHash = sha256(projectSalt + stableDeviceSource)`

Rules:

1. Never store the raw device source.
2. Only store the hash.
3. Declare cross-app device grouping behavior in the privacy policy.
4. Keep the salt server-controlled.

---

## Firestore Schema Changes

### Existing collection: `devices`

Add or normalize these fields:

| Field | Type | Required | Purpose |
|---|---|---:|---|
| `installationId` | string | yes | Existing per-install identifier |
| `packageName` | string | yes | App package |
| `timezone` | string | yes | IANA timezone, example `Europe/Istanbul` |
| `locale` | string | no | Locale for content selection |
| `country` | string | no | Optional targeting / fallback |
| `city` | string | no | Optional location context |
| `latitude` | number | no | Prayer-aware calculation |
| `longitude` | number | no | Prayer-aware calculation |
| `notificationsEnabled` | boolean | yes | Eligibility gate |
| `lastActiveAt` | timestamp | yes | Activity-based selection |
| `portfolioDeviceIdHash` | string | no | Cross-app dedupe key |
| `userId` | string | no | Strongest recipient identity |
| `prayerCalculationProfile` | string | no | User or app-specific prayer method |
| `engagementScore7d` | number | no | App selection signal |
| `sessionCount7d` | number | no | App selection signal |
| `lastNotificationOpenAt` | timestamp | no | App selection signal |
| `lastFridaySentWeekId` | string | no | Fast duplicate suppression |
| `nextFridayMessageAt` | timestamp | no | Precomputed send timestamp |
| `nextFridayMessageTimezone` | string | no | Audit/debug |
| `campaignOptOut` | boolean | no | Friday campaign opt-out |

### New collection: `notification_portfolio_locks`

Purpose: prevent duplicate Friday messages across apps.

Document id format:

`{recipientKey}_{weekId}_friday`

Example:

`3f8d..._2026-W10_friday`

Fields:

| Field | Type | Purpose |
|---|---|---|
| `recipientKey` | string | Deduped target identity |
| `campaignType` | string | `friday_message` |
| `weekId` | string | ISO week id |
| `selectedPackage` | string | Winning app package |
| `selectedInstallationId` | string | Winning installation |
| `sentAt` | timestamp | Send audit |
| `timezone` | string | Recipient local timezone |
| `strategy` | string | `prayer_aware` or `timezone_only` |
| `contentVariant` | string | Variant id used that week |

This lock is the source of truth for weekly dedupe.

---

## Scheduling Strategy

### V1: timezone-based MVP

Use this first if you want fast rollout.

Rules:

1. Event recurrence: `weekly:friday`
2. Delivery time: `11:45` local time
3. Timezone source: `devices.timezone`
4. Dedupe scope: `recipientKey`

This is simple and already much better than global fixed-time sending.

### V2: prayer-aware timing

Upgrade once location quality is reliable.

Rules:

1. Compute Friday send time relative to `dhuhr` or `jumuah`
2. Recommended offset: `-45 minutes`
3. If prayer time cannot be computed, fallback to `11:45 local`

Recommended formula:

`nextFridayMessageAt = fridayDhuhrTime - 45 minutes`

Fallbacks:

1. Missing latitude/longitude -> use timezone fallback
2. Invalid timezone -> use country fallback or skip device
3. Inactive device -> skip

---

## Scheduler Flow

### Recommended runtime

Run a cron every 10 minutes.

Why:

1. Handles timezone windows cleanly
2. Avoids one giant weekly global job
3. Works with recipient-level dedupe safely

### Execution steps

1. Determine current UTC time.
2. Query candidate devices where:
   - `notificationsEnabled = true`
   - `campaignOptOut != true`
   - `lastActiveAt >= now - 30 days`
   - `nextFridayMessageAt <= now`
   - local weekday is Friday
3. Group candidates by `recipientKey`.
4. For each group:
   - derive `weekId`
   - check `notification_portfolio_locks/{recipientKey}_{weekId}_friday`
   - if lock exists, skip
   - else select one winning app/device
5. Send notification only to the selected app/device.
6. Create the portfolio lock.
7. Update device metadata:
   - `lastFridaySentWeekId`
   - `lastCampaignSentAt`
8. Write send logs for observability.

---

## Winning App Selection

When multiple apps exist for the same recipient, choose one installation.

### Recommended ranking order

1. `notificationsEnabled = true`
2. Most recent `lastActiveAt`
3. Highest `engagementScore7d`
4. Most recent `lastNotificationOpenAt`
5. Portfolio priority list fallback

### Suggested priority list

1. `com.parsfilo.namazvakitleri`
2. `com.parsfilo.kuran_kerim`
3. Other apps

Reasoning:

Friday greeting belongs most naturally in apps with strong weekly or prayer-oriented engagement.

---

## Admin Panel Changes

### New event type

Add a campaign-oriented type:

- `friday_message`

### New event fields

| Field | Type | Example |
|---|---|---|
| `campaignType` | string | `friday_message` |
| `deliveryStrategy` | string | `prayer_aware` |
| `relativeToPrayer` | string | `dhuhr` |
| `offsetMinutes` | number | `-45` |
| `fallbackLocalTime` | string | `11:45` |
| `dedupeScope` | string | `portfolio` |
| `maxPerRecipientPerWeek` | number | `1` |
| `appSelectionMode` | string | `most_recently_active` |
| `priorityPackages` | array | package priority list |
| `activeWindowDays` | number | `30` |
| `contentVariantPool` | array | `v1,v2,v3...` |

### Recommended admin UI labels

1. `Delivery strategy`
2. `Relative prayer`
3. `Offset minutes`
4. `Fallback local time`
5. `Dedupe scope`
6. `Max sends / week`
7. `App selection`
8. `Active user window`

---

## Content Strategy

Do not send the exact same Friday message every week.

### Recommended message pool

Use 4 to 8 rotating variants:

1. Short Friday greeting
2. Friday prayer reminder
3. Short dua-based greeting
4. Verse-based greeting

### Anti-repetition rule

Track the previous variant per recipient and avoid sending the same variant twice in a row.

Suggested fields:

1. `lastFridayVariant` on the winning device record
2. `contentVariant` in the portfolio lock

---

## Eligibility Rules

Recommended skip conditions:

1. `notificationsEnabled != true`
2. `campaignOptOut = true`
3. `lastActiveAt < now - 30 days`
4. same recipient already received a Friday message in current `weekId`
5. another campaign was sent too recently

Suggested additional safety:

1. Only send within `09:00-13:30` local window
2. If device timezone is invalid, skip and log
3. If no valid app/device remains in a recipient group, skip and log

---

## Rollout Plan

### Phase 1

1. Add `recipientKey` support
2. Add `notification_portfolio_locks`
3. Run Friday campaign with timezone-based `11:45 local`
4. Use `most_recently_active` app selection

### Phase 2

1. Add prayer-aware schedule calculation
2. Precompute `nextFridayMessageAt`
3. Add variant rotation and recipient-level anti-repetition

### Phase 3

1. Add analytics dashboard for Friday campaign performance
2. Measure:
   - send success rate
   - notification open rate
   - opt-out impact
   - duplicate-prevention rate

---

## Observability

Track these events and counters:

1. `friday_campaign_candidate_count`
2. `friday_campaign_recipient_groups`
3. `friday_campaign_sent_count`
4. `friday_campaign_skipped_duplicate`
5. `friday_campaign_skipped_inactive`
6. `friday_campaign_skipped_notifications_disabled`
7. `friday_campaign_send_failures`

Recommended log fields:

1. `recipientKey`
2. `weekId`
3. `selectedPackage`
4. `strategy`
5. `timezone`
6. `contentVariant`

---

## Privacy Notes

1. Do not store raw cross-app device identifiers.
2. Use hashed recipient grouping only.
3. Document cross-app notification dedupe behavior in privacy policy.
4. Keep campaign targeting limited to active, opted-in users.

---

## Recommended Final Decision

If this were shipping now, the safest implementation order would be:

1. V1 timezone-based Friday scheduler
2. portfolio-level dedupe lock
3. winning app selection by recent activity
4. 30-day active-user filter
5. message variant rotation
6. prayer-aware timing in V2

This gives you a clean Friday campaign without notification spam and without waiting for perfect prayer-time data quality.
