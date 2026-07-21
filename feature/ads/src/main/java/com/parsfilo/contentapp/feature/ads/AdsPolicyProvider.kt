package com.parsfilo.contentapp.feature.ads

import com.parsfilo.contentapp.core.firebase.config.RemoteConfigManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsPolicyProvider @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager,
) {
    init {
        remoteConfigManager.setDefaults(DEFAULTS)
    }

    fun getPolicy(): AdsPolicyConfig {
        val adsEmergencyDisabled =
            (
                remoteConfigManager.getBooleanOrNull(KEY_GLOBAL_EMERGENCY_DISABLED)
                    ?: DEFAULT_GLOBAL_EMERGENCY_DISABLED
            ) ||
                (
                    remoteConfigManager.getBooleanOrNull(KEY_EMERGENCY_DISABLED)
                        ?: DEFAULT_EMERGENCY_DISABLED
                )
        val interstitialFrequencyCapMs = sanitizeLong(
            remoteConfigManager.getLongOrNull(KEY_INTERSTITIAL_FREQUENCY_CAP_MS)
                ?: DEFAULT_INTERSTITIAL_FREQUENCY_CAP_MS,
            min = 60_000L,
            max = 60 * 60 * 1000L,
            fallback = DEFAULT_INTERSTITIAL_FREQUENCY_CAP_MS,
        )
        val appOpenCooldownMs = sanitizeLong(
            remoteConfigManager.getLongOrNull(KEY_APP_OPEN_COOLDOWN_MS)
                ?: DEFAULT_APP_OPEN_COOLDOWN_MS,
            min = 60_000L,
            max = 60 * 60 * 1000L,
            fallback = DEFAULT_APP_OPEN_COOLDOWN_MS,
        )
        val appOpenResumeGapMs = sanitizeLong(
            remoteConfigManager.getLongOrNull(KEY_APP_OPEN_RESUME_GAP_MS)
                ?: DEFAULT_APP_OPEN_RESUME_GAP_MS,
            min = 0L,
            max = 30 * 60 * 1000L,
            fallback = DEFAULT_APP_OPEN_RESUME_GAP_MS,
        )
        val appOpenMaxPerSession = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_APP_OPEN_MAX_PER_SESSION)
                    ?: DEFAULT_APP_OPEN_MAX_PER_SESSION.toLong()
            ).toInt(),
            min = 0,
            max = 10,
            fallback = DEFAULT_APP_OPEN_MAX_PER_SESSION,
        )
        val interstitialMaxPerSession = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_INTERSTITIAL_MAX_PER_SESSION)
                    ?: DEFAULT_INTERSTITIAL_MAX_PER_SESSION.toLong()
            ).toInt(),
            min = 0,
            max = 20,
            fallback = DEFAULT_INTERSTITIAL_MAX_PER_SESSION,
        )
        val interstitialRelaxedFrequencyCapMs = sanitizeLong(
            remoteConfigManager.getLongOrNull(KEY_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS)
                ?: DEFAULT_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS,
            min = 60_000L,
            max = 60 * 60 * 1000L,
            fallback = DEFAULT_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS,
        )
        val rewardedMaxPerSession = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_REWARDED_MAX_PER_SESSION)
                    ?: DEFAULT_REWARDED_MAX_PER_SESSION.toLong()
            ).toInt(),
            min = 0,
            max = 20,
            fallback = DEFAULT_REWARDED_MAX_PER_SESSION,
        )
        val rewardedInterstitialMinIntervalMs = sanitizeLong(
            remoteConfigManager.getLongOrNull(KEY_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS)
                ?: DEFAULT_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS,
            min = 0L,
            max = 24 * 60 * 60 * 1000L,
            fallback = DEFAULT_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS,
        )
        val rewardedInterstitialMaxPerSession = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_REWARDED_INTERSTITIAL_MAX_PER_SESSION)
                    ?: DEFAULT_REWARDED_INTERSTITIAL_MAX_PER_SESSION.toLong()
            ).toInt(),
            min = 0,
            max = 10,
            fallback = DEFAULT_REWARDED_INTERSTITIAL_MAX_PER_SESSION,
        )
        val rewardedInterstitialIntroRequired =
            remoteConfigManager.getBooleanOrNull(KEY_REWARDED_INTERSTITIAL_INTRO_REQUIRED)
                ?: DEFAULT_REWARDED_INTERSTITIAL_INTRO_REQUIRED
        val bannerEnabled =
            !adsEmergencyDisabled &&
                (
                    remoteConfigManager.getBooleanOrNull(KEY_BANNER_ENABLED)
                        ?: DEFAULT_BANNER_ENABLED
                )
        val appOpenEnabled =
            !adsEmergencyDisabled &&
                (
                    remoteConfigManager.getBooleanOrNull(KEY_APP_OPEN_ENABLED)
                        ?: DEFAULT_APP_OPEN_ENABLED
                )
        val interstitialEnabled =
            !adsEmergencyDisabled &&
                (
                    remoteConfigManager.getBooleanOrNull(KEY_INTERSTITIAL_ENABLED)
                        ?: DEFAULT_INTERSTITIAL_ENABLED
                )
        val nativeEnabled =
            !adsEmergencyDisabled &&
                (
                    remoteConfigManager.getBooleanOrNull(KEY_NATIVE_ENABLED)
                        ?: DEFAULT_NATIVE_ENABLED
                )
        val rewardedEnabled =
            !adsEmergencyDisabled &&
                (
                    remoteConfigManager.getBooleanOrNull(KEY_REWARDED_ENABLED)
                        ?: DEFAULT_REWARDED_ENABLED
                )
        val rewardedInterstitialEnabled =
            !adsEmergencyDisabled &&
                (
                    remoteConfigManager.getBooleanOrNull(KEY_REWARDED_INTERSTITIAL_ENABLED)
                        ?: DEFAULT_REWARDED_INTERSTITIAL_ENABLED
                )
        val nativeBannerFallbackEnabled =
            remoteConfigManager.getBooleanOrNull(KEY_NATIVE_BANNER_FALLBACK_ENABLED)
                ?: DEFAULT_NATIVE_BANNER_FALLBACK_ENABLED
        val interstitialPoolMax = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_INTERSTITIAL_POOL_MAX)
                    ?: DEFAULT_INTERSTITIAL_POOL_MAX.toLong()
            ).toInt(),
            min = 1,
            max = 3,
            fallback = DEFAULT_INTERSTITIAL_POOL_MAX,
        )
        val rewardedPoolMax = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_REWARDED_POOL_MAX)
                    ?: DEFAULT_REWARDED_POOL_MAX.toLong()
            ).toInt(),
            min = 1,
            max = 3,
            fallback = DEFAULT_REWARDED_POOL_MAX,
        )
        val nativePoolMax = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_NATIVE_POOL_MAX)
                    ?: DEFAULT_NATIVE_POOL_MAX.toLong()
            ).toInt(),
            min = 1,
            max = 4,
            fallback = DEFAULT_NATIVE_POOL_MAX,
        )
        val nativeTtlMs = sanitizeLong(
            remoteConfigManager.getLongOrNull(KEY_NATIVE_TTL_MS) ?: DEFAULT_NATIVE_TTL_MS,
            min = 5 * 60 * 1000L,
            max = 6 * 60 * 60 * 1000L,
            fallback = DEFAULT_NATIVE_TTL_MS,
        )
        val nativeExactPlacementOnly =
            remoteConfigManager.getBooleanOrNull(KEY_NATIVE_EXACT_PLACEMENT_ONLY)
                ?: DEFAULT_NATIVE_EXACT_PLACEMENT_ONLY
        val reportFreshnessMaxHours = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_REPORT_FRESHNESS_MAX_HOURS)
                    ?: DEFAULT_REPORT_FRESHNESS_MAX_HOURS.toLong()
            ).toInt(),
            min = 1,
            max = 168,
            fallback = DEFAULT_REPORT_FRESHNESS_MAX_HOURS,
        )
        val consentRetryBackoffMinutes = sanitizeInt(
            (
                remoteConfigManager.getLongOrNull(KEY_CONSENT_RETRY_BACKOFF_MINUTES)
                    ?: DEFAULT_CONSENT_RETRY_BACKOFF_MINUTES.toLong()
            ).toInt(),
            min = 1,
            max = 180,
            fallback = DEFAULT_CONSENT_RETRY_BACKOFF_MINUTES,
        )

        val bannerPlacementsDisabled = parsePlacementCsv(
            remoteConfigManager.getStringOrNull(KEY_BANNER_PLACEMENTS_DISABLED_CSV),
            format = AdFormat.BANNER,
        )
        val appOpenPlacementsDisabled = parsePlacementCsv(
            remoteConfigManager.getStringOrNull(KEY_APP_OPEN_PLACEMENTS_DISABLED_CSV),
            format = AdFormat.APP_OPEN,
        )
        val interstitialPlacementsDisabled = parsePlacementCsv(
            remoteConfigManager.getStringOrNull(KEY_INTERSTITIAL_PLACEMENTS_DISABLED_CSV),
            format = AdFormat.INTERSTITIAL,
        )
        val nativePlacementsDisabled = parsePlacementCsv(
            remoteConfigManager.getStringOrNull(KEY_NATIVE_PLACEMENTS_DISABLED_CSV),
            format = AdFormat.NATIVE,
        )
        val rewardedPlacementsDisabled = parsePlacementCsv(
            remoteConfigManager.getStringOrNull(KEY_REWARDED_PLACEMENTS_DISABLED_CSV),
            format = AdFormat.REWARDED,
        )
        val rewardedInterstitialPlacementsDisabled = parsePlacementCsv(
            remoteConfigManager.getStringOrNull(KEY_REWARDED_INTERSTITIAL_PLACEMENTS_DISABLED_CSV),
            format = AdFormat.REWARDED_INTERSTITIAL,
        )
        val appOpenRouteBlocklist = parseStringCsv(
            remoteConfigManager.getStringOrNull(KEY_APP_OPEN_ROUTE_BLOCKLIST_CSV),
        )
        val interstitialRouteBlocklist = parseStringCsv(
            remoteConfigManager.getStringOrNull(KEY_INTERSTITIAL_ROUTE_BLOCKLIST_CSV),
        )
        val interstitialRelaxedPackages = parsePackageCsv(
            remoteConfigManager.getStringOrNull(KEY_INTERSTITIAL_RELAXED_PACKAGES_CSV),
        )
        val interstitialAggressivePreloadPackages = parsePackageCsv(
            remoteConfigManager.getStringOrNull(KEY_INTERSTITIAL_AGGRESSIVE_PRELOAD_PACKAGES_CSV),
        )
        val appOpenAggressivePreloadPackages = parsePackageCsv(
            remoteConfigManager.getStringOrNull(KEY_APP_OPEN_AGGRESSIVE_PRELOAD_PACKAGES_CSV),
        )
        val rewardOfferRoutes = parseStringCsv(
            remoteConfigManager.getStringOrNull(KEY_REWARD_OFFER_ROUTES_CSV),
        )
        val interstitialHotRoutes = parseStringCsv(
            remoteConfigManager.getStringOrNull(KEY_INTERSTITIAL_HOT_ROUTES_CSV),
        )
        val interstitialNotLoadedRecoveryEnabled =
            remoteConfigManager.getBooleanOrNull(KEY_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED)
                ?: DEFAULT_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED
        val nativeBannerFallbackPackages = parsePackageCsv(
            remoteConfigManager.getStringOrNull(KEY_NATIVE_BANNER_FALLBACK_PACKAGES_CSV),
        )

        return AdsPolicyConfig(
            rewardedMaxPerSession = rewardedMaxPerSession,
            interstitialFrequencyCapMs = interstitialFrequencyCapMs,
            interstitialRelaxedFrequencyCapMs = interstitialRelaxedFrequencyCapMs,
            interstitialRelaxedPackages = interstitialRelaxedPackages,
            appOpenCooldownMs = appOpenCooldownMs,
            appOpenResumeGapMs = appOpenResumeGapMs,
            appOpenMaxPerSession = appOpenMaxPerSession,
            interstitialMaxPerSession = interstitialMaxPerSession,
            rewardedInterstitialMinIntervalMs = rewardedInterstitialMinIntervalMs,
            rewardedInterstitialMaxPerSession = rewardedInterstitialMaxPerSession,
            rewardedInterstitialIntroRequired = rewardedInterstitialIntroRequired,
            appOpenEnabled = appOpenEnabled,
            interstitialEnabled = interstitialEnabled,
            bannerEnabled = bannerEnabled,
            nativeEnabled = nativeEnabled,
            rewardedEnabled = rewardedEnabled,
            rewardedInterstitialEnabled = rewardedInterstitialEnabled,
            appOpenPlacementsDisabled = appOpenPlacementsDisabled,
            interstitialPlacementsDisabled = interstitialPlacementsDisabled,
            bannerPlacementsDisabled = bannerPlacementsDisabled,
            nativePlacementsDisabled = nativePlacementsDisabled,
            rewardedPlacementsDisabled = rewardedPlacementsDisabled,
            rewardedInterstitialPlacementsDisabled = rewardedInterstitialPlacementsDisabled,
            appOpenRouteBlocklist = appOpenRouteBlocklist,
            interstitialRouteBlocklist = interstitialRouteBlocklist,
            interstitialAggressivePreloadPackages = interstitialAggressivePreloadPackages,
            appOpenAggressivePreloadPackages = appOpenAggressivePreloadPackages,
            rewardOfferRoutes = rewardOfferRoutes,
            interstitialHotRoutes = interstitialHotRoutes,
            interstitialNotLoadedRecoveryEnabled = interstitialNotLoadedRecoveryEnabled,
            nativeBannerFallbackEnabled = nativeBannerFallbackEnabled,
            nativeBannerFallbackPackages = nativeBannerFallbackPackages,
            reportFreshnessMaxHours = reportFreshnessMaxHours,
            consentRetryBackoffMinutes = consentRetryBackoffMinutes,
            interstitialPoolMax = interstitialPoolMax,
            rewardedPoolMax = rewardedPoolMax,
            nativePoolMax = nativePoolMax,
            nativeTtlMs = nativeTtlMs,
            nativeExactPlacementOnly = nativeExactPlacementOnly,
        )
    }

    private fun parsePlacementCsv(value: String?, format: AdFormat): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        val disabled = linkedSetOf<String>()
        value.split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { raw ->
                val normalized = raw.lowercase()
                val placement = AdPlacement.entries.firstOrNull { candidate ->
                    candidate.format == format &&
                        (
                            candidate.analyticsValue.equals(normalized, ignoreCase = true) ||
                                candidate.name.equals(raw, ignoreCase = true) ||
                                candidate.resourceName.equals(raw, ignoreCase = true)
                        )
                }
                if (placement != null) {
                    disabled += placement.analyticsValue
                } else {
                    Timber.w("Unknown %s placement in RC CSV: %s", format.analyticsValue, raw)
                }
            }
        return disabled
    }

    private fun parsePackageCsv(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun parseStringCsv(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()
    }

    private fun sanitizeLong(value: Long, min: Long, max: Long, fallback: Long): Long =
        value.takeIf { it in min..max } ?: fallback

    private fun sanitizeInt(value: Int, min: Int, max: Int, fallback: Int): Int =
        value.takeIf { it in min..max } ?: fallback

    companion object {
        const val KEY_GLOBAL_EMERGENCY_DISABLED = "ads_global_emergency_disabled"
        const val KEY_EMERGENCY_DISABLED = "ads_emergency_disabled"
        const val KEY_BANNER_ENABLED = "ads_banner_enabled"
        const val KEY_NATIVE_ENABLED = "ads_native_enabled"
        const val KEY_APP_OPEN_ENABLED = "ads_app_open_enabled"
        const val KEY_INTERSTITIAL_ENABLED = "ads_interstitial_enabled"
        const val KEY_REWARDED_ENABLED = "ads_rewarded_enabled"
        const val KEY_REWARDED_INTERSTITIAL_ENABLED = "ads_rewarded_interstitial_enabled"
        const val KEY_INTERSTITIAL_FREQUENCY_CAP_MS = "ads_interstitial_frequency_cap_ms"
        const val KEY_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS =
            "ads_interstitial_relaxed_frequency_cap_ms"
        const val KEY_INTERSTITIAL_RELAXED_PACKAGES_CSV =
            "ads_interstitial_relaxed_packages_csv"
        const val KEY_INTERSTITIAL_AGGRESSIVE_PRELOAD_PACKAGES_CSV =
            "ads_interstitial_aggressive_preload_packages_csv"
        const val KEY_APP_OPEN_AGGRESSIVE_PRELOAD_PACKAGES_CSV =
            "ads_app_open_aggressive_preload_packages_csv"
        const val KEY_REWARD_OFFER_ROUTES_CSV = "ads_reward_offer_routes_csv"
        const val KEY_INTERSTITIAL_HOT_ROUTES_CSV = "ads_interstitial_hot_routes_csv"
        const val KEY_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED =
            "ads_interstitial_not_loaded_recovery_enabled"
        const val KEY_APP_OPEN_COOLDOWN_MS = "ads_app_open_cooldown_ms"
        const val KEY_APP_OPEN_RESUME_GAP_MS = "ads_app_open_resume_gap_ms"
        const val KEY_APP_OPEN_MAX_PER_SESSION = "ads_app_open_max_per_session"
        const val KEY_INTERSTITIAL_MAX_PER_SESSION = "ads_interstitial_max_per_session"
        const val KEY_REWARDED_MAX_PER_SESSION = "ads_rewarded_max_per_session"
        const val KEY_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS =
            "ads_rewarded_interstitial_min_interval_ms"
        const val KEY_REWARDED_INTERSTITIAL_MAX_PER_SESSION =
            "ads_rewarded_interstitial_max_per_session"
        const val KEY_REWARDED_INTERSTITIAL_INTRO_REQUIRED =
            "ads_rewarded_interstitial_intro_required"
        const val KEY_INTERSTITIAL_POOL_MAX = "ads_interstitial_pool_max"
        const val KEY_REWARDED_POOL_MAX = "ads_rewarded_pool_max"
        const val KEY_NATIVE_POOL_MAX = "ads_native_pool_max"
        const val KEY_NATIVE_TTL_MS = "ads_native_ttl_ms"
        const val KEY_NATIVE_EXACT_PLACEMENT_ONLY = "ads_native_exact_placement_only"
        const val KEY_NATIVE_BANNER_FALLBACK_ENABLED = "ads_native_banner_fallback_enabled"
        const val KEY_NATIVE_BANNER_FALLBACK_PACKAGES_CSV =
            "ads_native_banner_fallback_packages_csv"
        const val KEY_REPORT_FRESHNESS_MAX_HOURS = "ads_report_freshness_max_hours"
        const val KEY_CONSENT_RETRY_BACKOFF_MINUTES = "ads_consent_retry_backoff_minutes"
        const val KEY_INTERSTITIAL_PLACEMENTS_DISABLED_CSV = "ads_interstitial_placements_disabled_csv"
        const val KEY_BANNER_PLACEMENTS_DISABLED_CSV = "ads_banner_placements_disabled_csv"
        const val KEY_NATIVE_PLACEMENTS_DISABLED_CSV = "ads_native_placements_disabled_csv"
        const val KEY_APP_OPEN_PLACEMENTS_DISABLED_CSV = "ads_app_open_placements_disabled_csv"
        const val KEY_REWARDED_PLACEMENTS_DISABLED_CSV = "ads_rewarded_placements_disabled_csv"
        const val KEY_REWARDED_INTERSTITIAL_PLACEMENTS_DISABLED_CSV =
            "ads_rewarded_interstitial_placements_disabled_csv"
        const val KEY_APP_OPEN_ROUTE_BLOCKLIST_CSV = "ads_app_open_route_blocklist_csv"
        const val KEY_INTERSTITIAL_ROUTE_BLOCKLIST_CSV = "ads_interstitial_route_blocklist_csv"

        const val DEFAULT_GLOBAL_EMERGENCY_DISABLED = false
        const val DEFAULT_EMERGENCY_DISABLED = false
        const val DEFAULT_BANNER_ENABLED = true
        const val DEFAULT_NATIVE_ENABLED = true
        const val DEFAULT_APP_OPEN_ENABLED = true
        const val DEFAULT_INTERSTITIAL_ENABLED = true
        const val DEFAULT_REWARDED_ENABLED = true
        const val DEFAULT_REWARDED_INTERSTITIAL_ENABLED = true
        const val DEFAULT_INTERSTITIAL_FREQUENCY_CAP_MS = 60_000L
        const val DEFAULT_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS = 120_000L
        const val DEFAULT_INTERSTITIAL_AGGRESSIVE_PRELOAD_PACKAGES =
            "com.parsfilo.namazsurelerivedualarsesli,com.parsfilo.ismiazamduasi,com.parsfilo.mucizedualar,com.parsfilo.ayetelkursi,com.parsfilo.yasinsuresi,com.parsfilo.kenzularsduasi,com.parsfilo.insirahsuresi"
        const val DEFAULT_APP_OPEN_AGGRESSIVE_PRELOAD_PACKAGES =
            "com.parsfilo.namazsurelerivedualarsesli,com.parsfilo.ismiazamduasi,com.parsfilo.mucizedualar,com.parsfilo.ayetelkursi,com.parsfilo.yasinsuresi,com.parsfilo.kenzularsduasi,com.parsfilo.insirahsuresi"
        const val DEFAULT_REWARD_OFFER_ROUTES =
            "home,content,prayer_list,prayer_detail,quran_sura_list,quran_sura_detail,counter,settings"
        const val DEFAULT_INTERSTITIAL_HOT_ROUTES =
            "content,prayer_list,prayer_detail,miracles_list,miracles_detail"
        const val DEFAULT_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED = true
        const val DEFAULT_APP_OPEN_COOLDOWN_MS = 90_000L
        const val DEFAULT_APP_OPEN_RESUME_GAP_MS = 15_000L
        const val DEFAULT_APP_OPEN_MAX_PER_SESSION = 3
        const val DEFAULT_INTERSTITIAL_MAX_PER_SESSION = 6
        const val DEFAULT_REWARDED_MAX_PER_SESSION = 10
        const val DEFAULT_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS = 900_000L
        const val DEFAULT_REWARDED_INTERSTITIAL_MAX_PER_SESSION = 2
        const val DEFAULT_REWARDED_INTERSTITIAL_INTRO_REQUIRED = true
        const val DEFAULT_INTERSTITIAL_POOL_MAX = 2
        const val DEFAULT_REWARDED_POOL_MAX = 2
        const val DEFAULT_NATIVE_POOL_MAX = 2
        const val DEFAULT_NATIVE_TTL_MS = 1_800_000L
        const val DEFAULT_NATIVE_EXACT_PLACEMENT_ONLY = false
        const val DEFAULT_NATIVE_BANNER_FALLBACK_ENABLED = false
        const val DEFAULT_NATIVE_BANNER_FALLBACK_PACKAGES =
            "com.parsfilo.namazsurelerivedualarsesli,com.parsfilo.mucizedualar,com.parsfilo.yasinsuresi"
        const val DEFAULT_REPORT_FRESHNESS_MAX_HOURS = 24
        const val DEFAULT_CONSENT_RETRY_BACKOFF_MINUTES = 30
        const val DEFAULT_APP_OPEN_ROUTE_BLOCKLIST =
            "subscription,rewards,settings"
        const val DEFAULT_INTERSTITIAL_ROUTE_BLOCKLIST = "subscription,rewards,settings"

        private val DEFAULTS = mapOf<String, Any>(
            KEY_GLOBAL_EMERGENCY_DISABLED to DEFAULT_GLOBAL_EMERGENCY_DISABLED,
            KEY_EMERGENCY_DISABLED to DEFAULT_EMERGENCY_DISABLED,
            KEY_BANNER_ENABLED to DEFAULT_BANNER_ENABLED,
            KEY_NATIVE_ENABLED to DEFAULT_NATIVE_ENABLED,
            KEY_APP_OPEN_ENABLED to DEFAULT_APP_OPEN_ENABLED,
            KEY_INTERSTITIAL_ENABLED to DEFAULT_INTERSTITIAL_ENABLED,
            KEY_REWARDED_ENABLED to DEFAULT_REWARDED_ENABLED,
            KEY_REWARDED_INTERSTITIAL_ENABLED to DEFAULT_REWARDED_INTERSTITIAL_ENABLED,
            KEY_INTERSTITIAL_FREQUENCY_CAP_MS to DEFAULT_INTERSTITIAL_FREQUENCY_CAP_MS,
            KEY_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS to DEFAULT_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS,
            KEY_INTERSTITIAL_RELAXED_PACKAGES_CSV to "",
            KEY_INTERSTITIAL_AGGRESSIVE_PRELOAD_PACKAGES_CSV to
                DEFAULT_INTERSTITIAL_AGGRESSIVE_PRELOAD_PACKAGES,
            KEY_APP_OPEN_AGGRESSIVE_PRELOAD_PACKAGES_CSV to
                DEFAULT_APP_OPEN_AGGRESSIVE_PRELOAD_PACKAGES,
            KEY_REWARD_OFFER_ROUTES_CSV to DEFAULT_REWARD_OFFER_ROUTES,
            KEY_INTERSTITIAL_HOT_ROUTES_CSV to DEFAULT_INTERSTITIAL_HOT_ROUTES,
            KEY_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED to
                DEFAULT_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED,
            KEY_APP_OPEN_COOLDOWN_MS to DEFAULT_APP_OPEN_COOLDOWN_MS,
            KEY_APP_OPEN_RESUME_GAP_MS to DEFAULT_APP_OPEN_RESUME_GAP_MS,
            KEY_APP_OPEN_MAX_PER_SESSION to DEFAULT_APP_OPEN_MAX_PER_SESSION.toLong(),
            KEY_INTERSTITIAL_MAX_PER_SESSION to DEFAULT_INTERSTITIAL_MAX_PER_SESSION.toLong(),
            KEY_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS to DEFAULT_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS,
            KEY_REWARDED_MAX_PER_SESSION to DEFAULT_REWARDED_MAX_PER_SESSION.toLong(),
            KEY_REWARDED_INTERSTITIAL_MAX_PER_SESSION to DEFAULT_REWARDED_INTERSTITIAL_MAX_PER_SESSION.toLong(),
            KEY_REWARDED_INTERSTITIAL_INTRO_REQUIRED to DEFAULT_REWARDED_INTERSTITIAL_INTRO_REQUIRED,
            KEY_INTERSTITIAL_POOL_MAX to DEFAULT_INTERSTITIAL_POOL_MAX.toLong(),
            KEY_REWARDED_POOL_MAX to DEFAULT_REWARDED_POOL_MAX.toLong(),
            KEY_NATIVE_POOL_MAX to DEFAULT_NATIVE_POOL_MAX.toLong(),
            KEY_NATIVE_TTL_MS to DEFAULT_NATIVE_TTL_MS,
            KEY_NATIVE_EXACT_PLACEMENT_ONLY to DEFAULT_NATIVE_EXACT_PLACEMENT_ONLY,
            KEY_NATIVE_BANNER_FALLBACK_ENABLED to DEFAULT_NATIVE_BANNER_FALLBACK_ENABLED,
            KEY_NATIVE_BANNER_FALLBACK_PACKAGES_CSV to DEFAULT_NATIVE_BANNER_FALLBACK_PACKAGES,
            KEY_REPORT_FRESHNESS_MAX_HOURS to DEFAULT_REPORT_FRESHNESS_MAX_HOURS.toLong(),
            KEY_CONSENT_RETRY_BACKOFF_MINUTES to DEFAULT_CONSENT_RETRY_BACKOFF_MINUTES.toLong(),
            KEY_INTERSTITIAL_PLACEMENTS_DISABLED_CSV to "",
            KEY_BANNER_PLACEMENTS_DISABLED_CSV to "",
            KEY_NATIVE_PLACEMENTS_DISABLED_CSV to "",
            KEY_APP_OPEN_PLACEMENTS_DISABLED_CSV to "",
            KEY_REWARDED_PLACEMENTS_DISABLED_CSV to "",
            KEY_REWARDED_INTERSTITIAL_PLACEMENTS_DISABLED_CSV to "",
            KEY_APP_OPEN_ROUTE_BLOCKLIST_CSV to DEFAULT_APP_OPEN_ROUTE_BLOCKLIST,
            KEY_INTERSTITIAL_ROUTE_BLOCKLIST_CSV to DEFAULT_INTERSTITIAL_ROUTE_BLOCKLIST,
        )
    }
}
