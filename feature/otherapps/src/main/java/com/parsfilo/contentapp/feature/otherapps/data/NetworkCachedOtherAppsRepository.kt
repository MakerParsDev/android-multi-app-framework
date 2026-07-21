package com.parsfilo.contentapp.feature.otherapps.data

import android.content.Context
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.common.network.TimberNetworkLoggingInterceptor
import com.parsfilo.contentapp.core.firebase.config.EndpointsProvider
import com.parsfilo.contentapp.feature.otherapps.model.OtherApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkCachedOtherAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val endpointsProvider: EndpointsProvider,
) : OtherAppsRepository {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .addInterceptor(TimberNetworkLoggingInterceptor("other_apps_feed"))
            .build()
    }

    private val _apps = MutableStateFlow<List<OtherApp>>(emptyList())
    override val apps: StateFlow<List<OtherApp>> = _apps.asStateFlow()

    private val cacheFile by lazy { File(context.filesDir, CACHE_FILE_NAME) }
    private val currentPackageName by lazy { context.packageName }
    private val refreshMutex = Mutex()

    override suspend fun refreshIfNeeded(force: Boolean): Unit = withContext(ioDispatcher) {
        refreshMutex.withLock {
            if (!force) {
                val freshCache = readCache(requireFresh = true)
                if (freshCache != null) {
                    _apps.value = filterCurrentApp(parseApps(freshCache))
                    return@withLock
                }
            }

            val remoteJson = fetchRemoteJson()
            if (!remoteJson.isNullOrBlank()) {
                val parsed = parseApps(remoteJson)
                if (parsed.isNotEmpty()) {
                    writeCache(remoteJson)
                    _apps.value = filterCurrentApp(parsed)
                    return@withLock
                }
            }

            val staleCache = readCache(requireFresh = false)
            if (staleCache != null) {
                _apps.value = filterCurrentApp(parseApps(staleCache))
            } else {
                val fallback = fallbackApps()
                if (fallback.isNotEmpty()) {
                    Timber.w(
                        "Other apps list could not be loaded from remote/cache, using local fallback size=%d",
                        fallback.size,
                    )
                    _apps.value = fallback
                } else {
                    Timber.w("Other apps list could not be loaded from remote or cache")
                }
            }
        }
    }

    private fun parseApps(jsonString: String): List<OtherApp> {
        return try {
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val appName = obj.optString("appName")
                    val packageName = obj.optString("packageName")
                    val appIconUrl = obj.optString("appIcon")
                    val isNew = obj.optBoolean("isNew", false)
                    if (appName.isNotBlank() && packageName.isNotBlank() && appIconUrl.isNotBlank()) {
                        add(
                            OtherApp(
                                appName = appName,
                                packageName = packageName,
                                appIconUrl = appIconUrl,
                                isNew = isNew,
                            ),
                        )
                    }
                }
            }.sortedWith(compareByDescending<OtherApp> { it.isNew }.thenBy { it.appName.lowercase() })
        } catch (e: JSONException) {
            Timber.e(e, "other_apps json parse error")
            emptyList()
        }
    }

    private fun filterCurrentApp(apps: List<OtherApp>): List<OtherApp> {
        return apps.filterNot { it.packageName.equals(currentPackageName, ignoreCase = true) }
    }

    private fun readCache(requireFresh: Boolean): String? {
        return try {
            if (!cacheFile.exists()) return null
            if (requireFresh) {
                val age = System.currentTimeMillis() - cacheFile.lastModified()
                if (age > CACHE_TTL_MILLIS) return null
            }
            cacheFile.readText()
        } catch (e: IOException) {
            Timber.w(e, "Failed to read other apps cache")
            null
        } catch (e: SecurityException) {
            Timber.w(e, "Failed to read other apps cache")
            null
        }
    }

    private fun writeCache(json: String) {
        try {
            cacheFile.writeText(json)
        } catch (e: IOException) {
            Timber.w(e, "Failed to write other apps cache")
        } catch (e: SecurityException) {
            Timber.w(e, "Failed to write other apps cache")
        }
    }

    private fun fetchRemoteJson(): String? {
        return fetchJsonFrom(endpointsProvider.getOtherAppsUrl())
    }

    private fun fetchJsonFrom(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.code !in 200..299) {
                    val errorBody = response.body.string().take(MAX_ERROR_BODY_LOG_CHARS)
                    Timber.w(
                        "Other apps fetch failed for %s with HTTP %d body=%s",
                        url,
                        response.code,
                        errorBody,
                    )
                    null
                } else {
                    response.body.string()
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Other apps remote fetch failed")
            null
        } catch (e: SecurityException) {
            Timber.w(e, "Other apps remote fetch failed")
            null
        }
    }

    private fun fallbackApps(): List<OtherApp> {
        // Fallback list is used only when remote endpoint and disk cache are both unavailable.
        return DEFAULT_FALLBACK_APPS
            .filterNot { it.packageName.equals(currentPackageName, ignoreCase = true) }
            .sortedWith(compareByDescending<OtherApp> { it.isNew }.thenBy { it.appName.lowercase() })
    }

    private companion object {
        private const val CACHE_FILE_NAME = "other_apps_cache.json"
        private const val CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_ERROR_BODY_LOG_CHARS = 200
        private const val DEFAULT_ICON_URL = "https://www.gstatic.com/android/market_images/web/play_prism_hlock_2x.png"

        private val DEFAULT_FALLBACK_APPS = listOf(
            OtherApp(
                appName = "Kuran-ı Kerim",
                packageName = "com.parsfilo.kuran_kerim",
                appIconUrl = DEFAULT_ICON_URL,
                isNew = false,
            ),
            OtherApp(
                appName = "Namaz Vakitleri",
                packageName = "com.parsfilo.namazvakitleri",
                appIconUrl = DEFAULT_ICON_URL,
                isNew = false,
            ),
            OtherApp(
                appName = "Zikirmatik",
                packageName = "com.parsfilo.zikirmatik",
                appIconUrl = DEFAULT_ICON_URL,
                isNew = false,
            ),
            OtherApp(
                appName = "Esmaül Hüsna",
                packageName = "com.parsfilo.esmaulhusna",
                appIconUrl = DEFAULT_ICON_URL,
                isNew = false,
            ),
        )
    }
}
