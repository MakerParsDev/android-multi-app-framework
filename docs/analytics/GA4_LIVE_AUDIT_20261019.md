# GA4 live administration audit — 19 October 2026

Scope: shared GA4 property `526790653`, Firebase/GCP project `makerpars-oaslananka-mobil`;
İsmi Azam Android stream `13698218874`. Read-only reconciliation with live GA4 Admin,
BigQuery dataset metadata, Cloud Audit logs, and the main-branch analytics contract.

## What is already operational

- App telemetry, AdMob linked app and Google Play linkage are present and sending data.
- All **12** event-scoped custom dimensions registered in the property are declared in
  `config/analytics-governance.json`. Five live registrations present since 16–17 Oct
  were previously omitted: `ad_error_category`, `suppress_reason`, `consent_trigger`,
  `ad_discard_reason`, `ump_error_category`. This commit only aligns the local inventory;
  **no new GA4 custom definitions or Firebase SDK changes** are needed.
- The GA4 BigQuery link selects İsmi Azam among **23/27** streams, uses daily export,
  excludes no event names, and targets dataset `analytics_526790653` in `europe-west1`.

## Broken: raw-event BigQuery export (#401)

The GA4 Admin link displays enabled, **but the BigQuery dataset has zero
`events_YYYYMMDD` tables** (only `pseudonymous_users_20260916`). Cloud Audit logs
for actual exporter `firebase-measurement@system.gserviceaccount.com` show repeated
`RESOURCE_EXHAUSTED` jobs on 18 Oct with **'Quota exceeded: Your project exceeded
quota for free storage for projects'**. This GCP project's billing is disabled,
although the exporter has `roles/bigquery.user` and dataset `OWNER`.

BigQuery **Sandbox has a lifetime 10 GiB storage limit that is not refunded by
deleting data**, plus mandatory 60-day table expiration:
<https://docs.cloud.google.com/bigquery/docs/sandbox#limitations>.
This resolves why near-empty *current* datasets can no longer receive writes.
**Do not delete datasets or relink GA4 to recover lifetime quota**. A sustainable
restore in the same project requires reviewing a billing upgrade with cost budgets,
least-privilege query guards and retention first; nothing was enabled here.
A separate Sandbox would only buy another finite lifetime allowance.

Once remediated, require three successive daily `events_YYYYMMDD` tables with
Ismi Azam's stream, version and event parameter fields; previously missed days
may not be backfilled. Reference: <https://support.google.com/analytics/answer/9823238>.

## Retention, privacy, and important events

- **Observed GA4 event retention: 2 months**. The governance manifest currently
  specifies a **target of 14 months**, *not a setting automatically applied by CI*.
  Review privacy, storage and reporting impact before changing this live setting.
- **BigQuery dataset default table expiration: 60 days** (forced by Sandbox).
- Mobile advertising identifiers are included in the GA4 BigQuery link. Assess need,
  lawful basis and minimization before exporting such identifiers at scale; do not
  disable or extend collection automatically as part of inventory reconciliation.
- **Live GA4 key-event definitions: 11** (`app_store_subscription_convert`,
  `app_store_subscription_renew`, `audio_complete`, `close_convert_lead`,
  `content_play_complete`, `first_open`, `in_app_purchase`, `purchase`,
  `push_open`, `qualify_lead`, `rewarded_watch_complete`).
  The manifest's **five desired key events** are a target/curation policy, not a
  mirror of all live Admin definitions. The six extras are not automatically wrong:
  review their purpose and event volume with the business owner before unmarking.
- Several desired/defined events have not appeared in the recent 28-day Admin view;
  never report conversion-rate improvement without verifying emitters and sample size.

## Acceptance status

**PASS:** App events, AdMob/Google Play links and 12 live dimension names verified.

**BLOCKED:** Historical raw data pipeline (#401) until sandbox/quota remediation.

**PENDING:** Privacy-approved retention change, key-event curation, event emitter
completeness, and joined revenue / retention cohort reports.

No changes to Firebase Remote Config, ads production settings, permissions,
Cloud Billing or data deletion were made in this audit.
