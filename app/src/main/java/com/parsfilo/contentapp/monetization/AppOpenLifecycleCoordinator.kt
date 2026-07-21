package com.parsfilo.contentapp.monetization

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.parsfilo.contentapp.feature.ads.AdSuppressReason
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.AppOpenTriggerReason
import com.parsfilo.contentapp.feature.ads.suppressReasonWhenBlocked
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenLifecycleCoordinator
    @Inject
    constructor(
        private val adOrchestrator: AdOrchestrator,
    ) : Application.ActivityLifecycleCallbacks,
        DefaultLifecycleObserver {
        @Volatile
        private var currentActivity: Activity? = null

        private val isRegistered = AtomicBoolean(false)
        private val pendingForegroundRequest = AtomicBoolean(false)
        private val firstForegroundHandled = AtomicBoolean(false)

        fun register(application: Application) {
            if (isRegistered.getAndSet(true)) {
                Timber.d("AppOpenLifecycleCoordinator already registered")
                return
            }
            application.registerActivityLifecycleCallbacks(this)
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            Timber.d("AppOpenLifecycleCoordinator registered")
        }

        override fun onStart(owner: LifecycleOwner) {
            pendingForegroundRequest.set(true)
            Timber.d("Process onStart: app-open request queued for next resumed activity")
        }

        override fun onStop(owner: LifecycleOwner) {
            Timber.d("Process onStop: notifying app paused")
            adOrchestrator.onAppPaused(currentActivity?.applicationContext)
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) {
            if (!adOrchestrator.isAppOpenAdShowing()) {
                currentActivity = activity
                Timber.d(
                    "Activity started for app-open tracking=%s",
                    activity::class.java.simpleName,
                )
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (adOrchestrator.isAppOpenAdShowing()) return
            currentActivity = activity
            if (pendingForegroundRequest.compareAndSet(true, false)) {
                val triggerReason =
                    if (firstForegroundHandled.compareAndSet(false, true)) {
                        AppOpenTriggerReason.COLD_START
                    } else {
                        AppOpenTriggerReason.RESUME
                    }
                requestAppOpen(
                    activity = activity,
                    source = "activity_resumed_after_process_on_start",
                    triggerReason = triggerReason,
                )
            }
        }

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivity === activity) {
                Timber.d("Tracked activity destroyed=%s", activity::class.java.simpleName)
                currentActivity = null
            }
        }

        private fun requestAppOpen(
            activity: Activity,
            source: String,
            triggerReason: AppOpenTriggerReason,
        ) {
            Timber.d(
                "Requesting app-open source=%s activity=%s",
                source,
                activity::class.java.simpleName,
            )
            ProcessLifecycleOwner.get().lifecycleScope.launch {
                runCatching {
                    delay(350L)
                    requestAfterForegroundDelay(activity, triggerReason)
                }.onFailure { error ->
                    Timber.w(error, "Failed to show app open ad source=%s", source)
                }
            }
        }

        private fun requestAfterForegroundDelay(
            requestActivity: Activity,
            triggerReason: AppOpenTriggerReason,
        ) {
            val targetActivity = currentActivity
            if (!targetActivity.isUsableForAds()) {
                Timber.w("AppOpen aborted in coordinator: Activity changed or invalid")
                recordSuppression(requestActivity, AdSuppressReason.APP_LIFECYCLE, triggerReason)
                return
            }
            adOrchestrator.refreshConsent(
                activity = requireNotNull(targetActivity),
                scope = ProcessLifecycleOwner.get().lifecycleScope,
            ) { canRequestAds ->
                handleConsentResult(requestActivity, triggerReason, canRequestAds)
            }
        }

        private fun handleConsentResult(
            requestActivity: Activity,
            triggerReason: AppOpenTriggerReason,
            canRequestAds: Boolean,
        ) {
            if (!canRequestAds) {
                Timber.d("AppOpen aborted after foreground consent refresh: ads unavailable")
                recordSuppression(requestActivity, foregroundSuppressionReason(), triggerReason)
                return
            }
            ProcessLifecycleOwner.get().lifecycleScope.launch {
                showOnCurrentActivity(requestActivity, triggerReason)
            }
        }

        private suspend fun showOnCurrentActivity(
            requestActivity: Activity,
            triggerReason: AppOpenTriggerReason,
        ) {
            val refreshedActivity = currentActivity
            if (refreshedActivity.isUsableForAds()) {
                adOrchestrator.showAppOpenAdIfEligible(requireNotNull(refreshedActivity), triggerReason)
                return
            }
            Timber.w("AppOpen aborted after consent refresh: Activity changed or invalid")
            recordSuppression(requestActivity, AdSuppressReason.APP_LIFECYCLE, triggerReason)
        }

        private fun recordSuppression(
            activity: Activity,
            reason: AdSuppressReason,
            triggerReason: AppOpenTriggerReason,
        ) {
            adOrchestrator.recordAppOpenSuppression(
                context = activity.applicationContext,
                reason = reason,
                triggerReason = triggerReason,
            )
        }

        private fun foregroundSuppressionReason(): AdSuppressReason =
            if (AdsConsentRuntimeState.canRequestAds.value) {
                AdSuppressReason.AD_GATE
            } else {
                AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked()
            }

        private fun Activity?.isUsableForAds(): Boolean =
            this != null && !isFinishing && !isDestroyed
    }
