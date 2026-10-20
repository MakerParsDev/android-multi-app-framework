package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AdGateCheckerTest {
    private val preferences = MutableStateFlow(userPreferences())
    private val dataSource = mockk<PreferencesDataSource> {
        every { userData } returns preferences
    }
    private val checker = AdGateChecker(dataSource)

    @Test
    fun `premium activation immediately blocks shared banner and native ad gate`() = runBlocking {
        assertThat(checker.shouldShowAds.first()).isTrue()

        preferences.value = userPreferences(isPremium = true)
        assertThat(checker.shouldShowAds.first()).isFalse()

        preferences.value = userPreferences()
        assertThat(checker.shouldShowAds.first()).isTrue()
    }

    @Test
    fun `rewarded ad free period blocks ads until expiry`() = runBlocking {
        preferences.value = userPreferences(rewardedAdFreeUntil = System.currentTimeMillis() + 60_000L)
        assertThat(checker.shouldShowAds.first()).isFalse()

        preferences.value = userPreferences(rewardedAdFreeUntil = System.currentTimeMillis() - 1_000L)
        assertThat(checker.shouldShowAds.first()).isTrue()
    }

    private fun userPreferences(
        isPremium: Boolean = false,
        rewardedAdFreeUntil: Long = 0L,
    ) = UserPreferencesData(
        darkMode = false,
        displayMode = "default",
        fontSize = 16,
        developerModeEnabled = false,
        isPremium = isPremium,
        lastAppOpenAdShown = 0L,
        rewardedAdFreeUntil = rewardedAdFreeUntil,
        rewardWatchCount = 0,
        lastInterstitialShown = 0L,
        lastRewardedInterstitialShown = 0L,
        notificationsEnabled = false,
    )
}
