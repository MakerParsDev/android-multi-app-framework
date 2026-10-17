# Request-efficiency experiment (issue #372)

## Guardrails

The treatment is **OFF by default**. Only a reviewed, controlled Firebase
Remote Config experiment may turn on
`ads_demand_aware_preload_enabled=true` for an explicitly selected app/user
cohort. The existing production template does **not** contain this new key;
deploying this code does not activate request-limiting/preload changes by itself.

Both cohorts retain UMP consent, age-gate, premium/rewarded-free, route,
user-action, native attribution, AdMob ad unit ID, and per-format frequency
checks. A common automatic full-screen coordinator prevents concurrent
app-open/interstitial ads and enforces at least 30 seconds from the last
automatic full-screen impression; this safety change applies to both cohorts.
Rewarded formats are user initiated and retain their existing reward gates.

## Control vs treatment

| Behavior | Control (false, default) | Treatment (true) |
| --- | --- | --- |
| Native | Initialize pool on app bootstrap; refill after pool consumption | Wait for composed native slot, request one, no automatic refill |
| Interstitial | Prefetch up to pool max 2 before any impression | Second-slot prefetch only after first impression in process |
| App-open | Existing preload on bootstrap/pause/route hints | Existing preload on bootstrap/pause/route hints |
| Native/interstitial/app-open requests | Existing format-specific backoff/deduplication | Same plus best-effort 12 optional requests per rolling 60s across the three formats |
| Full-screen concurrency | Shared automatic format spacing (30s) | Same |

For a composed native slot with no loaded ad, the treatment retries at
most twice at 60-second intervals while the slot and consent remain valid.
SDK deduplication and no-fill backoff still apply.

The 12-per-minute ceiling is a product-side protection against bursts,
**not** an official Google AdMob request quota. It never relaxes server-side
serving restrictions and intentionally does not throttle rewarded requests
or SDK-controlled banner refreshes. No client-side bound guarantees account
policy approval.

## Local physical-device treatment smoke test

The **debug-only** Gradle project property
`ADS_DEMAND_AWARE_DEBUG_OVERRIDE=true` allows a developer's own debug APK
to exercise the treatment against **Google test ad units**, without publishing
any Firebase Remote Config condition. The default is false; invalid strings
fail configuration and the **release BuildConfig is always false**, regardless
of Gradle input.

For example, use
`./gradlew :app:assembleIsmiazamduasiDebug -PADS_DEMAND_AWARE_DEBUG_OVERRIDE=true`
on a private build agent with real, ignored flavor Firebase configuration.
Install only on a consenting, authorized device that has **no existing
production installation of that package**, so production state is preserved.
Confirm the debug UI displays "Test Reklamı" and native loads occur after a
composed slot. This local override is not evidence of revenue improvement.

## Native inventory loss diagnostics

The new privacy-safe `ad_inventory_discarded` GA4 event records only native
inventory **loaded into the manager but discarded before UI handoff**. The
registered event-scoped custom dimension `ad_discard_reason` is one of
`ttl_expired`, `pool_full`, `gate_closed`, or `reset`. It is paired with
existing bounded `ad_format` / `ad_placement` / app version fields; no
per-user or per-request correlation token is added. Native ads handed to the
UI may remain unviewed, so do not equate `ad_served` with an AdMob
impression. Compare aggregated request, loaded, discarded, served and
impression events for the **same app/version/date/cohort**; allow for
Analytics reporting delay and consent-dependent data omission.

## Experiment and analysis

1. **Before enabling any treatment cohort:** release the verified code to
   a controlled internal/test track and complete user-flow, low-memory,
   navigation/recomposition, UMP, premium, and rewarded-path smoke tests.
2. Publish a reviewed Firebase Remote Config boolean parameter with
   default false, assign **at most 5%** of an individual approved flavor to
   treatment true, retain a contemporaneous 95% control. Use Firebase
   A/B Testing where supported; record experiment variant, app version,
   country and consent state using permitted privacy-safe analytics.
   Do not enable across all 17 apps simultaneously.
3. Compare cohorts over at least 7 days with same country/flavor/version
   and enough active users: ads per engaged user, matched/requested ratio,
   matched-to-impression ratio, actual **impressions**, publisher net
   revenue and ARPDAU, session duration, D1/D7 retention, crashes/ANRs,
   consent form errors and ad-serving enforcement. Use only aggregated
   non-PII data; do not equate GA4 event counts with AdMob network request
   counts without reconciliation.
4. **Rollback immediately:** set the treatment condition back to false if
   crashes/ANRs, UMP consent behavior, retention, no-fill, revenue or
   platform-policy indicators regress. Do not loosen frequency limits to
   compensate for request throttling without measurement.
5. Keep issue #372 OPEN until a measurable cohort comparison and
   per-screen attribution are complete. Issue #337 retains UMP/no-fill
   root-cause analysis; issue #365 retains v57 production readiness.

The placement aliases currently point to the same six per-format IDs in
each flavor. GA4 placement tagging exists, but true AdMob per-placement
reporting requires separate *reviewed* AdMob units and an explicit future
mapping change. Do not create extra live units automatically.
