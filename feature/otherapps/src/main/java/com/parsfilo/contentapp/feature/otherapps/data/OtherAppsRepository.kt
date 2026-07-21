package com.parsfilo.contentapp.feature.otherapps.data

import com.parsfilo.contentapp.feature.otherapps.model.OtherApp
import kotlinx.coroutines.flow.StateFlow

interface OtherAppsRepository {
    val apps: StateFlow<List<OtherApp>>

    suspend fun refreshIfNeeded(force: Boolean = false)
}
