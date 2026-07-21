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
-- 2) Ad funnel by format/placement (request -> loaded -> impression)
--------------------------------------------------------------------------------
WITH base AS (
  SELECT
    event_date,
    event_name,
    COALESCE(
      (SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'ad_format'),
      'unknown'
    ) AS ad_format,
    COALESCE(
      (SELECT ep.value.string_value FROM UNNEST(event_params) ep WHERE ep.key = 'placement'),
      'unknown'
    ) AS placement
  FROM `<DATASET>.events_*`
  WHERE _TABLE_SUFFIX BETWEEN '20260301' AND '20260331'
    AND event_name IN ('ad_request_sent', 'ad_loaded', 'ad_impression', 'ad_failed_to_load', 'ad_suppressed')
)
SELECT
  event_date,
  ad_format,
  placement,
  COUNTIF(event_name = 'ad_request_sent') AS requests,
  COUNTIF(event_name = 'ad_loaded') AS loaded,
  COUNTIF(event_name = 'ad_impression') AS impressions,
  COUNTIF(event_name = 'ad_failed_to_load') AS failed_to_load,
  COUNTIF(event_name = 'ad_suppressed') AS suppressed,
  SAFE_DIVIDE(COUNTIF(event_name = 'ad_loaded'), COUNTIF(event_name = 'ad_request_sent')) * 100 AS load_rate_pct,
  SAFE_DIVIDE(COUNTIF(event_name = 'ad_impression'), COUNTIF(event_name = 'ad_loaded')) * 100 AS show_rate_pct
FROM base
GROUP BY event_date, ad_format, placement
ORDER BY event_date, ad_format, placement;

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

