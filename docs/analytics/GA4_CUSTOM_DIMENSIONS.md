# GA4 Custom Dimensions / Metrics Setup

This runbook aligns Firebase Analytics events with GA4 reporting for ad funnel + consent + engagement revenue analysis.

## Scope

- Project: `makerpars-oaslananka-mobil`
- Source contract:
  - `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsContract.kt`
  - `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsConsent.kt`

## Required User Properties

Register as user-scoped dimensions:

1. `consent_status`
2. `is_premium`
3. `age_gate_status`
4. `flavor`
5. `build_type`
6. `app_lang`
7. `tz`

## Required Event Parameters (Custom Dimensions)

Register as event-scoped dimensions:

1. `ad_format`
2. `placement`
3. `route`
4. `suppress_reason`
5. `adapter_name`
6. `consent_status`
7. `consent_trigger`
8. `ump_form_shown`
9. `age_gate_result`
10. `session_content_type`

## Required Event Parameters (Custom Metrics)

Register as event-scoped metrics:

1. `fill_latency_ms` (integer)
2. `backoff_attempt` (integer)
3. `watch_duration_s` (integer)
4. `reward_minutes_earned` (integer)
5. `total_watch_count` (integer)
6. `session_duration_s` (integer)
7. `verse_count_before_ad` (integer)
8. `session_verse_count` (integer, if emitted)
9. `session_audio_played` (integer 0/1)

## Event Groups to Validate in DebugView

1. Consent:
   - `consent_flow_started`
   - `consent_granted` / `consent_denied` / `consent_not_required`
   - `consent_error`
   - `consent_refreshed`
   - `privacy_options_opened`
   - `age_gate_completed`
2. Ad funnel:
   - `ad_request_sent`
   - `ad_loaded`
   - `ad_failed_to_load`
   - `ad_suppressed`
   - `ad_impression`
   - `ad_failed_to_show`
   - `ad_paid_event`
3. Rewarded:
   - `rewarded_watch_complete`
   - `rewarded_watch_skipped`
4. Engagement correlation:
   - `ad_after_engagement`

## Operational Notes

1. Keep event and parameter names immutable after release.
2. Use normalized route templates only (`/detail/{id}` style), not raw IDs.
3. `ad_paid_event` remains source-of-truth for revenue.
4. Build dashboards with both:
   - `Today so far` operational view (admin panel)
   - BigQuery cohort analysis (weekly/monthly strategy).

