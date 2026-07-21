package com.parsfilo.contentapp.core.datastore

data class AdRuntimeTelemetrySnapshot(
    val windowStartAt: Long = 0L,
    val lastUpdatedAt: Long = 0L,
    val funnelCountsByFormat: Map<String, Map<String, Int>> = emptyMap(),
    val suppressReasonCounts: Map<String, Int> = emptyMap(),
)
