package com.parsfilo.contentapp.core.firebase.config

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EndpointsProvider @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager,
) {
    init {
        remoteConfigManager.setDefaults(DEFAULT_ENDPOINTS)
    }

    fun prefetchAsync() {
        remoteConfigManager.fetchAndActivateAsync(
            onSuccess = { activated ->
                Timber.d("Remote Config endpoints refreshed (activated=%s).", activated)
            },
            onFailure = { throwable ->
                Timber.w(throwable, "Remote Config endpoint refresh failed; defaults will be used.")
            },
        )
    }

    fun getAudioBaseUrl(): String =
        resolveEndpointValue(remoteConfigManager.getString(KEY_AUDIO_BASE_URL), DEFAULT_AUDIO_BASE_URL)

    fun getAudioManifestUrl(): String =
        resolveEndpointValue(
            remoteConfigManager.getString(KEY_AUDIO_MANIFEST_URL),
            DEFAULT_AUDIO_MANIFEST_URL,
        )

    fun getOtherAppsUrl(): String =
        resolveEndpointValue(remoteConfigManager.getString(KEY_OTHER_APPS_URL), DEFAULT_OTHER_APPS_URL)

    companion object {
        const val KEY_AUDIO_BASE_URL = "audio_base_url"
        const val KEY_AUDIO_MANIFEST_URL = "audio_manifest_url"
        const val KEY_OTHER_APPS_URL = "other_apps_url"

        const val DEFAULT_AUDIO_BASE_URL =
            "https://contentapp-content-api.oaslananka.workers.dev/api/audio"
        const val DEFAULT_AUDIO_MANIFEST_URL =
            "https://contentapp-content-api.oaslananka.workers.dev/api/audio-manifest"
        const val DEFAULT_OTHER_APPS_URL =
            "https://contentapp-content-api.oaslananka.workers.dev/api/other-apps"

        private val DEFAULT_ENDPOINTS =
            mapOf(
                KEY_AUDIO_BASE_URL to DEFAULT_AUDIO_BASE_URL,
                KEY_AUDIO_MANIFEST_URL to DEFAULT_AUDIO_MANIFEST_URL,
                KEY_OTHER_APPS_URL to DEFAULT_OTHER_APPS_URL,
            )
    }
}

internal fun resolveEndpointValue(remoteValue: String?, defaultValue: String): String {
    val normalized = remoteValue?.trim().orEmpty()
    return normalized.ifBlank { defaultValue }
}
