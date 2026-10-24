package com.parsfilo.contentapp.feature.counter.ui

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity
import com.parsfilo.contentapp.core.datastore.ActiveZikirSessionSnapshot
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import com.parsfilo.contentapp.feature.counter.alarm.ZikirReminderNotifier
import com.parsfilo.contentapp.feature.counter.alarm.ZikirReminderScheduler
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import com.parsfilo.contentapp.feature.counter.model.CompletionPersistenceStatus
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import com.parsfilo.contentapp.feature.counter.sharing.ZikirShareHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CounterCompletionPersistenceTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository: ZikirRepository = mockk(relaxed = true)
    private val prefs: PreferencesDataSource = mockk(relaxed = true)
    private val reminderScheduler: ZikirReminderScheduler = mockk(relaxed = true)
    private val notifier: ZikirReminderNotifier = mockk(relaxed = true)
    private val share: ZikirShareHelper = mockk(relaxed = true)
    private val snapshots = MutableStateFlow<ActiveZikirSessionSnapshot?>(null)
    private val item = ZikirItem(
        key = "subhanallah",
        arabicText = "test",
        latinText = "test",
        turkishMeaning = "test",
        defaultTarget = 1,
        virtue = "",
        virtueSource = "",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.getZikirList() } returns listOf(item)
        every { repository.getRecentSessions(any()) } returns flowOf(emptyList())
        every { repository.getTodayTotalCount() } returns flowOf(0)
        every { repository.getTodayCompletedSessionCount() } returns flowOf(1)
        every { repository.observeStreak() } returns flowOf(ZikirStreakEntity())

        every { prefs.deletedZikirKeysJson } returns flowOf("[]")
        every { prefs.customZikirItemsJson } returns flowOf("[]")
        every { prefs.lastSelectedZikirKey } returns flowOf(item.key)
        every { prefs.activeZikirSessionSnapshot } returns snapshots
        coEvery { prefs.setActiveZikirSessionSnapshot(any()) } coAnswers {
            snapshots.value = firstArg()
        }
        coEvery { prefs.setActiveZikirSessionSnapshot(null) } coAnswers {
            snapshots.value = null
        }
        coEvery { prefs.clearActiveZikirSessionIfId(any()) } coAnswers {
            if (snapshots.value?.sessionId == firstArg<Long>()) snapshots.value = null
        }
        every { prefs.zikirHapticEnabled } returns flowOf(true)
        every { prefs.zikirSoundEnabled } returns flowOf(false)
        every { prefs.zikirReminderEnabled } returns flowOf(true)
        every { prefs.zikirReminderHour } returns flowOf(9)
        every { prefs.zikirReminderMinute } returns flowOf(0)
        every { prefs.zikirDailyGoal } returns flowOf(100)
        every { prefs.zikirStreakReminderEnabled } returns flowOf(false)
        every { prefs.userData } returns flowOf(
            UserPreferencesData(
                darkMode = false,
                displayMode = "ARABIC",
                fontSize = 20,
                developerModeEnabled = false,
                isPremium = true,
                lastAppOpenAdShown = 0L,
                rewardedAdFreeUntil = 0L,
                rewardWatchCount = 0,
                lastInterstitialShown = 0L,
                lastRewardedInterstitialShown = 0L,
                notificationsEnabled = false,
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CounterViewModel(
        zikirRepository = repository,
        preferencesDataSource = prefs,
        zikirReminderScheduler = reminderScheduler,
        zikirReminderNotifier = notifier,
        zikirShareHelper = share,
        ioDispatcher = dispatcher,
    )

    @Test
    fun partialCountSurvivesNewViewModelAndTargetRemains33() = runTest(dispatcher) {
        val withTarget33 = item.copy(defaultTarget = 33)
        every { repository.getZikirList() } returns listOf(withTarget33)
        val first = viewModel()
        runCurrent()
        repeat(17) { first.onCounterTapped() }
        runCurrent()
        assertThat(snapshots.value?.count).isEqualTo(17)
        assertThat(snapshots.value?.target).isEqualTo(33)
        val id = snapshots.value!!.sessionId
        first.viewModelScope.cancel()

        val recovered = viewModel()
        runCurrent()
        assertThat(recovered.uiState.value.currentCount).isEqualTo(17)
        assertThat(recovered.uiState.value.targetCount).isEqualTo(33)
        assertThat(recovered.uiState.value.activeSessionId).isEqualTo(id)
        assertThat(recovered.uiState.value.sessionStartTime).isEqualTo(snapshots.value!!.startedAt)
    }

    @Test
    fun explicitResetClearsSnapshotAndPreventsProgressResurrection() = runTest(dispatcher) {
        every { repository.getZikirList() } returns listOf(item.copy(defaultTarget = 33))
        val first = viewModel()
        runCurrent()
        repeat(17) { first.onCounterTapped() }
        runCurrent()
        assertThat(snapshots.value?.count).isEqualTo(17)
        first.onResetCurrentCount()
        runCurrent()
        assertThat(snapshots.value).isNull()
        first.viewModelScope.cancel()
        val recovered = viewModel()
        runCurrent()
        assertThat(recovered.uiState.value.currentCount).isEqualTo(0)
        assertThat(recovered.uiState.value.activeSessionId).isEqualTo(0L)
    }

    @Test
    fun loweringTargetBelowActiveCountCannotFabricateCompletionOnRestart() = runTest(dispatcher) {
        every { repository.getZikirList() } returns listOf(item.copy(defaultTarget = 33))
        val first = viewModel()
        runCurrent()
        repeat(17) { first.onCounterTapped() }
        runCurrent()
        first.onTargetChanged(10)
        runCurrent()
        assertThat(first.uiState.value.targetCount).isEqualTo(33)
        assertThat(snapshots.value!!.target).isEqualTo(33)
        first.viewModelScope.cancel()
        val recovered = viewModel()
        runCurrent()
        assertThat(recovered.uiState.value.targetCount).isEqualTo(33)
        assertThat(recovered.uiState.value.currentCount).isEqualTo(17)
        assertThat(recovered.uiState.value.showSessionComplete).isFalse()
        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun selectingAnotherZikirInvalidatesOnlyOldActiveSnapshot() = runTest(dispatcher) {
        val replacement = item.copy(key = "elhamdulillah", defaultTarget = 99)
        every { repository.getZikirList() } returns listOf(item.copy(defaultTarget = 33), replacement)
        val first = viewModel()
        runCurrent()
        repeat(17) { first.onCounterTapped() }
        runCurrent()
        assertThat(snapshots.value?.count).isEqualTo(17)
        first.onZikirSelected(replacement)
        runCurrent()
        assertThat(snapshots.value).isNull()
        assertThat(first.uiState.value.currentCount).isEqualTo(0)
        assertThat(first.uiState.value.activeSessionId).isEqualTo(0L)
    }

    @Test
    fun savedPendingCompletionReplaysExactIdAfterScopeCancellation() = runTest(dispatcher) {
        val saving = CompletableDeferred<Boolean>()
        coEvery { repository.saveSession(any()) } coAnswers { saving.await() }
        val first = viewModel()
        runCurrent()
        first.onCounterTapped()
        runCurrent()
        val pendingId = snapshots.value!!.sessionId
        assertThat(snapshots.value?.count).isEqualTo(1)
        first.viewModelScope.cancel()

        coEvery { repository.saveSession(any()) } returns true
        val restored = viewModel()
        runCurrent()
        assertThat(restored.uiState.value.lastCompletedSession?.id).isEqualTo(pendingId)
        assertThat(restored.uiState.value.completionPersistenceStatus)
            .isEqualTo(CompletionPersistenceStatus.SAVED)
        assertThat(snapshots.value).isNull()
        coVerify(atLeast = 1) {
            repository.saveSession(match { it.id == pendingId })
        }
    }

    @Test
    fun delayedInsertKeepsCountAndBlocksDismissalAndDuplicateTaps() = runTest(dispatcher) {
        val barrier = CompletableDeferred<Boolean>()
        coEvery { repository.saveSession(any()) } coAnswers { barrier.await() }
        val vm = viewModel()
        runCurrent()
        assertThat(vm.uiState.value.isLoading).isFalse()

        vm.onCounterTapped()
        vm.onCounterTapped()
        vm.onDismissSessionComplete()
        vm.onResetCurrentCount()
        runCurrent()

        assertThat(vm.uiState.value.currentCount).isEqualTo(1)
        assertThat(vm.uiState.value.completionPersistenceStatus)
            .isEqualTo(CompletionPersistenceStatus.SAVING)
        assertThat(vm.uiState.value.showSessionComplete).isTrue()
        val stableId = vm.uiState.value.lastCompletedSession!!.id
        assertThat(stableId).isGreaterThan(0L)
        coVerify(exactly = 1) { repository.saveSession(any()) }

        barrier.complete(true)
        runCurrent()
        assertThat(vm.uiState.value.completionPersistenceStatus)
            .isEqualTo(CompletionPersistenceStatus.SAVED)
        vm.onDismissSessionComplete()
        assertThat(vm.uiState.value.currentCount).isEqualTo(0)
        assertThat(vm.uiState.value.completionPersistenceStatus)
            .isEqualTo(CompletionPersistenceStatus.NONE)
    }

    @Test
    fun failedInsertPreservesProgressAndRetriesSameSessionId() = runTest(dispatcher) {
        val ids = mutableListOf<Long>()
        var attempt = 0
        coEvery { repository.saveSession(any()) } coAnswers {
            ids += firstArg<ZikirSession>().id
            attempt++
            if (attempt == 1) throw IOException("synthetic Room failure")
            true
        }
        val vm = viewModel()
        runCurrent()
        vm.onCounterTapped()
        runCurrent()
        assertThat(vm.uiState.value.completionPersistenceStatus)
            .isEqualTo(CompletionPersistenceStatus.FAILED)
        vm.onDismissSessionComplete()
        assertThat(vm.uiState.value.currentCount).isEqualTo(1)

        vm.retryCompletedSessionSave()
        vm.retryCompletedSessionSave()
        runCurrent()
        assertThat(ids).hasSize(2)
        assertThat(ids[0]).isEqualTo(ids[1])
        assertThat(vm.uiState.value.completionPersistenceStatus)
            .isEqualTo(CompletionPersistenceStatus.SAVED)
        coVerify(exactly = 2) { repository.saveSession(any()) }
    }

    @Test
    fun alreadyCommittedRetryIsAcknowledgedWithoutDuplicateEffects() = runTest(dispatcher) {
        coEvery { repository.saveSession(any()) } returns false
        val vm = viewModel()
        runCurrent()
        vm.onCounterTapped()
        runCurrent()
        assertThat(vm.uiState.value.completionPersistenceStatus)
            .isEqualTo(CompletionPersistenceStatus.SAVED)
        coVerify(exactly = 1) { repository.saveSession(any()) }
    }
}
