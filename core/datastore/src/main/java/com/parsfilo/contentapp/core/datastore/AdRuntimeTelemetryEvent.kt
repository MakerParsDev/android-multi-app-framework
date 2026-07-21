package com.parsfilo.contentapp.core.datastore

data class AdRuntimeTelemetryEvent(
    val format: String,
    val placement: String? = null,
    val event: String,
    val suppressReason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
