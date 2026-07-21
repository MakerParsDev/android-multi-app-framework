# Ads Remote Config Profiles

## Aggressive Revenue Profile

Source file: `config/remote-config/ads-aggressive-profile.json`

Use these values in Firebase Remote Config for aggressive monetization.

### Parameters

| Key | Value |
|---|---|
| `ads_app_open_enabled` | `true` |
| `ads_interstitial_enabled` | `true` |
| `ads_banner_enabled` | `true` |
| `ads_native_enabled` | `true` |
| `ads_rewarded_enabled` | `true` |
| `ads_rewarded_interstitial_enabled` | `true` |
| `ads_app_open_cooldown_ms` | `180000` |
| `ads_interstitial_frequency_cap_ms` | `120000` |
| `ads_interstitial_relaxed_frequency_cap_ms` | `90000` |
| `ads_interstitial_relaxed_packages_csv` | `` (empty) |
| `ads_rewarded_interstitial_min_interval_ms` | `600000` |
| `ads_rewarded_interstitial_max_per_session` | `3` |
| `ads_native_pool_max` | `3` |
| `ads_native_ttl_ms` | `1800000` |
| `ads_app_open_placements_disabled_csv` | `` (empty) |
| `ads_interstitial_placements_disabled_csv` | `` (empty) |
| `ads_banner_placements_disabled_csv` | `` (empty) |
| `ads_native_placements_disabled_csv` | `` (empty) |
| `ads_rewarded_placements_disabled_csv` | `` (empty) |
| `ads_rewarded_interstitial_placements_disabled_csv` | `` (empty) |

### Rollout

1. Apply global defaults with this profile.
2. Add app/flavor condition overrides only where UX metrics degrade.
3. Monitor for 7-14 days:
   - `ARPDAU`
   - ad requests / impressions
   - interstitial show rate
   - app open impressions / DAU
   - retention D1

### Fast rollback

If UX degrades, immediately set:

- `ads_banner_enabled=false`
- `ads_native_enabled=false`
- `ads_interstitial_frequency_cap_ms=180000` or `240000`
- `ads_app_open_cooldown_ms=240000`
- add problematic placements to the corresponding `*_placements_disabled_csv`

