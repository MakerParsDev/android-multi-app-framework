package com.parsfilo.contentapp.feature.ads

import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merkezi reklam gösterim kontrolü.
 *
 * Reklam gösterilmez eğer:
 * - Kullanıcı premium aboneyse (isPremium)
 * - Ödüllü reklam izleme süresi aktifse (rewardedAdFreeUntil > now)
 */
@Singleton
class AdGateChecker @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) {
    /** true → reklam gösterilebilir, false → reklam gösterme */
    val shouldShowAds: Flow<Boolean> = preferencesDataSource.userData.map { prefs ->
        !prefs.isPremium && !isRewardActive(prefs.rewardedAdFreeUntil)
    }

    private fun isRewardActive(rewardedAdFreeUntil: Long): Boolean {
        return rewardedAdFreeUntil > System.currentTimeMillis()
    }

    /**
     * Ödüllü reklam izlendikten sonra çağrılır.
     * Kademeli süre artışı:
     *   1. izleme → 30 dk
     *   2. izleme → 60 dk
     *   3+ izleme → +30 dk (maks 90 dk)
     */
    suspend fun onRewardEarned() {
        val prefs = preferencesDataSource.userData.first()
        val newCount = prefs.rewardWatchCount + 1
        val rewardMinutes = calculateRewardMinutes(newCount)
        val expiryTimestamp = System.currentTimeMillis() + (rewardMinutes * 60 * 1000L)

        preferencesDataSource.setRewardWatchCount(newCount)
        preferencesDataSource.setRewardedAdFreeUntil(expiryTimestamp)
    }

    companion object {
        /**
         * Kademeli ödül süresi hesaplar.
         * 1. izleme → 30 dk
         * 2. izleme → 60 dk
         * 3. izleme → 90 dk
         * Tavan: 90 dk
         */
        fun calculateRewardMinutes(watchCount: Int): Int {
            return (watchCount * 30).coerceAtMost(90)
        }
    }
}
