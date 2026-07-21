package com.parsfilo.contentapp.feature.prayertimes.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val BASE_URL = "https://ezanvakti.emushaf.net"

class EzanVaktiHttpException(
    val statusCode: Int,
    val endpoint: String,
) : IOException("EzanVakti API HTTP $statusCode for $endpoint")

@Singleton
class EzanVaktiApiClient @Inject constructor() {
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        builder.readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        builder.addInterceptor(buildNetworkLoggingInterceptor())
        builder.build()
    }

    fun getCountries(): List<ApiCountry> =
        fetchArray("/ulkeler").map { obj ->
            ApiCountry(
                id = obj.optString("UlkeID").toIntOrNull() ?: 0,
                nameTr = obj.optString("UlkeAdi"),
                nameEn = obj.optString("UlkeAdiEn"),
            )
        }.filter { it.id > 0 && it.nameTr.isNotBlank() }

    fun getCities(countryId: Int): List<ApiCity> =
        fetchArray("/sehirler/$countryId").map { obj ->
            ApiCity(
                id = obj.optString("SehirID").toIntOrNull() ?: 0,
                nameTr = obj.optString("SehirAdi"),
                nameEn = obj.optString("SehirAdiEn"),
            )
        }.filter { it.id > 0 && it.nameTr.isNotBlank() }

    fun getDistricts(cityId: Int): List<ApiDistrict> =
        fetchArray("/ilceler/$cityId").map { obj ->
            ApiDistrict(
                id = obj.optString("IlceID").toIntOrNull() ?: 0,
                nameTr = obj.optString("IlceAdi"),
                nameEn = obj.optString("IlceAdiEn"),
            )
        }.filter { it.id > 0 && it.nameTr.isNotBlank() }

    fun getPrayerTimes(districtId: Int): List<ApiPrayerTime> =
        fetchArray("/vakitler/$districtId").map { obj ->
            ApiPrayerTime(
                miladiDateShort = obj.optString("MiladiTarihKisa"),
                imsak = obj.optString("Imsak"),
                gunes = obj.optString("Gunes"),
                ogle = obj.optString("Ogle"),
                ikindi = obj.optString("Ikindi"),
                aksam = obj.optString("Aksam"),
                yatsi = obj.optString("Yatsi"),
            )
        }.filter {
            it.miladiDateShort.isNotBlank() &&
                it.imsak.isNotBlank() &&
                it.gunes.isNotBlank() &&
                it.ogle.isNotBlank() &&
                it.ikindi.isNotBlank() &&
                it.aksam.isNotBlank() &&
                it.yatsi.isNotBlank()
        }

    private fun fetchArray(path: String): List<org.json.JSONObject> {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .get()
            .header("Accept", "application/json")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val responseCode = response.code
                if (responseCode !in HTTP_SUCCESS_CODE_MIN..HTTP_SUCCESS_CODE_MAX) {
                    throw EzanVaktiHttpException(statusCode = responseCode, endpoint = path)
                }

                val payload = response.body.string()
                val arr = JSONArray(payload)
                buildList(arr.length()) {
                    repeat(arr.length()) { index ->
                        add(arr.getJSONObject(index))
                    }
                }
            }
        }.onFailure {
            Timber.w(it, "EzanVaktiApiClient request error path=%s", path)
        }.getOrThrow()
    }

    private companion object {
        private const val NETWORK_LOG_SOURCE = "ezanvakti_api"
        private const val HTTP_SUCCESS_CODE_MIN = 200
        private const val HTTP_SUCCESS_CODE_MAX = 299
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    private fun buildNetworkLoggingInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val startNs = System.nanoTime()
            val method = request.method
            val host = request.url.host
            Timber.d("[Net][%s] -> %s %s", NETWORK_LOG_SOURCE, method, host)

            runCatching {
                chain.proceed(request)
            }.fold(
                onSuccess = { response: Response ->
                    val durationMs = ((System.nanoTime() - startNs) / 1_000_000.0).roundToInt()
                    Timber.d(
                        "[Net][%s] <- %s %s code=%d success=%s durationMs=%d",
                        NETWORK_LOG_SOURCE,
                        method,
                        host,
                        response.code,
                        response.isSuccessful,
                        durationMs,
                    )
                    response
                },
                onFailure = { error ->
                    val durationMs = ((System.nanoTime() - startNs) / 1_000_000.0).roundToInt()
                    Timber.w(
                        error,
                        "[Net][%s] xx %s %s durationMs=%d",
                        NETWORK_LOG_SOURCE,
                        method,
                        host,
                        durationMs,
                    )
                    throw error
                },
            )
        }
    }
}

data class ApiCountry(
    val id: Int,
    val nameTr: String,
    val nameEn: String,
)

data class ApiCity(
    val id: Int,
    val nameTr: String,
    val nameEn: String,
)

data class ApiDistrict(
    val id: Int,
    val nameTr: String,
    val nameEn: String,
)

data class ApiPrayerTime(
    val miladiDateShort: String,
    val imsak: String,
    val gunes: String,
    val ogle: String,
    val ikindi: String,
    val aksam: String,
    val yatsi: String,
)
