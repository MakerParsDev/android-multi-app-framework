# AdMob Weekly Optimization

Generated: `2026-07-13T00:00:00+03:00`
Current week: **2026-07-06 – 2026-07-12**
Previous week: **2026-06-29 – 2026-07-05**

## Portfolio trend

| Metric | Current | Previous | Change |
|---|---:|---:|---:|
| Earnings (TRY) | 318.32 | 320.14 | -0.6% |
| Requests | 35424 | 34578 | +2.5% |
| Impressions | 5662 | 5640 | +0.4% |
| CTR | 6.3% | 5.5% | +14.4% |
| Match rate | 81.6% | 79.2% | +3.0% |
| Show rate | 19.6% | 20.6% | -4.8% |

## Format trend

| Format | Requests | Match | Show | CTR | Earnings TRY | WoW earnings | eCPM TRY |
|---|---:|---:|---:|---:|---:|---:|---:|
| interstitial | 11214 | 78.7% | 8.6% | 19.9% | 137.37 | -4.2% | 181.23 |
| banner | 5109 | 82.1% | 78.9% | 3.1% | 90.62 | +4.2% | 27.37 |
| app_open | 5261 | 93.9% | 7.1% | 18.5% | 61.47 | -6.8% | 175.13 |
| native | 13446 | 78.5% | 11.6% | 3.0% | 22.07 | +1.8% | 17.99 |
| rewarded | 394 | 93.4% | 4.1% | 13.3% | 6.79 | +222.6% | 452.70 |

## Flavor, version and ad-unit trend

AdMob's network report exposes the ad unit rather than the in-app placement alias. The scheduled live pipeline maps AdMob app IDs to framework flavors and queries app version/platform directly. The source-controlled bootstrap fixture is intentionally portfolio-aggregated, so those rows are marked `unmapped`/`mixed`. The admin runtime report exposes exact placement conversion in `runtimeFunnelByPlacement` after rollout.

| Flavor | App | Format | Platform | Version | Ad unit | Match | Show | CTR | eCPM TRY | Earnings TRY | WoW earnings |
|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|
| unmapped | portfolio | interstitial | ANDROID | mixed | all | 78.7% | 8.6% | 19.9% | 181.23 | 137.37 | -4.2% |
| unmapped | portfolio | banner | ANDROID | mixed | all | 82.1% | 78.9% | 3.1% | 27.37 | 90.62 | +4.2% |
| unmapped | portfolio | app_open | ANDROID | mixed | all | 93.9% | 7.1% | 18.5% | 175.13 | 61.47 | -6.8% |
| unmapped | portfolio | native | ANDROID | mixed | all | 78.5% | 11.6% | 3.0% | 17.99 | 22.07 | +1.8% |
| unmapped | portfolio | rewarded | ANDROID | mixed | all | 93.4% | 4.1% | 13.3% | 452.70 | 6.79 | +222.6% |

## Highest-priority app/format opportunities

- `unmapped` / `portfolio` / `rewarded` / `mixed` / `all`: low_show_rate; matched=368, show=4.1%.
- `unmapped` / `portfolio` / `app_open` / `mixed` / `all`: low_show_rate; matched=4940, show=7.1%.
- `unmapped` / `portfolio` / `interstitial` / `mixed` / `all`: low_show_rate; matched=8825, show=8.6%.
- `unmapped` / `portfolio` / `native` / `mixed` / `all`: low_show_rate; matched=10560, show=11.6%.

## Country opportunities

- `MY` / `native`: zero_impressions, show_rate_decline; requests=155, match=100.0%, show=0.0%.
- `MY` / `interstitial`: zero_impressions; requests=104, match=100.0%, show=0.0%.
- `MY` / `banner`: show_rate_decline; requests=1, match=100.0%, show=0.0%.
- `RU` / `app_open`: low_match_rate; requests=290, match=0.0%, show=0.0%.
- `RU` / `banner`: low_match_rate; requests=146, match=0.0%, show=0.0%.
- `RU` / `interstitial`: low_match_rate; requests=1272, match=0.0%, show=0.0%.
- `RU` / `native`: low_match_rate; requests=792, match=0.0%, show=0.0%.
- `TR` / `app_open`: low_show_rate; requests=3392, match=99.6%, show=7.2%.
- `TR` / `rewarded`: low_show_rate; requests=127, match=96.1%, show=7.4%.
- `TR` / `interstitial`: low_show_rate; requests=6917, match=92.4%, show=8.7%.
- `TR` / `native`: low_show_rate; requests=8536, match=92.2%, show=11.9%.

## Bounded experiments

### `native_visibility_recovery`

Native matched-to-impression conversion is below 20%.

- Change: Audit only placements with >=100 matched requests and low visibility; fix composition/lifecycle attachment without increasing request frequency.
- Success: Native show rate improves >=20% relative while CTR, crashes and session retention do not regress.
- Rollback: Disable native fallback and restore pool size when show rate, CTR quality or runtime health regresses.
- Bounds: `{"ads_native_banner_fallback_enabled": [false, true], "ads_native_pool_max": [1, 2], "frequency_increase_allowed": false}`

### `fullscreen_readiness_recovery`

High-match app-open/interstitial inventory converts to impressions below 15%.

- Change: Measure lifecycle/activity/not-loaded suppression and improve preload readiness; do not reduce cooldowns or raise session caps.
- Success: Show rate improves >=15% relative with unchanged caps and no ANR/crash/retention regression.
- Rollback: Use app/format emergency switches when suppression, UX or release-health signals worsen.
- Bounds: `{"ads_app_open_cooldown_ms_min": 120000, "ads_interstitial_frequency_cap_ms_min": 90000, "session_cap_increase_allowed": false}`

### `rewarded_opt_in_discovery`

Rewarded inventory has high match but low opt-in impression conversion.

- Change: Test clearer user-initiated reward offers only on configured reward routes; never auto-show rewarded formats.
- Success: Rewarded impressions and completion rise without increasing skipped offers or complaints.
- Rollback: Remove experiment routes and retain current opt-in behavior.
- Bounds: `{"ads_reward_offer_routes_csv": "allowlist_only", "ads_rewarded_max_per_session_max": 10, "auto_show_allowed": false}`

### `geo_no_fill_diagnosis`

One or more countries have >=500 requests with sub-50% match rate.

- Change: Investigate demand/mediation and consent coverage by country; do not increase request volume in no-fill markets.
- Success: Match rate improves without higher request counts or policy exceptions.
- Rollback: Revert country-specific changes and keep global safe defaults.
- Bounds: `{"country_targeting_requires_review": true, "request_frequency_increase_allowed": false}`

### `placement_canary_only`

App/format outliers exist and portfolio-wide changes would hide placement-specific regressions.

- Change: Run one-package, one-placement canaries for seven days with explicit owner and previous template version.
- Success: Canary meets show-rate/revenue target with stable UX and release health before expansion.
- Rollback: Restore the previous Remote Config template immediately on guardrail breach.
- Bounds: `{"max_apps_per_canary": 1, "max_placements_per_canary": 1, "minimum_observation_days": 7}`
