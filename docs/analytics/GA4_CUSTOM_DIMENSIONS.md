# GA4 live configuration and analytics contract

This is the event-parameter inventory **read back from GA4 Admin** for property
`526790653` on 2026-10-19, shared by the MakerPars Android apps. It mirrors
`config/analytics-governance.json`; **editing this runbook or contract does not
change the live GA4 property**. Never register duplicate custom definitions.

## Event-scoped custom dimensions (12)

| GA4 event parameter | Purpose |
|---|---|
| `flavor_id` | Android product flavor, not a user identifier |
| `app_version` | App version attached to governed events |
| `consent_state` | Resolved ads/analytics consent state at the event |
| `ad_format` | Bounded banner/interstitial/native/app-open/rewarded format |
| `ad_placement` | Bounded UI ad placement (not legacy `placement`) |
| `content_id` | Bounded content identifier; avoid high-cardinality report breakdowns |
| `subscription_status` | Subscription state for funnel reports |
| `ad_error_category` | Ad SDK load failure category, not raw exception message |
| `suppress_reason` | Reason a show was ineligible (e.g. `no_consent`) |
| `consent_trigger` | Source triggering an official UMP consent check |
| `ad_discard_reason` | Native cache inventory eviction reason |
| `ump_error_category` | Privacy-safe consent callback failure category |

GA4 Admin also has **three event-scoped custom metrics**: `ad_value`
(standard), `audio_duration_ms` (milliseconds) and `load_latency_ms`
(milliseconds). App SDK events and these diagnostic custom metrics are not
interchangeable with authoritative AdMob network-request, impression or
estimated-earnings figures.

## Operational verifications

1. Filter GA4 analyses by **stream ID `13698218874`** and app version to
   isolate `com.parsfilo.ismiazamduasi` from other Android applications.
2. In DebugView, verify SDK-owned automatic `ad_impression` is distinct from
   the app-owned `ad_placement_impression` carrying `ad_format` and
   `ad_placement`. Do not add both event counts together.
3. Reconcile `ad_show_intent`, `ad_show_blocked` + `suppress_reason`,
   `ad_show_not_loaded`, `ad_served`, and `ad_inventory_discarded` **within the
   same version/day/format**. Those counters are not direct equivalents of
   AdMob network matched/paid impressions.
4. Confirm `consent_state` and user property consent flags are appropriately
   populated. EEA UMP regional acceptance requires a registered debug test
   device and cannot be inferred from consent observed in Türkiye.
5. Check retention and export **separately**: the GA4 Admin settings observed
   on 2026-10-19 were **2 months event retention** and **14 months user data
   retention**; the declarative governance contract's `retention_months=14`
   is a desired duration and not evidence of the actual event setting.
6. A GA4→BigQuery link being enabled is **not proof that daily tables are
   arriving**. As of 2026-10-19, `analytics_526790653` had no
   `events_YYYYMMDD` tables and official BigQuery GA4-exporter Cloud logs
   showed sandbox/free storage quota failures. Track remediation in #401.

## Privacy and reporting notes

- Do not create dimensions from raw email addresses, advertising IDs, raw
  error messages, raw device IDs, or user-generated free text.
- Use `AnalyticsParamKey` constants and the bounded analytics payload policy.
- Custom definitions can take processing time and are not backfilled onto
  older events. Empty dimensions in old builds do **not** prove the event
  failed; inspect the emitting app version and DebugView.
- GA4 publisher monetization events are separate from app purchases,
  engagement key events and AdMob reporting.
