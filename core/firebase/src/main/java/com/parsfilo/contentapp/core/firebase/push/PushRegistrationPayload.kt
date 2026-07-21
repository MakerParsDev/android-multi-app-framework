package com.parsfilo.contentapp.core.firebase.push

data class PushRegistrationPayload(
    val installationId: String,
    val fcmToken: String,
    val packageName: String,
    val locale: String,
    val timezone: String,
    val notificationsEnabled: Boolean,
    val appVersion: String,
    val deviceModel: String,
    val reason: String,
    val syncedAtEpochMs: Long,
    val tokenHash: String,
    val hasToken: Boolean,
    val lastAttemptAtEpochMs: Long,
    val lastSuccessAtEpochMs: Long? = null,
    val lastFailureReason: String? = null,
    val adRuntimeWindowStartAtEpochMs: Long? = null,
    val adRuntimeLastUpdatedAtEpochMs: Long? = null,
    val adRuntimeFunnelCounts: Map<String, Map<String, Int>> = emptyMap(),
    val adRuntimeSuppressReasonCounts: Map<String, Int> = emptyMap(),
)
