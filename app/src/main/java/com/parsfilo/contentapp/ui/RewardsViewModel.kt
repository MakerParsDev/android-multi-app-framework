package com.parsfilo.contentapp.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AnalyticsEventName
import com.parsfilo.contentapp.core.firebase.AnalyticsParamKey
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.billing.BillingManager
import com.parsfilo.contentapp.feature.billing.model.BillingProduct
import com.parsfilo.contentapp.monetization.AdOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RewardsViewModel
    @Inject
    constructor(
        private val adOrchestrator: AdOrchestrator,
        private val billingManager: BillingManager,
        private val preferencesDataSource: PreferencesDataSource,
        private val appAnalytics: AppAnalytics,
    ) : ViewModel() {
        private fun logDebug(
            message: String,
            vararg args: Any?,
        ) {
            Timber.tag("timber_log").d(message, *args)
        }

        private fun logWarn(
            message: String,
            vararg args: Any?,
        ) {
            Timber.tag("timber_log").w(message, *args)
        }

        private val _isAdLoading = MutableStateFlow(false)
        val isAdLoading: StateFlow<Boolean> = _isAdLoading.asStateFlow()

        val uiState: StateFlow<RewardsUiState> =
            combine(
                preferencesDataSource.userData,
                billingManager.subscriptionState,
                billingManager.productDetails,
            ) { prefs, subState, products ->
                val now = System.currentTimeMillis()
                val remainingMs = (prefs.rewardedAdFreeUntil - now).coerceAtLeast(0L)

                RewardsUiState(
                    remainingAdFreeMs = remainingMs,
                    watchCount = prefs.rewardWatchCount,
                    nextRewardMinutes =
                        AdGateChecker.calculateRewardMinutes(
                            prefs.rewardWatchCount + 1,
                        ),
                    isPremium = prefs.isPremium,
                    subscriptionState = subState,
                    productDetails = products,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RewardsUiState(),
            )

        // Countdown timer — kalan süreyi her saniye güncelle
        private val _remainingSeconds = MutableStateFlow(0L)
        val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

        init {
            viewModelScope.launch {
                while (isActive) {
                    val prefs = preferencesDataSource.userData.first()
                    val remaining =
                        (prefs.rewardedAdFreeUntil - System.currentTimeMillis())
                            .coerceAtLeast(0L)
                    _remainingSeconds.value = remaining / 1000
                    delay(1000L)
                }
            }
        }

        fun watchRewardedAd(activity: Activity) {
            logDebug(
                "Rewarded funnel: watch tapped adReadyNow=%s",
                adOrchestrator.rewardedAdIsReadyNow(),
            )
            if (adOrchestrator.rewardedAdIsReadyNow()) {
                _isAdLoading.value = false
                logDebug("Rewarded funnel: showing immediately")
                showRewardedAd(activity)
                return
            }

            _isAdLoading.value = true
            logDebug("Rewarded funnel: requesting on-demand rewarded load")
            adOrchestrator.requestRewardedLoad(
                context = activity,
                route = "rewards",
            )
            viewModelScope.launch {
                val adReady =
                    withTimeoutOrNull(10_000L) {
                        adOrchestrator.rewardedAdIsReady
                            .filter { it }
                            .first()
                    } != null
                _isAdLoading.value = false

                if (adReady) {
                    if (activity.isFinishing || activity.isDestroyed) {
                        logWarn("Rewarded funnel: activity invalid after timeout, aborting show")
                    } else {
                        logDebug("Rewarded funnel: ad ready after on-demand load, showing")
                        showRewardedAd(activity)
                    }
                } else {
                    logWarn("Rewarded funnel: ad not ready after 10s wait")
                }
            }
        }

        private fun showRewardedAd(activity: Activity) {
            data class RewardOutcome(
                val rewardMinutesEarned: Int,
                val totalWatchCount: Int,
            )
            val watchStartedAtMs = System.currentTimeMillis()
            val rewardOutcomeDeferred = CompletableDeferred<RewardOutcome?>()
            logDebug("Rewarded funnel: showRewardedIfEligible invoked")
            viewModelScope.launch {
                adOrchestrator.showRewardedIfEligible(
                    activity = activity,
                    onUserEarnedReward = {
                        viewModelScope.launch {
                            val currentPrefs = preferencesDataSource.userData.first()
                            val rewardMinutesEarned =
                                AdGateChecker.calculateRewardMinutes(
                                    currentPrefs.rewardWatchCount + 1,
                                )
                            // adGateChecker.onRewardEarned() is handled by AdOrchestrator
                            val latestPrefs = preferencesDataSource.userData.first()
                            if (!rewardOutcomeDeferred.isCompleted) {
                                logDebug(
                                    "Rewarded funnel: reward earned minutes=%d watchCount=%d",
                                    rewardMinutesEarned,
                                    latestPrefs.rewardWatchCount,
                                )
                                rewardOutcomeDeferred.complete(
                                    RewardOutcome(
                                        rewardMinutesEarned = rewardMinutesEarned,
                                        totalWatchCount = latestPrefs.rewardWatchCount,
                                    ),
                                )
                            }
                        }
                    },
                    onAdDismissed = {
                        val watchDurationSeconds =
                            ((System.currentTimeMillis() - watchStartedAtMs).coerceAtLeast(0L) / 1000L)
                        viewModelScope.launch {
                            val rewardOutcome =
                                withTimeoutOrNull(1500L) {
                                    rewardOutcomeDeferred.await()
                                }
                            val latestPrefs = preferencesDataSource.userData.first()
                            if (rewardOutcome != null) {
                                appAnalytics.logEvent(
                                    AnalyticsEventName.REWARDED_WATCH_COMPLETE,
                                    android.os.Bundle().apply {
                                        putLong(
                                            AnalyticsParamKey.WATCH_DURATION_S,
                                            watchDurationSeconds,
                                        )
                                        putLong(
                                            AnalyticsParamKey.REWARD_MINUTES_EARNED,
                                            rewardOutcome.rewardMinutesEarned.toLong(),
                                        )
                                        putLong(
                                            AnalyticsParamKey.TOTAL_WATCH_COUNT,
                                            rewardOutcome.totalWatchCount.toLong(),
                                        )
                                    },
                                )
                            } else {
                                appAnalytics.logEvent(
                                    AnalyticsEventName.REWARDED_WATCH_SKIPPED,
                                    android.os.Bundle().apply {
                                        putLong(
                                            AnalyticsParamKey.WATCH_DURATION_S,
                                            watchDurationSeconds,
                                        )
                                        putLong(
                                            AnalyticsParamKey.TOTAL_WATCH_COUNT,
                                            latestPrefs.rewardWatchCount.toLong(),
                                        )
                                    },
                                )
                            }
                            logDebug(
                                "Rewarded funnel: ad dismissed rewarded=%s premium=%s",
                                rewardOutcome != null,
                                latestPrefs.isPremium,
                            )
                            // Post-dismiss reload is handled by AdOrchestrator.showRewardedIfEligible
                        }
                    },
                )
            }
        }

        fun launchBillingFlow(
            activity: Activity,
            billingProduct: BillingProduct,
        ) {
            billingManager.launchBillingFlow(activity, billingProduct)
        }
    }

data class RewardsUiState(
    val remainingAdFreeMs: Long = 0L,
    val watchCount: Int = 0,
    val nextRewardMinutes: Int = 30,
    val isPremium: Boolean = false,
    val subscriptionState: SubscriptionState = SubscriptionState.Loading,
    val productDetails: List<BillingProduct> = emptyList(),
)
