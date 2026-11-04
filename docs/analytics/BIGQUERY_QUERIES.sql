-- Analytics + Ad Revenue Queries (GA4 BigQuery Export)
-- Project: makerpars-oaslananka-mobil
-- Dataset: analytics_<property_id>
-- Table pattern: events_*
-- NOTE:
-- 1) Replace `<DATASET>` with your dataset name.
-- 2) Keep date filters narrow for cost control.

--------------------------------------------------------------------------------
-- 1) Daily ARPU / ARPDAU (from ad_paid_event)
--------------------------------------------------------------------------------
WITH paid AS (
  SELECT
    event_date,
    user_pseudo_id,
    SAFE_CAST((
      SELECT ep.value.int_value
      FROM UNNEST(event_params) ep
      WHERE ep.key = 'value_micros'
    ) AS INT64) AS value_micros
  FROM `<DATASET>.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
    AND event_name = 'ad_paid_event'
),
daily AS (
  SELECT
    event_date,
    COUNT(DISTINCT user_pseudo_id) AS dau,
    SUM(IFNULL(value_micros, 0)) / 1000000.0 AS revenue_try
  FROM paid
  GROUP BY event_date
)
SELECT
  event_date,
  dau,
  revenue_try,
  SAFE_DIVIDE(revenue_try, dau) AS arpdau
FROM daily
ORDER BY event_date;

--------------------------------------------------------------------------------
-- 2) App-owned ad funnel diagnostics by app/version/format/placement
--------------------------------------------------------------------------------
-- These are client event counts, not AdMob billable network impressions.
-- 'ad_impression' is Firebase/AdMob-collected and has no reliable client
-- placement key. Use only the separate SDK callback 'ad_placement_impression'
-- for placement attribution. UI handoffs, callbacks and paid impressions
-- are distinct events; do not calculate an impression/revenue rate from them.
-- Compare mature days and the same app/version cohort; daily GA4 exports can
-- receive late events, and older binaries may not emit all diagnostic events.
WITH base AS (
  SELECT
    event_date,
    event_name,
    COALESCE(app_info.id, 'unknown') AS app_package,
    COALESCE(app_info.version, 'unknown') AS app_version,
    COALESCE(
      (SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'ad_format'),
      'unknown'
    ) AS ad_format,
    COALESCE(
      (SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'ad_placement'),
      'unknown'
    ) AS ad_placement,
    COALESCE(
      (SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'ad_error_category'),
      'unknown'
    ) AS ad_error_category
  FROM `<DATASET>.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
    AND event_name IN (
      'ad_request_sent', 'ad_loaded', 'ad_served', 'ad_inventory_discarded',
      'ad_placement_impression', 'ad_failed_to_load', 'ad_suppressed'
    )
)
SELECT
  event_date,
  app_package,
  app_version,
  ad_format,
  ad_placement,
  COUNTIF(event_name = 'ad_request_sent') AS request_events,
  COUNTIF(event_name = 'ad_loaded') AS loaded_events,
  COUNTIF(event_name = 'ad_served') AS ui_handoffs,
  COUNTIF(event_name = 'ad_inventory_discarded') AS native_discarded_before_handoff,
  COUNTIF(event_name = 'ad_placement_impression') AS placement_impression_callbacks,
  COUNTIF(event_name = 'ad_failed_to_load') AS failed_to_load_events,
  COUNTIF(event_name = 'ad_failed_to_load' AND ad_error_category IN ('no_fill', 'mediation_no_fill')) AS no_fill_events,
  COUNTIF(event_name = 'ad_failed_to_load' AND ad_error_category IN ('internal', 'network', 'invalid_request', 'configuration', 'other_domain', 'other')) AS other_categorized_failure_events,
  COUNTIF(event_name = 'ad_failed_to_load' AND ad_error_category = 'unknown') AS uncategorized_failure_events,
  COUNTIF(event_name = 'ad_suppressed') AS suppressed_events
FROM base
GROUP BY event_date, app_package, app_version, ad_format, ad_placement
ORDER BY event_date, app_package, app_version, ad_format, ad_placement;

--------------------------------------------------------------------------------
-- 3) Consent cohort performance
--------------------------------------------------------------------------------
WITH paid AS (
  SELECT
    event_date,
    user_pseudo_id,
    SAFE_CAST((
      SELECT ep.value.int_value
      FROM UNNEST(event_params) ep
      WHERE ep.key = 'value_micros'
    ) AS INT64) AS value_micros
  FROM `<DATASET>.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
    AND event_name = 'ad_paid_event'
),
consent AS (
  SELECT
    user_pseudo_id,
    COALESCE(
      (SELECT up.value.string_value FROM UNNEST(user_properties) up WHERE up.key = 'consent_status'),
      'unknown'
    ) AS consent_status
  FROM `<DATASET>.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
)
SELECT
  p.event_date,
  c.consent_status,
  COUNT(DISTINCT p.user_pseudo_id) AS users,
  SUM(IFNULL(p.value_micros, 0)) / 1000000.0 AS revenue_try,
  SAFE_DIVIDE(SUM(IFNULL(p.value_micros, 0)) / 1000000.0, COUNT(DISTINCT p.user_pseudo_id)) AS arpu_try
FROM paid p
LEFT JOIN consent c
  ON c.user_pseudo_id = p.user_pseudo_id
GROUP BY p.event_date, c.consent_status
ORDER BY p.event_date, c.consent_status;

--------------------------------------------------------------------------------
-- 4) Suppression reason analysis
--------------------------------------------------------------------------------
SELECT
  event_date,
  COALESCE(
    (SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'ad_format'),
    'unknown'
  ) AS ad_format,
  COALESCE(
    (SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'suppress_reason'),
    'unknown'
  ) AS suppress_reason,
  COUNT(*) AS suppress_count
FROM `<DATASET>.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
  AND event_name = 'ad_suppressed'
GROUP BY event_date, ad_format, suppress_reason
ORDER BY event_date, suppress_count DESC;

--------------------------------------------------------------------------------
-- 5) Engagement -> ad correlation (ad_after_engagement)
--------------------------------------------------------------------------------
SELECT
  event_date,
  COALESCE((SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'session_content_type'), 'unknown') AS session_content_type,
  COALESCE((SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'route'), 'unknown') AS route,
  COALESCE((SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'ad_format'), 'unknown') AS ad_format,
  AVG(COALESCE((SELECT ep.value.int_value FROM UNNEST(event_params) ep WHERE ep.key = 'session_duration_s'), 0)) AS avg_session_duration_s,
  AVG(COALESCE((SELECT ep.value.int_value FROM UNNEST(event_params) ep WHERE ep.key = 'verse_count_before_ad'), 0)) AS avg_verse_count_before_ad,
  AVG(COALESCE((SELECT ep.value.int_value FROM UNNEST(event_params) ep WHERE ep.key = 'session_audio_played'), 0)) AS audio_played_ratio,
  COUNT(*) AS events
FROM `<DATASET>.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
  AND event_name = 'ad_after_engagement'
GROUP BY event_date, session_content_type, route, ad_format
ORDER BY event_date, events DESC;

--------------------------------------------------------------------------------
-- 6) Rewarded completion vs skipped
--------------------------------------------------------------------------------
SELECT
  event_date,
  event_name,
  COUNT(*) AS event_count,
  AVG(COALESCE((SELECT ep.value.int_value FROM UNNEST(event_params) ep WHERE ep.key = 'watch_duration_s'), 0)) AS avg_watch_duration_s,
  AVG(COALESCE((SELECT ep.value.int_value FROM UNNEST(event_params) ep WHERE ep.key = 'reward_minutes_earned'), 0)) AS avg_reward_minutes,
  AVG(COALESCE((SELECT ep.value.int_value FROM UNNEST(event_params) ep WHERE ep.key = 'total_watch_count'), 0)) AS avg_total_watch_count
FROM `<DATASET>.events_*`
WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
  AND event_name IN ('rewarded_watch_complete', 'rewarded_watch_skipped')
GROUP BY event_date, event_name
ORDER BY event_date, event_name;

