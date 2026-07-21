package com.parsfilo.contentapp.feature.content.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.content.data.ContentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var contentRepository: ContentRepository
    private lateinit var preferencesDataSource: PreferencesDataSource
    private lateinit var adGateChecker: AdGateChecker

    // ✅ EKLENDİ
    private lateinit var appAnalytics: AppAnalytics

    private val testVerses = listOf(
        Verse(id = 1, arabic = "بسم الله", latin = "Bismillah", turkish = "Allah'ın adıyla"),
        Verse(id = 2, arabic = "الحمد لله", latin = "Elhamdülillah", turkish = "Hamd Allah'a"),
    )

    private val defaultPrefs = UserPreferencesData(
        darkMode = false,
        displayMode = "ARABIC",
        fontSize = 24,
        developerModeEnabled = false,
        isPremium = false,
        lastAppOpenAdShown = 0L,
        rewardedAdFreeUntil = 0L,
        rewardWatchCount = 0,
        lastInterstitialShown = 0L,
        lastRewardedInterstitialShown = 0L,
        notificationsEnabled = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        contentRepository = mockk()
        preferencesDataSource = mockk(relaxed = true)
        adGateChecker = mockk()

        // ✅ EKLENDİ (relaxed: true -> çağrılsa bile exception fırlatmaz)
        appAnalytics = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ContentViewModel {
        return ContentViewModel(
            contentRepository = contentRepository,
            preferencesDataSource = preferencesDataSource,
            adGateChecker = adGateChecker,
            appAnalytics = appAnalytics // ✅ EKLENDİ
        )
    }

    @Test
    fun `initial state is Loading`() = runTest(testDispatcher) {
        coEvery { contentRepository.getVerses() } returns Result.Success(testVerses)
        every { preferencesDataSource.userData } returns flowOf(defaultPrefs)
        every { adGateChecker.shouldShowAds } returns flowOf(true)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(ContentUiState.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState emits Success with correct data`() = runTest(testDispatcher) {
        coEvery { contentRepository.getVerses() } returns Result.Success(testVerses)
        every { preferencesDataSource.userData } returns flowOf(defaultPrefs)
        every { adGateChecker.shouldShowAds } returns flowOf(true)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Skip Loading
            assertThat(awaitItem()).isEqualTo(ContentUiState.Loading)
            val success = awaitItem() as ContentUiState.Success
            assertThat(success.verses).isEqualTo(testVerses)
            assertThat(success.displayMode).isEqualTo(DisplayMode.ARABIC)
            assertThat(success.fontSize).isEqualTo(24)
            assertThat(success.shouldShowAds).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `premium user does not see ads`() = runTest(testDispatcher) {
        coEvery { contentRepository.getVerses() } returns Result.Success(testVerses)
        every { preferencesDataSource.userData } returns flowOf(defaultPrefs.copy(isPremium = true))
        every { adGateChecker.shouldShowAds } returns flowOf(false)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // Loading
            val success = awaitItem() as ContentUiState.Success
            assertThat(success.shouldShowAds).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid displayMode falls back to ARABIC`() = runTest(testDispatcher) {
        coEvery { contentRepository.getVerses() } returns Result.Success(testVerses)
        every { preferencesDataSource.userData } returns flowOf(
            defaultPrefs.copy(displayMode = "INVALID_MODE")
        )
        every { adGateChecker.shouldShowAds } returns flowOf(true)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // Loading
            val success = awaitItem() as ContentUiState.Success
            assertThat(success.displayMode).isEqualTo(DisplayMode.ARABIC)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateDisplayMode calls preferencesDataSource`() = runTest(testDispatcher) {
        coEvery { contentRepository.getVerses() } returns Result.Success(testVerses)
        every { preferencesDataSource.userData } returns flowOf(defaultPrefs)
        every { adGateChecker.shouldShowAds } returns flowOf(true)

        val viewModel = createViewModel()
        viewModel.updateDisplayMode(DisplayMode.TURKISH)
        advanceUntilIdle()

        coVerify { preferencesDataSource.setDisplayMode("TURKISH") }
    }

    @Test
    fun `uiState reacts to preference changes`() = runTest(testDispatcher) {
        val prefsFlow = MutableStateFlow(defaultPrefs)

        coEvery { contentRepository.getVerses() } returns Result.Success(testVerses)
        every { preferencesDataSource.userData } returns prefsFlow
        every { adGateChecker.shouldShowAds } returns flowOf(true)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // Loading
            val first = awaitItem() as ContentUiState.Success
            assertThat(first.fontSize).isEqualTo(24)

            // Update preferences
            prefsFlow.value = defaultPrefs.copy(fontSize = 32)
            val updated = awaitItem() as ContentUiState.Success
            assertThat(updated.fontSize).isEqualTo(32)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repository error emits Error state`() = runTest(testDispatcher) {
        val exception = RuntimeException("Network error")
        coEvery { contentRepository.getVerses() } returns Result.Error(exception)
        every { preferencesDataSource.userData } returns flowOf(defaultPrefs)
        every { adGateChecker.shouldShowAds } returns flowOf(true)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // Loading
            val error = awaitItem() as ContentUiState.Error
            assertThat(error.throwable).isInstanceOf(RuntimeException::class.java)
            assertThat(error.throwable.message).isEqualTo(exception.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
