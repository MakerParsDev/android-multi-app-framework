package com.parsfilo.contentapp.product

import com.parsfilo.contentapp.BuildConfig

data class AppProductDefinition(
    val flavorId: String,
    val packageName: String,
    val displayName: String,
    val contentFamily: ContentFamily,
    val monetizationProfile: String,
    val notificationProfile: String,
    val billingProfile: String,
    val themeTokenKey: String,
    val audioFileName: String?,
    val useAssetPackAudio: Boolean,
    val capabilityFlags: Set<String>,
) {
    val isPrayerTimesFlavor: Boolean
        get() = contentFamily == ContentFamily.PRAYER_TIMES

    fun hasCapability(capability: String): Boolean = capability in capabilityFlags

    companion object {
        val current: AppProductDefinition by lazy {
            AppProductDefinition(
                flavorId = BuildConfig.FLAVOR_NAME,
                packageName = BuildConfig.APPLICATION_ID,
                displayName = BuildConfig.PRODUCT_DISPLAY_NAME,
                contentFamily = ContentFamily.from(BuildConfig.CONTENT_FAMILY),
                monetizationProfile = BuildConfig.MONETIZATION_PROFILE,
                notificationProfile = BuildConfig.NOTIFICATION_PROFILE,
                billingProfile = BuildConfig.BILLING_PROFILE,
                themeTokenKey = BuildConfig.THEME_TOKEN_KEY,
                audioFileName = BuildConfig.AUDIO_FILE_NAME.takeIf { it.isNotBlank() },
                useAssetPackAudio = BuildConfig.USE_ASSET_PACK_AUDIO,
                capabilityFlags =
                    BuildConfig.PRODUCT_CAPABILITIES_CSV
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet(),
            )
        }
    }
}

enum class ContentFamily(
    val storageValue: String,
) {
    CONTENT("content"),
    PRAYER_LIBRARY("prayer_library"),
    MIRACLES("miracles"),
    PRAYER_TIMES("prayer_times"),
    ZIKIR_COUNTER("zikir_counter"),
    QIBLA("qibla"),
    QURAN("quran"),
    ESMA("esma"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun from(value: String): ContentFamily =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
