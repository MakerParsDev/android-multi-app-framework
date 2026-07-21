package com.parsfilo.contentapp.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.firebase.config.RemoteConfigManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val remoteConfigManager: RemoteConfigManager,
        @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun checkForUpdate(forceFetch: Boolean = false): UpdatePolicy =
            withContext(ioDispatcher) {
                fetchSnapshot(forceFetch).resolvedPolicy
            }

        fun currentVersionCode(): Long = resolveCurrentVersionCode(context)

        fun getCachedRemoteUpdateConfig(): RemoteUpdateConfig {
            val versionCode = currentVersionCode()
            ensureDefaults(versionCode)
            return readConfigFromRemote(versionCode)
        }

        suspend fun refreshAndGetConfig(): RemoteUpdateConfig =
            withContext(ioDispatcher) {
                fetchSnapshot(forceFetch = true).config
            }

        suspend fun getDebugSnapshot(forceFetch: Boolean = false): UpdateDebugSnapshot =
            withContext(ioDispatcher) {
                fetchSnapshot(forceFetch)
            }

        private suspend fun fetchSnapshot(forceFetch: Boolean): UpdateDebugSnapshot {
            val versionCode = currentVersionCode()
            ensureDefaults(versionCode)
            if (forceFetch) {
                Timber.d("Force update check requested (debug fetch interval still applies).")
            }
            runCatching {
                remoteConfigManager.fetchAndActivate()
            }.onFailure { throwable ->
                Timber.w(
                    throwable,
                    "Remote Config fetch failed for update check; cached/default values will be used.",
                )
            }

            val config = readConfigFromRemote(versionCode)
            val policy = resolveUpdatePolicy(versionCode, config)
            return UpdateDebugSnapshot(
                currentVersionCode = versionCode,
                config = config,
                resolvedPolicy = policy,
            )
        }

        private fun ensureDefaults(currentVersionCode: Long) {
            remoteConfigManager.applyClientSettingsIfNeeded()
            remoteConfigManager.setDefaults(RemoteUpdateConfigKeys.defaults(currentVersionCode))
        }

        private fun readConfigFromRemote(currentVersionCode: Long): RemoteUpdateConfig {
            val languageCode =
                context.resources.configuration.locales[0]
                    ?.language
                    ?: Locale.getDefault().language
            val versions =
                sanitizeRemoteVersionRange(
                    currentVersionCode = currentVersionCode,
                    rawMinSupportedVersionCode =
                        remoteConfigManager.getLongOrNull(
                            RemoteUpdateConfigKeys.MIN_SUPPORTED_VERSION_CODE,
                        ),
                    rawLatestVersionCode =
                        remoteConfigManager.getLongOrNull(
                            RemoteUpdateConfigKeys.LATEST_VERSION_CODE,
                        ),
                )
            if (versions.usedFallback) {
                Timber.w(
                    "Invalid Remote Config update range; falling back to installed versionCode=%s",
                    currentVersionCode,
                )
            }
            val defaults = RemoteUpdateConfigKeys.defaults(currentVersionCode)
            val mode =
                remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.UPDATE_MODE)
                    ?.trim()
                    .orEmpty()

            val title =
                resolveLocalizedRemoteText(
                    languageCode = languageCode,
                    trValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.UPDATE_TITLE_TR),
                    enValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.UPDATE_TITLE_EN),
                    fallback = defaults.getValue(RemoteUpdateConfigKeys.UPDATE_TITLE_EN) as String,
                )
            val message =
                resolveLocalizedRemoteText(
                    languageCode = languageCode,
                    trValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.UPDATE_MESSAGE_TR),
                    enValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.UPDATE_MESSAGE_EN),
                    fallback = defaults.getValue(RemoteUpdateConfigKeys.UPDATE_MESSAGE_EN) as String,
                )
            val updateButton =
                resolveLocalizedRemoteText(
                    languageCode = languageCode,
                    trValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.UPDATE_BUTTON_TR),
                    enValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.UPDATE_BUTTON_EN),
                    fallback = defaults.getValue(RemoteUpdateConfigKeys.UPDATE_BUTTON_EN) as String,
                )
            val laterButton =
                resolveLocalizedRemoteText(
                    languageCode = languageCode,
                    trValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.LATER_BUTTON_TR),
                    enValue = remoteConfigManager.getStringOrNull(RemoteUpdateConfigKeys.LATER_BUTTON_EN),
                    fallback = defaults.getValue(RemoteUpdateConfigKeys.LATER_BUTTON_EN) as String,
                )

            return RemoteUpdateConfig(
                updatesEnabled =
                    remoteConfigManager.getBooleanOrNull(RemoteUpdateConfigKeys.UPDATES_ENABLED)
                        ?: false,
                globalEmergencyDisabled =
                    remoteConfigManager.getBooleanOrNull(
                        RemoteUpdateConfigKeys.GLOBAL_EMERGENCY_DISABLED,
                    ) ?: false,
                appEmergencyDisabled =
                    remoteConfigManager.getBooleanOrNull(
                        RemoteUpdateConfigKeys.APP_EMERGENCY_DISABLED,
                    ) ?: false,
                minSupportedVersionCode = versions.minSupportedVersionCode,
                latestVersionCode = versions.latestVersionCode,
                updateMode = mode,
                title = title,
                message = message,
                updateButton = updateButton,
                laterButton = laterButton,
            )
        }
    }

internal fun resolveCurrentVersionCode(context: Context): Long =
    runCatching {
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

        PackageInfoCompat.getLongVersionCode(packageInfo)
    }.getOrElse { throwable ->
        Timber.w(
            throwable,
            "Failed to read installed package versionCode; using BuildConfig fallback.",
        )
        com.parsfilo.contentapp.BuildConfig.VERSION_CODE
            .toLong()
    }
