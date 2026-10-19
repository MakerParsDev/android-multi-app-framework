# GA4 → BigQuery export recovery: cost and verification (#401)

**Last reviewed:** 2026-10-19. Target Google Cloud project:
`makerpars-oaslananka-mobil`; GA4 property `526790653`, Android
İsmi Azam stream `13698218874`, dataset `analytics_526790653`
in `europe-west1`.

## Actual failure, not a missing Analytics SDK

GA4 Data API still receives app events. The GA4 Admin daily BigQuery
export link selects this stream, but the dataset lacks *every*
`events_YYYYMMDD` table (the only observed table was a
`pseudonymous_users_20260916` snapshot).
Cloud Audit logs show the actual exporter principal
`firebase-measurement@system.gserviceaccount.com` receiving repeated
`RESOURCE_EXHAUSTED` failures (18–19 Oct), specifically:
`Quota exceeded: Your project exceeded quota for free storage for projects`.
The service account already has dataset-owner/project-user access.

This project has **Cloud Billing disabled**. Of three billing accounts
visible to the authenticated CLI, **zero are open**; the owner must establish
an eligible, active billing account before upgrading an existing project.
Do **not** modify credentials, broaden service-account IAM, delete Firebase
datasets, or create fresh temporary sandbox projects to circumvent quotas.

Official BigQuery Sandbox rules: lifetime **10 GiB stored** (deletions do
**not** refund that quota), and a 60-day table expiry:
<https://docs.cloud.google.com/bigquery/docs/sandbox#limitations>.

## Read-only recovery check

On a machine with an authorized `bq` CLI login and dataset read access:

```bash
python3 scripts/ci/check_ga4_bigquery_export.py \
  --project makerpars-oaslananka-mobil \
  --dataset analytics_526790653 \
  --as-of-date 2026-10-19
```

The script issues **only** `bq ls --format=json` (metadata, no BigQuery
data scan). It checks **three consecutive mature days** ending two
calendar days before the supplied date. The two-day offset avoids
misdiagnosing an ordinary next-day delay. Exit codes:

- `0`: daily tables for the 3 mature dates are **present**, **not**
  evidence of complete row content or stream coverage.
- `2`: one or more required tables are **missing**.
- `1`: bq CLI/permissions/response/timeout/validation error;
  **never** report this as "missing tables".

To check a new day, omit `--as-of-date` (uses the local machine's date,
so ensure it matches the GA4 property's reporting calendar). The script
does not fix quota and should not be scheduled as a write-capable job.

**Once storage is restored**, require three *post-remediation*
consecutive completed daily tables **and** verify representative rows
and params for İsmi Azam. A table name alone is insufficient:

```sql
-- First use the BigQuery UI dry run to confirm bytes scanned;
-- restrict actual query jobs with maximum_bytes_billed.
SELECT
  stream_id,
  app_info.version AS app_version,
  event_name,
  COUNT(*) AS event_count
FROM `makerpars-oaslananka-mobil.analytics_526790653.events_YYYYMMDD`
WHERE stream_id = '13698218874'
  AND event_name IN ('ad_placement_impression', 'ad_paid_event', 'screen_view')
GROUP BY 1, 2, 3
LIMIT 50;
```

Replace `YYYYMMDD` with an existing fully processed day. Verify
`event_params` values for `ad_placement`, `ad_format`, `consent_state`
and `app_version`; keep any user IDs/advertising IDs out of reports.
GA4 daily tables can continue accepting late arrivals for three days:
<https://support.google.com/analytics/answer/7029846>.
Missed exports may not be backfilled:
<https://support.google.com/analytics/answer/9823238>.

## Cost planning *before* enabling Cloud Billing

Authenticated GA4 daily report for **12–18 October 2026** measured
approximately **47,727 events/day** across the linked property; about
**1.43 million events per 30-day month** if traffic stays constant.
Google's GA4 guidance uses *about* 600,000 events per GB for streaming
data as a rough example, which suggests **~2.4 GB/30 days**. This
is a **planning proxy**, not measured BigQuery logical bytes or a quote.
Actual storage varies with nested event parameters, compression,
retention, app traffic growth and other project datasets.

For an upgraded (billed) project, Google's *monthly free tier* includes
the first **10 GiB of active storage and 1 TiB of processed queries**;
this differs from the unbilled Sandbox's *lifetime* quota:
<https://cloud.google.com/bigquery/pricing>. A paid account can still
incur costs from **BigQuery and other GCP services** even when the
Analytics estimate is below the free allowance.

Before enabling billing, require:

1. Owner-approved monthly cost target and an active, eligible Cloud
   Billing account; no account was open as of the audit.
2. **Alert-only** project-scoped Billing budget notifications at 50%,
   80% and 100%; they are **not** hard spending limits.
   The newer "spend cap budgets" currently **do not support BigQuery**
   (eligible services are Gemini API, Vertex AI, Cloud Run and Cloud
   Functions):
   <https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps#eligible-services>.
3. BigQuery query-cost guards: `maximum_bytes_billed`, narrow date
   partitions, on-demand query usage quotas, least-privilege accounts;
   they do not cap background storage or charges from other GCP
   services. Review the full project's existing workloads.
4. Explicit, privacy-reviewed table-expiration duration. The GA4 UI
   retains event data for **2 months**, while the repository documents
   **14 months as a desired policy**, not an applied setting. Do not
   silently extend event retention or copy identifier-rich raw data.
5. Assess whether **mobile advertising IDs included in the linked
   export** are necessary; minimize data and access rights before
   storing long-lived raw analytics.

Never advertise any budget alert as a binding cost ceiling. No automatic
Cloud Billing, GA4 Admin, AdMob, Firebase Remote Config, production,
or raw-data modifications are part of this read-only acceptance tool.
