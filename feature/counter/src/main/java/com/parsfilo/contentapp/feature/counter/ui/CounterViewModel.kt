package com.parsfilo.contentapp.feature.counter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.counter.alarm.ReminderScheduleMode
import com.parsfilo.contentapp.feature.counter.alarm.ZikirReminderNotifier
import com.parsfilo.contentapp.feature.counter.alarm.ZikirReminderScheduler
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import com.parsfilo.contentapp.feature.counter.model.CounterHapticEvent
import com.parsfilo.contentapp.feature.counter.model.CounterUiState
import com.parsfilo.contentapp.feature.counter.model.ReminderSettings
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import com.parsfilo.contentapp.feature.counter.sharing.ZikirShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CounterViewModel @Inject constructor(
    private val zikirRepository: ZikirRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val zikirReminderScheduler: ZikirReminderScheduler,
    private val zikirReminderNotifier: ZikirReminderNotifier,
    private val zikirShareHelper: ZikirShareHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CounterUiState())
    val uiState = _uiState.asStateFlow()

    private val _hapticEvents = MutableSharedFlow<CounterHapticEvent>(extraBufferCapacity = 1)
    val hapticEvents = _hapticEvents.asSharedFlow()

    private val _interstitialEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val interstitialEvents = _interstitialEvents.asSharedFlow()

    private val _reminderUiEvents = MutableSharedFlow<CounterReminderUiEvent>(extraBufferCapacity = 1)
    val reminderUiEvents = _reminderUiEvents.asSharedFlow()

    private val isPremiumFlow = preferencesDataSource.userData.map { it.isPremium }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        observePreferences()
        observeSessions()
        observeStats()
        observeStreak()
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedDefaultKeys = parseDeletedZikirKeys(preferencesDataSource.deletedZikirKeysJson.first())
            val baseZikirList = zikirRepository.getZikirList()
                .filterNot { it.key in deletedDefaultKeys }
            val customItems = parseCustomZikirItems(preferencesDataSource.customZikirItemsJson.first())
            val zikirList = mergeZikirLists(
                baseItems = baseZikirList,
                customItems = customItems,
            )
            val selectedKey = preferencesDataSource.lastSelectedZikirKey.first()
            val selected = zikirList.firstOrNull { it.key == selectedKey } ?: zikirList.firstOrNull()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                zikirList = zikirList,
                selectedZikir = selected,
                targetCount = selected?.defaultTarget ?: _uiState.value.targetCount,
                // Selector should be the default entry point; this also recovers
                // the app when the user deletes every zikir and needs to add one back.
                showZikirSelector = true,
            )
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            val hapticSoundFlow = combine(
                preferencesDataSource.zikirHapticEnabled,
                preferencesDataSource.zikirSoundEnabled,
            ) { isHapticEnabled, isSoundEnabled ->
                isHapticEnabled to isSoundEnabled
            }

            val reminderFlow = combine(
                preferencesDataSource.zikirReminderEnabled,
                preferencesDataSource.zikirReminderHour,
                preferencesDataSource.zikirReminderMinute,
            ) { reminderEnabled, reminderHour, reminderMinute ->
                Triple(reminderEnabled, reminderHour, reminderMinute)
            }

            val goalFlow = combine(
                preferencesDataSource.zikirDailyGoal,
                preferencesDataSource.zikirStreakReminderEnabled,
            ) { dailyGoal, streakReminderEnabled ->
                dailyGoal to streakReminderEnabled
            }

            combine(
                hapticSoundFlow,
                reminderFlow,
                goalFlow,
                isPremiumFlow,
            ) { hapticSound, reminder, goal, isPremium ->
                PreferenceSnapshot(
                    isHapticEnabled = hapticSound.first,
                    isSoundEnabled = hapticSound.second,
                    reminderEnabled = reminder.first,
                    reminderHour = reminder.second,
                    reminderMinute = reminder.third,
                    dailyGoal = goal.first,
                    streakReminderEnabled = goal.second,
                    isPremium = isPremium,
                )
            }.collectLatest { snapshot ->
                val settings = ReminderSettings(
                    enabled = snapshot.reminderEnabled,
                    hour = snapshot.reminderHour,
                    minute = snapshot.reminderMinute,
                    dailyGoal = snapshot.dailyGoal,
                    streakReminderEnabled = snapshot.streakReminderEnabled,
                )
                _uiState.value = _uiState.value.copy(
                    isHapticEnabled = snapshot.isHapticEnabled,
                    isSoundEnabled = snapshot.isSoundEnabled,
                    reminderSettings = settings,
                    dailyGoal = snapshot.dailyGoal,
                    isPremium = snapshot.isPremium,
                    dailyGoalProgress = progress(_uiState.value.todayTotalCount, snapshot.dailyGoal),
                )
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            zikirRepository.getRecentSessions(limit = 100).collectLatest { sessions ->
                _uiState.value = _uiState.value.copy(recentSessions = sessions)
            }
        }
    }

    private fun observeStats() {
        viewModelScope.launch {
            combine(
                zikirRepository.getTodayTotalCount(),
                zikirRepository.getTodayCompletedSessionCount(),
                preferencesDataSource.zikirDailyGoal,
            ) { todayTotal, completedSessions, dailyGoal ->
                Triple(todayTotal, completedSessions, dailyGoal)
            }.collectLatest { (todayTotal, completedSessions, dailyGoal) ->
                _uiState.value = _uiState.value.copy(
                    todayTotalCount = todayTotal,
                    completedSessionsToday = completedSessions,
                    dailyGoal = dailyGoal,
                    dailyGoalProgress = progress(todayTotal, dailyGoal),
                )
            }
        }
    }

    private fun observeStreak() {
        viewModelScope.launch {
            zikirRepository.observeStreak().collectLatest { streak ->
                _uiState.value = _uiState.value.copy(
                    currentStreak = streak.currentStreak,
                    longestStreak = streak.longestStreak,
                )
            }
        }
    }

    fun onCounterTapped() {
        val state = _uiState.value
        val selected = state.selectedZikir ?: return
        val now = System.currentTimeMillis()

        val newCount = state.currentCount + 1
        val sessionStart = if (!state.isSessionActive || state.sessionStartTime == 0L) now else state.sessionStartTime

        val hapticEvent = when {
            newCount >= state.targetCount -> CounterHapticEvent.TARGET
            newCount % 10 == 0 -> CounterHapticEvent.TEN
            else -> CounterHapticEvent.TAP
        }
        _hapticEvents.tryEmit(hapticEvent)

        if (newCount >= state.targetCount) {
            val durationSeconds = ((now - sessionStart) / 1000L).coerceAtLeast(0L)
            val session = ZikirSession(
                zikirKey = selected.key,
                arabicText = selected.arabicText,
                latinText = selected.latinText,
                targetCount = state.targetCount,
                completedCount = newCount,
                completedAt = now,
                durationSeconds = durationSeconds,
                isComplete = true,
            )

            _uiState.value = state.copy(
                currentCount = newCount,
                isSessionActive = true,
                sessionStartTime = sessionStart,
                showSessionComplete = true,
                lastCompletedSession = session,
            )

            viewModelScope.launch(Dispatchers.IO) {
                zikirRepository.saveSession(session)
                zikirRepository.updateStreakAfterSession()

                val completedSessionsToday = zikirRepository.getTodayCompletedSessionCount().first()
                if (completedSessionsToday == 1) {
                    val reminderEnabled = preferencesDataSource.zikirReminderEnabled.first()
                    if (!reminderEnabled) {
                        _uiState.value = _uiState.value.copy(showFirstSessionReminderHint = true)
                    }
                }

                val previousToday = state.todayTotalCount
                val dailyGoal = _uiState.value.dailyGoal
                val afterToday = previousToday + session.completedCount
                if (previousToday < dailyGoal && afterToday >= dailyGoal) {
                    zikirReminderNotifier.showGoalCompleted(dailyGoal)
                }
            }
        } else {
            _uiState.value = state.copy(
                currentCount = newCount,
                isSessionActive = true,
                sessionStartTime = sessionStart,
            )
        }
    }

    fun onZikirSelected(item: ZikirItem) {
        _uiState.value = _uiState.value.copy(
            selectedZikir = item,
            targetCount = item.defaultTarget,
            currentCount = 0,
            isSessionActive = false,
            sessionStartTime = 0L,
            showZikirSelector = false,
        )
        viewModelScope.launch {
            preferencesDataSource.setLastSelectedZikirKey(item.key)
        }
    }

    fun onAddCustomZikir(
        arabicText: String,
        latinText: String,
        turkishMeaning: String,
        defaultTarget: Int,
    ) {
        val arabic = arabicText.trim()
        val latin = latinText.trim()
        val meaning = turkishMeaning.trim()
        if (arabic.isBlank() && latin.isBlank()) return

        val normalizedTarget = defaultTarget.coerceAtLeast(1)
        val item = ZikirItem(
            key = "$CUSTOM_ZIKIR_KEY_PREFIX${System.currentTimeMillis()}",
            arabicText = arabic,
            latinText = if (latin.isNotBlank()) latin else arabic,
            turkishMeaning = meaning,
            defaultTarget = normalizedTarget,
            virtue = "",
            virtueSource = "",
        )

        val baseItems = _uiState.value.zikirList.filterNot { it.key.startsWith(CUSTOM_ZIKIR_KEY_PREFIX) }
        val currentCustomItems = _uiState.value.zikirList.filter { it.key.startsWith(CUSTOM_ZIKIR_KEY_PREFIX) }
        val updatedList = mergeZikirLists(
            baseItems = baseItems,
            customItems = currentCustomItems + item,
        )
        _uiState.value = _uiState.value.copy(
            zikirList = updatedList,
            selectedZikir = item,
            targetCount = normalizedTarget,
            currentCount = 0,
            isSessionActive = false,
            sessionStartTime = 0L,
            showZikirSelector = false,
        )

        viewModelScope.launch(Dispatchers.IO) {
            preferencesDataSource.setCustomZikirItemsJson(serializeCustomZikirItems(updatedList))
            preferencesDataSource.setLastSelectedZikirKey(item.key)
        }
    }

    fun onDeleteZikir(item: ZikirItem) {
        val state = _uiState.value
        if (state.zikirList.none { it.key == item.key }) return

        val updatedList = state.zikirList.filterNot { it.key == item.key }
        val nextSelected = if (state.selectedZikir?.key == item.key) {
            updatedList.firstOrNull()
        } else {
            state.selectedZikir
        }

        _uiState.value = state.copy(
            zikirList = updatedList,
            selectedZikir = nextSelected,
            targetCount = nextSelected?.defaultTarget ?: state.targetCount,
            currentCount = 0,
            isSessionActive = false,
            sessionStartTime = 0L,
            showZikirSelector = true,
        )

        viewModelScope.launch(Dispatchers.IO) {
            if (item.key.startsWith(CUSTOM_ZIKIR_KEY_PREFIX)) {
                preferencesDataSource.setCustomZikirItemsJson(serializeCustomZikirItems(updatedList))
            } else {
                val deletedDefaultKeys = parseDeletedZikirKeys(preferencesDataSource.deletedZikirKeysJson.first())
                preferencesDataSource.setDeletedZikirKeysJson(
                    serializeDeletedZikirKeys(deletedDefaultKeys + item.key),
                )
            }
            preferencesDataSource.setLastSelectedZikirKey(nextSelected?.key ?: DEFAULT_FALLBACK_ZIKIR_KEY)
        }
    }

    fun onResetCurrentCount() {
        _uiState.value = _uiState.value.copy(
            currentCount = 0,
            isSessionActive = false,
            sessionStartTime = 0L,
        )
    }

    fun onTargetChanged(newTarget: Int) {
        _uiState.value = _uiState.value.copy(targetCount = newTarget.coerceAtLeast(1))
    }

    fun onCustomTargetSelected(target: Int) {
        onTargetChanged(target)
    }

    fun onDismissSessionComplete() {
        val isPremium = _uiState.value.isPremium
        _uiState.value = _uiState.value.copy(
            showSessionComplete = false,
            currentCount = 0,
            isSessionActive = false,
            sessionStartTime = 0L,
        )

        if (!isPremium) {
            viewModelScope.launch {
                if (consumeLocalInterstitialQuota()) {
                    _interstitialEvents.emit(Unit)
                }
            }
        }
    }

    fun onZikirSelectorToggle() {
        _uiState.value = _uiState.value.copy(showZikirSelector = !_uiState.value.showZikirSelector)
    }

    fun onReminderSettingsToggle() {
        _uiState.value = _uiState.value.copy(showReminderSettings = !_uiState.value.showReminderSettings)
    }

    fun onFirstSessionReminderAction() {
        _uiState.value = _uiState.value.copy(
            showFirstSessionReminderHint = false,
            showReminderSettings = true,
        )
    }

    fun onFirstSessionReminderConsumed() {
        _uiState.value = _uiState.value.copy(showFirstSessionReminderHint = false)
    }

    fun onSessionHistoryToggle() {
        _uiState.value = _uiState.value.copy(showSessionHistory = !_uiState.value.showSessionHistory)
    }

    fun onSessionHistoryDismissed() {
        _uiState.value = _uiState.value.copy(
            showSessionHistory = false,
            historyUnlockedForSession = false,
        )
    }

    fun toggleHaptic() {
        viewModelScope.launch {
            preferencesDataSource.setZikirHapticEnabled(!_uiState.value.isHapticEnabled)
        }
    }

    fun toggleSound() {
        viewModelScope.launch {
            preferencesDataSource.setZikirSoundEnabled(!_uiState.value.isSoundEnabled)
        }
    }

    fun onReminderSaved(settings: ReminderSettings) {
        viewModelScope.launch {
            Timber.d(
                "Reminder settings saved enabled=%s hour=%d minute=%d dailyGoal=%d streakReminder=%s",
                settings.enabled,
                settings.hour,
                settings.minute,
                settings.dailyGoal,
                settings.streakReminderEnabled,
            )
            preferencesDataSource.setZikirReminderEnabled(settings.enabled)
            preferencesDataSource.setZikirReminderHour(settings.hour)
            preferencesDataSource.setZikirReminderMinute(settings.minute)
            preferencesDataSource.setZikirDailyGoal(settings.dailyGoal)
            preferencesDataSource.setZikirStreakReminderEnabled(settings.streakReminderEnabled)
            if (settings.enabled) {
                val scheduleMode = zikirReminderScheduler.scheduleDaily(settings.hour, settings.minute)
                Timber.d("Reminder schedule result=%s", scheduleMode)
                if (scheduleMode == ReminderScheduleMode.INEXACT_FALLBACK &&
                    zikirReminderScheduler.canRequestExactAlarmPermission()
                ) {
                    Timber.d("Exact alarm permission required; emitting reminder permission request event")
                    _reminderUiEvents.emit(CounterReminderUiEvent.RequestExactAlarmPermission)
                }
            } else {
                zikirReminderScheduler.cancel()
            }
            zikirReminderScheduler.scheduleStreakCheckWorker()
            _uiState.value = _uiState.value.copy(showReminderSettings = false)
        }
    }

    fun onExactAlarmPermissionSettingsReturned() {
        viewModelScope.launch {
            if (!zikirReminderScheduler.canRequestExactAlarmPermission()) {
                val scheduleMode = zikirReminderScheduler.scheduleOrCancelFromPreferences()
                Timber.d("Exact alarm settings returned: permission granted, reschedule result=%s", scheduleMode)
                _reminderUiEvents.emit(
                    CounterReminderUiEvent.ExactAlarmPermissionGranted
                )
            } else {
                Timber.d("Exact alarm settings returned: permission still missing")
                _reminderUiEvents.emit(
                    CounterReminderUiEvent.ExactAlarmPermissionStillMissing
                )
            }
        }
    }

    fun onDailyGoalChanged(goal: Int) {
        viewModelScope.launch {
            preferencesDataSource.setZikirDailyGoal(goal)
        }
    }

    fun onShareSession() {
        val state = _uiState.value
        val session = state.lastCompletedSession
        val selectedZikir = state.selectedZikir ?: state.zikirList.firstOrNull() ?: return
        val latinText = session?.latinText ?: selectedZikir.latinText
        val arabicText = session?.arabicText ?: selectedZikir.arabicText
        val completedCount = session?.completedCount ?: state.currentCount

        val shareText = zikirShareHelper.buildShareText(
            latinText = latinText,
            arabicText = arabicText,
            completedCount = completedCount,
            todayTotal = state.todayTotalCount,
            currentStreak = state.currentStreak,
        )
        _uiState.value = state.copy(
            shareText = shareText,
            showSharePreview = true,
        )
    }

    fun onShareConfirmed() {
        val text = _uiState.value.shareText
        if (text.isNotBlank()) {
            zikirShareHelper.shareText(text)
        }
    }

    fun onShareDismissed() {
        _uiState.value = _uiState.value.copy(showSharePreview = false)
    }

    fun onShareTextCopied() {
        _uiState.value = _uiState.value.copy(showSharePreview = false)
    }

    fun onRewardedHistoryUnlocked() {
        _uiState.value = _uiState.value.copy(historyUnlockedForSession = true)
    }

    private suspend fun consumeLocalInterstitialQuota(): Boolean {
        val now = System.currentTimeMillis()
        val todayKey = dayKey(now)

        val storedDayKey = preferencesDataSource.zikirInterstitialDayKey.first()
        val lastAt = preferencesDataSource.zikirLastInterstitialAt.first()
        var shownCount = preferencesDataSource.zikirInterstitialShownCountDay.first()

        if (storedDayKey != todayKey) {
            shownCount = 0
        }

        if (now - lastAt < MIN_INTERSTITIAL_GAP_MS) return false
        if (shownCount >= MAX_INTERSTITIAL_PER_DAY) return false

        preferencesDataSource.setZikirInterstitialDayKey(todayKey)
        preferencesDataSource.setZikirInterstitialShownCountDay(shownCount + 1)
        preferencesDataSource.setZikirLastInterstitialAt(now)
        return true
    }

    private fun progress(todayTotal: Int, dailyGoal: Int): Float {
        if (dailyGoal <= 0) return 0f
        return (todayTotal.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    }

    private fun dayKey(nowMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nowMs)
    }

    private fun parseCustomZikirItems(raw: String): List<ZikirItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val key = obj.optString("key")
                    if (!key.startsWith(CUSTOM_ZIKIR_KEY_PREFIX)) continue
                    add(
                        ZikirItem(
                            key = key,
                            arabicText = obj.optString("arabicText"),
                            latinText = obj.optString("latinText"),
                            turkishMeaning = obj.optString("turkishMeaning"),
                            defaultTarget = obj.optInt("defaultTarget", 33).coerceAtLeast(1),
                            virtue = obj.optString("virtue"),
                            virtueSource = obj.optString("virtueSource"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeCustomZikirItems(items: List<ZikirItem>): String {
        val array = JSONArray()
        items
            .filter { it.key.startsWith(CUSTOM_ZIKIR_KEY_PREFIX) }
            .forEach { item ->
                val obj = JSONObject().apply {
                    put("key", item.key)
                    put("arabicText", item.arabicText)
                    put("latinText", item.latinText)
                    put("turkishMeaning", item.turkishMeaning)
                    put("defaultTarget", item.defaultTarget)
                    put("virtue", item.virtue)
                    put("virtueSource", item.virtueSource)
                }
                array.put(obj)
            }
        return array.toString()
    }

    private fun parseDeletedZikirKeys(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    val key = array.optString(index).trim()
                    if (key.isBlank() || key.startsWith(CUSTOM_ZIKIR_KEY_PREFIX)) continue
                    add(key)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun serializeDeletedZikirKeys(keys: Set<String>): String {
        val array = JSONArray()
        keys.asSequence()
            .filter { it.isNotBlank() && !it.startsWith(CUSTOM_ZIKIR_KEY_PREFIX) }
            .sorted()
            .forEach(array::put)
        return array.toString()
    }

    private fun mergeZikirLists(
        baseItems: List<ZikirItem>,
        customItems: List<ZikirItem>,
    ): List<ZikirItem> {
        val sortedCustomItems = customItems
            .filter { it.key.startsWith(CUSTOM_ZIKIR_KEY_PREFIX) }
            .distinctBy { it.key }
            .sortedByDescending(::customZikirSortValue)

        return (sortedCustomItems + baseItems).distinctBy { it.key }
    }

    private fun customZikirSortValue(item: ZikirItem): Long {
        return item.key
            .removePrefix(CUSTOM_ZIKIR_KEY_PREFIX)
            .toLongOrNull()
            ?: Long.MIN_VALUE
    }

    private data class PreferenceSnapshot(
        val isHapticEnabled: Boolean,
        val isSoundEnabled: Boolean,
        val reminderEnabled: Boolean,
        val reminderHour: Int,
        val reminderMinute: Int,
        val dailyGoal: Int,
        val streakReminderEnabled: Boolean,
        val isPremium: Boolean,
    )

    companion object {
        private const val MIN_INTERSTITIAL_GAP_MS = 15 * 60 * 1000L
        private const val MAX_INTERSTITIAL_PER_DAY = 2
        private const val CUSTOM_ZIKIR_KEY_PREFIX = "custom_"
        private const val DEFAULT_FALLBACK_ZIKIR_KEY = "subhanallah"
    }
}
