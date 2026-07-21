package com.parsfilo.contentapp.update

data class RemoteUpdateConfig(
    val updatesEnabled: Boolean,
    val globalEmergencyDisabled: Boolean,
    val appEmergencyDisabled: Boolean,
    val minSupportedVersionCode: Long,
    val latestVersionCode: Long,
    val updateMode: String,
    val title: String,
    val message: String,
    val updateButton: String,
    val laterButton: String,
)

sealed class UpdatePolicy {
    data object None : UpdatePolicy()

    data class Soft(
        val title: String,
        val message: String,
        val updateButton: String,
        val laterButton: String,
    ) : UpdatePolicy()

    data class Hard(
        val title: String,
        val message: String,
        val updateButton: String,
    ) : UpdatePolicy()
}

internal fun resolveUpdatePolicy(
    currentVersionCode: Long,
    cfg: RemoteUpdateConfig,
): UpdatePolicy {
    if (cfg.globalEmergencyDisabled || !cfg.updatesEnabled || cfg.appEmergencyDisabled) {
        return UpdatePolicy.None
    }
    if (cfg.minSupportedVersionCode < 1L || cfg.latestVersionCode < cfg.minSupportedVersionCode) {
        return UpdatePolicy.None
    }
    if (currentVersionCode < cfg.minSupportedVersionCode) {
        return UpdatePolicy.Hard(
            title = cfg.title,
            message = cfg.message,
            updateButton = cfg.updateButton,
        )
    }
    if (currentVersionCode >= cfg.latestVersionCode) {
        return UpdatePolicy.None
    }

    return when (normalizeUpdateMode(cfg.updateMode)) {
        UpdateMode.HARD ->
            UpdatePolicy.Hard(
                title = cfg.title,
                message = cfg.message,
                updateButton = cfg.updateButton,
            )

        UpdateMode.SOFT ->
            UpdatePolicy.Soft(
                title = cfg.title,
                message = cfg.message,
                updateButton = cfg.updateButton,
                laterButton = cfg.laterButton,
            )

        UpdateMode.NONE -> UpdatePolicy.None
    }
}

internal enum class UpdateMode {
    NONE,
    SOFT,
    HARD,
}

internal fun normalizeUpdateMode(value: String?): UpdateMode =
    when (value?.trim()?.lowercase()) {
        "hard" -> UpdateMode.HARD
        "soft" -> UpdateMode.SOFT
        else -> UpdateMode.NONE
    }

internal data class SafeRemoteVersionRange(
    val minSupportedVersionCode: Long,
    val latestVersionCode: Long,
    val usedFallback: Boolean,
)

internal fun sanitizeRemoteVersionRange(
    currentVersionCode: Long,
    rawMinSupportedVersionCode: Long?,
    rawLatestVersionCode: Long?,
): SafeRemoteVersionRange {
    val safeCurrent = currentVersionCode.coerceAtLeast(1L)
    val minimum = rawMinSupportedVersionCode ?: safeCurrent
    val latest = rawLatestVersionCode ?: safeCurrent
    val maximumAccepted =
        safeCurrent.coerceAtMost(Long.MAX_VALUE - MAX_REMOTE_VERSION_AHEAD) +
            MAX_REMOTE_VERSION_AHEAD
    val valid =
        rawMinSupportedVersionCode != null &&
            rawLatestVersionCode != null &&
            minimum >= 1L &&
            latest >= minimum &&
            latest <= maximumAccepted
    return if (valid) {
        SafeRemoteVersionRange(
            minSupportedVersionCode = minimum,
            latestVersionCode = latest,
            usedFallback = false,
        )
    } else {
        SafeRemoteVersionRange(
            minSupportedVersionCode = safeCurrent,
            latestVersionCode = safeCurrent,
            usedFallback = true,
        )
    }
}

internal fun coerceRemoteVersionCode(value: Long): Long = value.coerceAtLeast(1L)

internal fun resolveLocalizedRemoteText(
    languageCode: String,
    trValue: String?,
    enValue: String?,
    fallback: String,
): String {
    val preferred = if (languageCode.equals("tr", ignoreCase = true)) trValue else enValue
    val secondary = if (languageCode.equals("tr", ignoreCase = true)) enValue else trValue
    return preferred?.trim().takeUnless { it.isNullOrBlank() }
        ?: secondary?.trim().takeUnless { it.isNullOrBlank() }
        ?: fallback
}

data class UpdateDebugSnapshot(
    val currentVersionCode: Long,
    val config: RemoteUpdateConfig,
    val resolvedPolicy: UpdatePolicy,
) {
    fun toSummaryText(): String =
        "current=$currentVersionCode, enabled=${config.updatesEnabled}, " +
            "emergency=${config.globalEmergencyDisabled || config.appEmergencyDisabled}, " +
            "min=${config.minSupportedVersionCode}, latest=${config.latestVersionCode}, " +
            "mode=${config.updateMode}, policy=${resolvedPolicy::class.simpleName}"
}

internal object RemoteUpdateConfigKeys {
    const val GLOBAL_EMERGENCY_DISABLED = "update_global_emergency_disabled"
    const val UPDATES_ENABLED = "updates_enabled"
    const val APP_EMERGENCY_DISABLED = "update_emergency_disabled"
    const val MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
    const val LATEST_VERSION_CODE = "latest_version_code"
    const val UPDATE_MODE = "update_mode"
    const val UPDATE_TITLE_TR = "update_title_tr"
    const val UPDATE_MESSAGE_TR = "update_message_tr"
    const val UPDATE_TITLE_EN = "update_title_en"
    const val UPDATE_MESSAGE_EN = "update_message_en"
    const val UPDATE_BUTTON_TR = "update_button_tr"
    const val UPDATE_BUTTON_EN = "update_button_en"
    const val LATER_BUTTON_TR = "later_button_tr"
    const val LATER_BUTTON_EN = "later_button_en"

    val allKeys: List<String> =
        listOf(
            GLOBAL_EMERGENCY_DISABLED,
            UPDATES_ENABLED,
            APP_EMERGENCY_DISABLED,
            MIN_SUPPORTED_VERSION_CODE,
            LATEST_VERSION_CODE,
            UPDATE_MODE,
            UPDATE_TITLE_TR,
            UPDATE_MESSAGE_TR,
            UPDATE_TITLE_EN,
            UPDATE_MESSAGE_EN,
            UPDATE_BUTTON_TR,
            UPDATE_BUTTON_EN,
            LATER_BUTTON_TR,
            LATER_BUTTON_EN,
        )

    fun defaults(currentVersionCode: Long): Map<String, Any> {
        val safeCurrent = currentVersionCode.coerceAtLeast(1L)
        return mapOf(
            GLOBAL_EMERGENCY_DISABLED to false,
            UPDATES_ENABLED to false,
            APP_EMERGENCY_DISABLED to false,
            MIN_SUPPORTED_VERSION_CODE to safeCurrent,
            LATEST_VERSION_CODE to safeCurrent,
            UPDATE_MODE to "none",
            UPDATE_TITLE_TR to "Güncelleme gerekli",
            UPDATE_MESSAGE_TR to "Devam etmek için lütfen uygulamayı güncelleyin.",
            UPDATE_TITLE_EN to "Update required",
            UPDATE_MESSAGE_EN to "Please update the app to continue.",
            UPDATE_BUTTON_TR to "Güncelle",
            UPDATE_BUTTON_EN to "Update",
            LATER_BUTTON_TR to "Daha sonra",
            LATER_BUTTON_EN to "Later",
        )
    }
}

private const val MAX_REMOTE_VERSION_AHEAD = 10_000L
