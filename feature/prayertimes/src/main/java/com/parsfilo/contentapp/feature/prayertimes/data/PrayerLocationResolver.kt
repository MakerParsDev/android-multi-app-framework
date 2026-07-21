package com.parsfilo.contentapp.feature.prayertimes.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the device's last known location into a district-level prayer
 * location candidate.
 *
 * The public Nominatim request is delegated to [NominatimReverseGeocoder],
 * which rounds coordinates before transmission and centrally enforces cache,
 * coalescing, retry and the service's one-request-per-second policy.
 */
@Singleton
class PrayerLocationResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reverseGeocoder: NominatimReverseGeocoder,
) {
    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    suspend fun resolveAddressCandidate(): PrayerAddressCandidate? {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching { fused.lastLocation.await() }.getOrElse { error ->
            when (error) {
                is CancellationException -> throw error
                is SecurityException -> {
                    Timber.w(error, "Location permission rejected while reading last location")
                    null
                }

                is ApiException -> {
                    Timber.w(
                        error,
                        "Failed to fetch last known location (ApiException code=${error.statusCode})",
                    )
                    null
                }

                is IllegalStateException, is IllegalArgumentException -> {
                    Timber.w(error, "Failed to fetch last known location")
                    null
                }

                else -> throw error
            }
        } ?: return null

        return reverseGeocoder.reverse(
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }
}

data class PrayerAddressCandidate(
    val country: String,
    val city: String,
    val district: String,
)
