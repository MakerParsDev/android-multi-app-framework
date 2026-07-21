package com.parsfilo.contentapp.feature.prayertimes.model

sealed interface RefreshResult {
    data object Refreshed : RefreshResult

    data object SkippedFresh : RefreshResult

    data object ServedFromCache : RefreshResult

    data object InvalidSelection : RefreshResult

    data class Failed(val throwable: Throwable) : RefreshResult
}

sealed interface ResolveResult {
    data class Success(val selection: PrayerLocationSelection) : ResolveResult

    data object PermissionDenied : ResolveResult

    data object LocationUnavailable : ResolveResult

    data object NoMatch : ResolveResult

    data class Failed(val throwable: Throwable) : ResolveResult
}
