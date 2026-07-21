# Ads Incident Runbook

## Purpose
Use this runbook when there is a privacy, policy, invalid traffic, or UX incident and ad behavior must be reduced immediately without shipping a new build.

## OPS-CRITICAL Immediate Actions
1. Set `ads_incident_mode=true`.
2. Disable high-risk formats first:
   - `ads_app_open_enabled=false`
   - `ads_interstitial_enabled=false`
   - `ads_rewarded_interstitial_enabled=false`
3. Keep only low-risk formats if needed:
   - `ads_banner_enabled=true`
   - `ads_native_enabled=true`
4. Publish Remote Config and force a fetch on a test device.

## COMPLIANCE-CRITICAL Privacy Incident
1. Confirm runtime privacy state is not `CanRequestAds` for revoked users.
2. Confirm preloaded ads are cleared after revoke.
3. Confirm `Privacy Options` remains visible.

## UX-CRITICAL App Open / Interstitial Incident
1. Increase:
   - `ads_app_open_resume_gap_ms`
   - `ads_app_open_cooldown_ms`
   - `ads_interstitial_frequency_cap_ms`
2. Lower:
   - `ads_app_open_max_per_session`
   - `ads_interstitial_max_per_session`
3. Add emergency routes to:
   - `ads_app_open_route_blocklist_csv`
   - `ads_interstitial_route_blocklist_csv`

## REVENUE-CRITICAL Triage Data
- Review request -> loaded -> impression -> paid funnel.
- Break down by:
  - flavor
  - format
  - placement
  - route
  - consent state
  - premium state
  - suppressed reason

## Rollback
- Restore previous Remote Config values after confirming the incident is resolved.
- Remove temporary route blocklist entries last.
