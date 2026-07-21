package com.parsfilo.contentapp.core.firebase.push

import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushRegistrationRequestTest {

    @Test
    fun `payload json includes the complete registration contract`() {
        val json = samplePayload().toJson()

        assertEquals("installation-12345678", json.getString("installationId"))
        assertEquals("token-value", json.getString("fcmToken"))
        assertEquals("com.parsfilo.yasinsuresi", json.getString("packageName"))
        assertEquals("tr-TR", json.getString("locale"))
        assertEquals("Europe/Istanbul", json.getString("timezone"))
        assertTrue(json.getBoolean("notificationsEnabled"))
        assertEquals("2.4.1", json.getString("appVersion"))
        assertEquals("Android Device", json.getString("deviceModel"))
        assertEquals("token_refresh", json.getString("reason"))
        assertEquals(1_800_000_000_000L, json.getLong("syncedAtEpochMs"))
        assertEquals("hash-value", json.getString("tokenHash"))
        assertTrue(json.getBoolean("hasToken"))
        assertEquals(1_799_999_999_000L, json.getLong("lastAttemptAtEpochMs"))
        assertEquals(1_800_000_000_000L, json.getLong("lastSuccessAtEpochMs"))
        assertEquals("none", json.getString("lastFailureReason"))
        assertEquals(1_799_999_900_000L, json.getLong("adRuntimeWindowStartAtEpochMs"))
        assertEquals(1_799_999_999_500L, json.getLong("adRuntimeLastUpdatedAtEpochMs"))
        assertEquals(4, json.getJSONObject("adRuntimeFunnelCounts").getJSONObject("banner").getInt("requested"))
        assertEquals(2, json.getJSONObject("adRuntimeFunnelCounts").getJSONObject("banner").getInt("shown"))
        assertEquals(3, json.getJSONObject("adRuntimeSuppressReasonCounts").getInt("consent_missing"))
    }

    @Test
    fun `request sends App Check header and JSON body`() {
        val request = buildPushRegistrationRequest(
            url = "https://contentapp-admin-api.example/registerDevice".toHttpUrl(),
            payload = samplePayload(),
            appCheckToken = "verified-app-check-token",
        )
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val body = JSONObject(buffer.readUtf8())

        assertEquals("POST", request.method)
        assertEquals("verified-app-check-token", request.header("X-Firebase-AppCheck"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("application/json", request.body!!.contentType()?.let { "${it.type}/${it.subtype}" })
        assertEquals("installation-12345678", body.getString("installationId"))
        assertEquals("hash-value", body.getString("tokenHash"))
    }

    @Test
    fun `request rejects a blank App Check token`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildPushRegistrationRequest(
                url = "https://contentapp-admin-api.example/registerDevice".toHttpUrl(),
                payload = samplePayload(),
                appCheckToken = " ",
            )
        }
    }

    @Test
    fun `nullable payload fields are omitted instead of serialized as strings`() {
        val json = samplePayload().copy(
            lastSuccessAtEpochMs = null,
            lastFailureReason = null,
            adRuntimeWindowStartAtEpochMs = null,
            adRuntimeLastUpdatedAtEpochMs = null,
        ).toJson()

        assertFalse(json.has("lastSuccessAtEpochMs"))
        assertFalse(json.has("lastFailureReason"))
        assertFalse(json.has("adRuntimeWindowStartAtEpochMs"))
        assertFalse(json.has("adRuntimeLastUpdatedAtEpochMs"))
    }

    private fun samplePayload() = PushRegistrationPayload(
        installationId = "installation-12345678",
        fcmToken = "token-value",
        packageName = "com.parsfilo.yasinsuresi",
        locale = "tr-TR",
        timezone = "Europe/Istanbul",
        notificationsEnabled = true,
        appVersion = "2.4.1",
        deviceModel = "Android Device",
        reason = "token_refresh",
        syncedAtEpochMs = 1_800_000_000_000L,
        tokenHash = "hash-value",
        hasToken = true,
        lastAttemptAtEpochMs = 1_799_999_999_000L,
        lastSuccessAtEpochMs = 1_800_000_000_000L,
        lastFailureReason = "none",
        adRuntimeWindowStartAtEpochMs = 1_799_999_900_000L,
        adRuntimeLastUpdatedAtEpochMs = 1_799_999_999_500L,
        adRuntimeFunnelCounts = mapOf("banner" to mapOf("requested" to 4, "shown" to 2)),
        adRuntimeSuppressReasonCounts = mapOf("consent_missing" to 3),
    )
}
