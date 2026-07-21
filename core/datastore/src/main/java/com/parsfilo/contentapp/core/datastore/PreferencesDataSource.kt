package com.parsfilo.contentapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

class PreferencesDataSource @Inject constructor(
    private val userPreferences: DataStore<Preferences>
) {
    val otherAppsBadgeSeenSignature: Flow<String> = userPreferences.data.map { preferences ->
        preferences[PreferencesKeys.OTHER_APPS_BADGE_SEEN_SIGNATURE] ?: ""
    }

    val zikirHapticEnabled: Flow<Boolean> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_HAPTIC] ?: true
    }

    val zikirSoundEnabled: Flow<Boolean> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_SOUND] ?: false
    }

    val lastSelectedZikirKey: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.LAST_ZIKIR_KEY] ?: "subhanallah"
    }

    val zikirReminderEnabled: Flow<Boolean> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_REMINDER_ENABLED] ?: false
    }

    val zikirReminderHour: Flow<Int> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_REMINDER_HOUR] ?: 9
    }

    val zikirReminderMinute: Flow<Int> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_REMINDER_MINUTE] ?: 0
    }

    val zikirDailyGoal: Flow<Int> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_DAILY_GOAL] ?: 100
    }

    val adsAgeGateStatus: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.ADS_AGE_GATE_STATUS] ?: "UNKNOWN"
    }

    val adsConsentStatus: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.ADS_CONSENT_STATUS] ?: "unknown"
    }

    val adsConsentUpdatedAt: Flow<Long> = userPreferences.data.map {
        it[PreferencesKeys.ADS_CONSENT_UPDATED_AT] ?: 0L
    }

    val adsLastSuccessfulConsentStatus: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.ADS_LAST_SUCCESSFUL_CONSENT_STATUS] ?: "unknown"
    }

    val adsLastSuccessfulConsentUpdatedAt: Flow<Long> = userPreferences.data.map {
        it[PreferencesKeys.ADS_LAST_SUCCESSFUL_CONSENT_UPDATED_AT] ?: 0L
    }

    val adsAgeGatePromptCompleted: Flow<Boolean> = userPreferences.data.map {
        it[PreferencesKeys.ADS_AGE_GATE_PROMPT_COMPLETED] ?: false
    }

    val zikirStreakReminderEnabled: Flow<Boolean> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_STREAK_REMINDER] ?: true
    }

    val zikirLastInterstitialAt: Flow<Long> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_LAST_INTERSTITIAL_AT] ?: 0L
    }

    val zikirInterstitialShownCountDay: Flow<Int> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_INTERSTITIAL_SHOWN_COUNT_DAY] ?: 0
    }

    val zikirInterstitialDayKey: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.ZIKIR_INTERSTITIAL_DAY_KEY] ?: ""
    }

    val customZikirItemsJson: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.CUSTOM_ZIKIR_ITEMS_JSON] ?: "[]"
    }

    val deletedZikirKeysJson: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.DELETED_ZIKIR_KEYS_JSON] ?: "[]"
    }

    val quranSelectedReciter: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.QURAN_SELECTED_RECITER] ?: "alafasy_128"
    }

    val quranDisplayMode: Flow<String> = userPreferences.data.map {
        it[PreferencesKeys.QURAN_DISPLAY_MODE] ?: "TRANSLATION"
    }

    val quranFontSize: Flow<Int> = userPreferences.data.map {
        it[PreferencesKeys.QURAN_FONT_SIZE] ?: 28
    }

    val userData: Flow<UserPreferencesData> = userPreferences.data.map { preferences ->
        UserPreferencesData(
            darkMode = preferences[PreferencesKeys.DARK_MODE] ?: false,
            displayMode = preferences[PreferencesKeys.DISPLAY_MODE] ?: "ARABIC",
            fontSize = preferences[PreferencesKeys.FONT_SIZE] ?: 20,
            developerModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false,
            isPremium = preferences[PreferencesKeys.IS_PREMIUM] ?: false,
            lastAppOpenAdShown = preferences[PreferencesKeys.LAST_APP_OPEN_AD] ?: 0L,
            rewardedAdFreeUntil = preferences[PreferencesKeys.REWARDED_AD_FREE_UNTIL] ?: 0L,
            rewardWatchCount = preferences[PreferencesKeys.REWARD_WATCH_COUNT] ?: 0,
            lastInterstitialShown = preferences[PreferencesKeys.LAST_INTERSTITIAL_SHOWN] ?: 0L,
            lastRewardedInterstitialShown = preferences[PreferencesKeys.LAST_REWARDED_INTERSTITIAL_SHOWN] ?: 0L,
            notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
            notificationPermissionPrompted = preferences[PreferencesKeys.NOTIFICATION_PERMISSION_PROMPTED] ?: false,
            installationId = preferences[PreferencesKeys.INSTALLATION_ID] ?: "",
            lastPushSyncAt = preferences[PreferencesKeys.LAST_PUSH_SYNC_AT] ?: 0L,
            lastPushToken = preferences[PreferencesKeys.LAST_PUSH_TOKEN] ?: "",
            lastPushSyncAttemptAt = preferences[PreferencesKeys.LAST_PUSH_SYNC_ATTEMPT_AT] ?: 0L,
            lastPushSyncSuccessAt = preferences[PreferencesKeys.LAST_PUSH_SYNC_SUCCESS_AT] ?: 0L,
            lastPushSyncFailureReason =
                preferences[PreferencesKeys.LAST_PUSH_SYNC_FAILURE_REASON] ?: "",
            lastPushTokenHash = preferences[PreferencesKeys.LAST_PUSH_TOKEN_HASH] ?: "",
            hasPushToken = preferences[PreferencesKeys.HAS_PUSH_TOKEN] ?: false,
            adsAgeGateStatus = preferences[PreferencesKeys.ADS_AGE_GATE_STATUS] ?: "UNKNOWN",
            adsAgeGatePromptCompleted = preferences[PreferencesKeys.ADS_AGE_GATE_PROMPT_COMPLETED] ?: false,
            adRuntimeTelemetry = readAdRuntimeTelemetrySnapshot(preferences),
        )
    }

    suspend fun setDarkMode(darkMode: Boolean) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = darkMode
        }
    }

    suspend fun setDisplayMode(mode: String) {
        userPreferences.edit { preferences ->
            val validMode = if (com.parsfilo.contentapp.core.model.DisplayMode.entries.any { it.name == mode }) {
                mode
            } else {
                "ARABIC"
            }
            preferences[PreferencesKeys.DISPLAY_MODE] = validMode
        }
    }

    suspend fun setFontSize(size: Int) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE] = size
        }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    suspend fun setPremium(isPremium: Boolean) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.IS_PREMIUM] = isPremium
        }
    }

    suspend fun setLastAppOpenAdShown(timestamp: Long) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.LAST_APP_OPEN_AD] = timestamp
        }
    }

    suspend fun setRewardedAdFreeUntil(timestamp: Long) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.REWARDED_AD_FREE_UNTIL] = timestamp
        }
    }

    suspend fun setRewardWatchCount(count: Int) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.REWARD_WATCH_COUNT] = count
        }
    }

    suspend fun setLastInterstitialShown(timestamp: Long) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.LAST_INTERSTITIAL_SHOWN] = timestamp
        }
    }

    suspend fun setLastRewardedInterstitialShown(timestamp: Long) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.LAST_REWARDED_INTERSTITIAL_SHOWN] = timestamp
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setNotificationPermissionPrompted(prompted: Boolean) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATION_PERMISSION_PROMPTED] = prompted
        }
    }

    suspend fun getOrCreateInstallationId(): String {
        var installationId = ""
        userPreferences.edit { preferences ->
            val existing = preferences[PreferencesKeys.INSTALLATION_ID]
            installationId = if (existing.isNullOrBlank()) {
                UUID.randomUUID().toString().also { generated ->
                    preferences[PreferencesKeys.INSTALLATION_ID] = generated
                }
            } else {
                existing
            }
        }
        return installationId
    }

    suspend fun setPushSyncMeta(token: String, timestamp: Long = System.currentTimeMillis()) {
        val tokenHash = token.sha256()
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.LAST_PUSH_TOKEN] = token
            preferences[PreferencesKeys.LAST_PUSH_SYNC_AT] = timestamp
            preferences[PreferencesKeys.LAST_PUSH_SYNC_ATTEMPT_AT] = timestamp
            preferences[PreferencesKeys.LAST_PUSH_SYNC_SUCCESS_AT] = timestamp
            preferences[PreferencesKeys.LAST_PUSH_SYNC_FAILURE_REASON] = ""
            preferences[PreferencesKeys.LAST_PUSH_TOKEN_HASH] = tokenHash
            preferences[PreferencesKeys.HAS_PUSH_TOKEN] = token.isNotBlank()
        }
    }

    suspend fun markPushSyncAttempt(token: String, timestamp: Long = System.currentTimeMillis()) {
        val tokenHash = token.sha256()
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.LAST_PUSH_SYNC_ATTEMPT_AT] = timestamp
            preferences[PreferencesKeys.LAST_PUSH_TOKEN] = token
            preferences[PreferencesKeys.LAST_PUSH_TOKEN_HASH] = tokenHash
            preferences[PreferencesKeys.HAS_PUSH_TOKEN] = token.isNotBlank()
        }
    }

    suspend fun markPushSyncFailure(
        reason: String,
        token: String,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val tokenHash = token.sha256()
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.LAST_PUSH_SYNC_ATTEMPT_AT] = timestamp
            preferences[PreferencesKeys.LAST_PUSH_SYNC_FAILURE_REASON] = reason
            preferences[PreferencesKeys.LAST_PUSH_TOKEN] = token
            preferences[PreferencesKeys.LAST_PUSH_TOKEN_HASH] = tokenHash
            preferences[PreferencesKeys.HAS_PUSH_TOKEN] = token.isNotBlank()
        }
    }

    suspend fun recordAdRuntimeEvent(telemetryEvent: AdRuntimeTelemetryEvent) {
        userPreferences.edit { preferences ->
            val snapshot = readAdRuntimeTelemetrySnapshot(preferences)
            val shouldResetWindow =
                snapshot.windowStartAt == 0L ||
                    telemetryEvent.timestamp - snapshot.windowStartAt > AD_RUNTIME_TELEMETRY_WINDOW_MS

            val funnelCounts =
                if (shouldResetWindow) {
                    mutableMapOf()
                } else {
                    snapshot.funnelCountsByFormat
                        .mapValues { (_, counts) -> counts.toMutableMap() }
                        .toMutableMap()
                }

            val suppressReasonCounts =
                if (shouldResetWindow) {
                    mutableMapOf()
                } else {
                    snapshot.suppressReasonCounts.toMutableMap()
                }

            val formatBucket = funnelCounts.getOrPut(telemetryEvent.format) { mutableMapOf() }
            formatBucket[telemetryEvent.event] = (formatBucket[telemetryEvent.event] ?: 0) + 1
            runtimePlacementEventKey(telemetryEvent.event, telemetryEvent.placement)?.let { placementEventKey ->
                formatBucket[placementEventKey] = (formatBucket[placementEventKey] ?: 0) + 1
            }

            if (!telemetryEvent.suppressReason.isNullOrBlank()) {
                suppressReasonCounts[telemetryEvent.suppressReason] =
                    (suppressReasonCounts[telemetryEvent.suppressReason] ?: 0) + 1
            }

            val windowStartAt = if (shouldResetWindow) telemetryEvent.timestamp else snapshot.windowStartAt
            preferences[PreferencesKeys.AD_RUNTIME_TELEMETRY_WINDOW_START_AT] = windowStartAt
            preferences[PreferencesKeys.AD_RUNTIME_TELEMETRY_LAST_UPDATED_AT] = telemetryEvent.timestamp
            preferences[PreferencesKeys.AD_RUNTIME_FUNNEL_COUNTS_JSON] =
                funnelCounts.toNestedJsonString()
            preferences[PreferencesKeys.AD_RUNTIME_SUPPRESS_REASON_COUNTS_JSON] =
                suppressReasonCounts.toFlatJsonString()
        }
    }

    suspend fun setOtherAppsBadgeSeenSignature(signature: String) {
        userPreferences.edit { preferences ->
            preferences[PreferencesKeys.OTHER_APPS_BADGE_SEEN_SIGNATURE] = signature
        }
    }

    suspend fun setZikirHapticEnabled(v: Boolean) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_HAPTIC] = v }
    }

    suspend fun setZikirSoundEnabled(v: Boolean) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_SOUND] = v }
    }

    suspend fun setLastSelectedZikirKey(key: String) {
        userPreferences.edit { it[PreferencesKeys.LAST_ZIKIR_KEY] = key }
    }

    suspend fun setZikirReminderEnabled(v: Boolean) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_REMINDER_ENABLED] = v }
    }

    suspend fun setZikirReminderHour(h: Int) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_REMINDER_HOUR] = h }
    }

    suspend fun setZikirReminderMinute(m: Int) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_REMINDER_MINUTE] = m }
    }

    suspend fun setZikirDailyGoal(goal: Int) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_DAILY_GOAL] = goal }
    }

    suspend fun setZikirStreakReminderEnabled(v: Boolean) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_STREAK_REMINDER] = v }
    }

    suspend fun setZikirLastInterstitialAt(timestamp: Long) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_LAST_INTERSTITIAL_AT] = timestamp }
    }

    suspend fun setZikirInterstitialShownCountDay(count: Int) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_INTERSTITIAL_SHOWN_COUNT_DAY] = count }
    }

    suspend fun setZikirInterstitialDayKey(dayKey: String) {
        userPreferences.edit { it[PreferencesKeys.ZIKIR_INTERSTITIAL_DAY_KEY] = dayKey }
    }

    suspend fun setCustomZikirItemsJson(value: String) {
        userPreferences.edit { it[PreferencesKeys.CUSTOM_ZIKIR_ITEMS_JSON] = value }
    }

    suspend fun setDeletedZikirKeysJson(value: String) {
        userPreferences.edit { it[PreferencesKeys.DELETED_ZIKIR_KEYS_JSON] = value }
    }

    suspend fun setQuranReciter(reciterId: String) {
        userPreferences.edit { it[PreferencesKeys.QURAN_SELECTED_RECITER] = reciterId }
    }

    suspend fun setQuranDisplayMode(mode: String) {
        userPreferences.edit { it[PreferencesKeys.QURAN_DISPLAY_MODE] = mode }
    }

    suspend fun setQuranFontSize(size: Int) {
        userPreferences.edit { it[PreferencesKeys.QURAN_FONT_SIZE] = size }
    }

    suspend fun setAdsAgeGateStatus(status: String) {
        userPreferences.edit { it[PreferencesKeys.ADS_AGE_GATE_STATUS] = status }
    }

    suspend fun setAdsAgeGatePromptCompleted(completed: Boolean) {
        userPreferences.edit { it[PreferencesKeys.ADS_AGE_GATE_PROMPT_COMPLETED] = completed }
    }

    suspend fun setAdsConsentSnapshot(
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        userPreferences.edit {
            it[PreferencesKeys.ADS_CONSENT_STATUS] = status
            it[PreferencesKeys.ADS_CONSENT_UPDATED_AT] = updatedAt
        }
    }

    suspend fun setAdsLastSuccessfulConsentSnapshot(
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        userPreferences.edit {
            it[PreferencesKeys.ADS_LAST_SUCCESSFUL_CONSENT_STATUS] = status
            it[PreferencesKeys.ADS_LAST_SUCCESSFUL_CONSENT_UPDATED_AT] = updatedAt
        }
    }

    private fun String.sha256(): String {
        if (isBlank()) return ""
        return MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    internal object PreferencesKeys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val FONT_SIZE = intPreferencesKey("font_size")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val LAST_APP_OPEN_AD = longPreferencesKey("last_app_open_ad")
        val REWARDED_AD_FREE_UNTIL = longPreferencesKey("rewarded_ad_free_until")
        val REWARD_WATCH_COUNT = intPreferencesKey("reward_watch_count")
        val LAST_INTERSTITIAL_SHOWN = longPreferencesKey("last_interstitial_shown")
        val LAST_REWARDED_INTERSTITIAL_SHOWN = longPreferencesKey("last_rewarded_interstitial_shown")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
        val INSTALLATION_ID = stringPreferencesKey("installation_id")
        val LAST_PUSH_SYNC_AT = longPreferencesKey("last_push_sync_at")
        val LAST_PUSH_TOKEN = stringPreferencesKey("last_push_token")
        val LAST_PUSH_SYNC_ATTEMPT_AT = longPreferencesKey("last_push_sync_attempt_at")
        val LAST_PUSH_SYNC_SUCCESS_AT = longPreferencesKey("last_push_sync_success_at")
        val LAST_PUSH_SYNC_FAILURE_REASON = stringPreferencesKey("last_push_sync_failure_reason")
        val LAST_PUSH_TOKEN_HASH = stringPreferencesKey("last_push_token_hash")
        val HAS_PUSH_TOKEN = booleanPreferencesKey("has_push_token")
        val AD_RUNTIME_TELEMETRY_WINDOW_START_AT =
            longPreferencesKey("ad_runtime_telemetry_window_start_at")
        val AD_RUNTIME_TELEMETRY_LAST_UPDATED_AT =
            longPreferencesKey("ad_runtime_telemetry_last_updated_at")
        val AD_RUNTIME_FUNNEL_COUNTS_JSON =
            stringPreferencesKey("ad_runtime_funnel_counts_json")
        val AD_RUNTIME_SUPPRESS_REASON_COUNTS_JSON =
            stringPreferencesKey("ad_runtime_suppress_reason_counts_json")
        val ADS_AGE_GATE_STATUS = stringPreferencesKey("ads_age_gate_status")
        val ADS_CONSENT_STATUS = stringPreferencesKey("ads_consent_status")
        val ADS_CONSENT_UPDATED_AT = longPreferencesKey("ads_consent_updated_at")
        val ADS_LAST_SUCCESSFUL_CONSENT_STATUS =
            stringPreferencesKey("ads_last_successful_consent_status")
        val ADS_LAST_SUCCESSFUL_CONSENT_UPDATED_AT =
            longPreferencesKey("ads_last_successful_consent_updated_at")
        val ADS_AGE_GATE_PROMPT_COMPLETED = booleanPreferencesKey("ads_age_gate_prompt_completed")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val OTHER_APPS_BADGE_SEEN_SIGNATURE = stringPreferencesKey("other_apps_badge_seen_signature")

        val ZIKIR_HAPTIC = booleanPreferencesKey("zikir_haptic")
        val ZIKIR_SOUND = booleanPreferencesKey("zikir_sound")
        val LAST_ZIKIR_KEY = stringPreferencesKey("last_zikir_key")
        val ZIKIR_REMINDER_ENABLED = booleanPreferencesKey("zikir_reminder_enabled")
        val ZIKIR_REMINDER_HOUR = intPreferencesKey("zikir_reminder_hour")
        val ZIKIR_REMINDER_MINUTE = intPreferencesKey("zikir_reminder_minute")
        val ZIKIR_DAILY_GOAL = intPreferencesKey("zikir_daily_goal")
        val ZIKIR_STREAK_REMINDER = booleanPreferencesKey("zikir_streak_reminder")

        val ZIKIR_LAST_INTERSTITIAL_AT = longPreferencesKey("zikir_last_interstitial_at")
        val ZIKIR_INTERSTITIAL_SHOWN_COUNT_DAY = intPreferencesKey("zikir_interstitial_shown_count_day")
        val ZIKIR_INTERSTITIAL_DAY_KEY = stringPreferencesKey("zikir_interstitial_day_key")
        val CUSTOM_ZIKIR_ITEMS_JSON = stringPreferencesKey("custom_zikir_items_json")
        val DELETED_ZIKIR_KEYS_JSON = stringPreferencesKey("deleted_zikir_keys_json")

        val QURAN_SELECTED_RECITER = stringPreferencesKey("quran_selected_reciter")
        val QURAN_DISPLAY_MODE = stringPreferencesKey("quran_display_mode")
        val QURAN_FONT_SIZE = intPreferencesKey("quran_font_size")
    }
}

data class UserPreferencesData(
    val darkMode: Boolean,
    val displayMode: String,
    val fontSize: Int,
    val developerModeEnabled: Boolean,
    val isPremium: Boolean,
    val lastAppOpenAdShown: Long,
    val rewardedAdFreeUntil: Long,
    val rewardWatchCount: Int,
    val lastInterstitialShown: Long,
    val lastRewardedInterstitialShown: Long,
    val notificationsEnabled: Boolean,
    val notificationPermissionPrompted: Boolean = false,
    val installationId: String = "",
    val lastPushSyncAt: Long = 0L,
    val lastPushToken: String = "",
    val lastPushSyncAttemptAt: Long = 0L,
    val lastPushSyncSuccessAt: Long = 0L,
    val lastPushSyncFailureReason: String = "",
    val lastPushTokenHash: String = "",
    val hasPushToken: Boolean = false,
    val adsAgeGateStatus: String = "UNKNOWN",
    val adsAgeGatePromptCompleted: Boolean = false,
    val adRuntimeTelemetry: AdRuntimeTelemetrySnapshot = AdRuntimeTelemetrySnapshot(),
)

internal fun readAdRuntimeTelemetrySnapshot(
    preferences: Preferences,
): AdRuntimeTelemetrySnapshot =
    AdRuntimeTelemetrySnapshot(
        windowStartAt =
            preferences[PreferencesDataSource.PreferencesKeys.AD_RUNTIME_TELEMETRY_WINDOW_START_AT]
                ?: 0L,
        lastUpdatedAt =
            preferences[PreferencesDataSource.PreferencesKeys.AD_RUNTIME_TELEMETRY_LAST_UPDATED_AT]
                ?: 0L,
        funnelCountsByFormat =
            parseNestedCountsJson(
                preferences[PreferencesDataSource.PreferencesKeys.AD_RUNTIME_FUNNEL_COUNTS_JSON]
                    ?: "{}",
            ),
        suppressReasonCounts =
            parseFlatCountsJson(
                preferences[PreferencesDataSource.PreferencesKeys.AD_RUNTIME_SUPPRESS_REASON_COUNTS_JSON]
                    ?: "{}",
            ),
    )

private fun parseNestedCountsJson(raw: String): Map<String, Map<String, Int>> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val root = JSONObject(raw)
        root.keys().asSequence().associateWith { format ->
            val counts = root.optJSONObject(format) ?: JSONObject()
            counts.keys().asSequence()
                .associateWith { key -> counts.optInt(key, 0) }
                .filterValues { value -> value > 0 }
        }.filterValues { value -> value.isNotEmpty() }
    }.getOrDefault(emptyMap())
}

private fun parseFlatCountsJson(raw: String): Map<String, Int> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val root = JSONObject(raw)
        root.keys().asSequence()
            .associateWith { key -> root.optInt(key, 0) }
            .filterValues { value -> value > 0 }
    }.getOrDefault(emptyMap())
}

internal fun runtimePlacementEventKey(
    event: String,
    placement: String?,
): String? =
    placement
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { normalizedPlacement -> "$event$AD_RUNTIME_PLACEMENT_EVENT_SEPARATOR$normalizedPlacement" }

internal fun Map<String, Map<String, Int>>.toNestedJsonString(): String =
    JSONObject().also { root ->
        for ((outerKey, innerMap) in this) {
            val nested = JSONObject()
            for ((innerKey, value) in innerMap) {
                nested.put(innerKey, value)
            }
            root.put(outerKey, nested)
        }
    }.toString()

internal fun Map<String, Int>.toFlatJsonString(): String =
    JSONObject().also { root ->
        for ((key, value) in this) {
            root.put(key, value)
        }
    }.toString()

private const val AD_RUNTIME_TELEMETRY_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
private const val AD_RUNTIME_PLACEMENT_EVENT_SEPARATOR = "@"
