package com.parsfilo.contentapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.parsfilo.contentapp.core.common.logging.sanitizeLogMessage
import com.parsfilo.contentapp.core.common.logging.shouldForwardToProductionCrashReporting
import com.parsfilo.contentapp.core.common.logging.toPrivacySafeThrowable
import com.parsfilo.contentapp.core.firebase.ADVANCED_CONSENT_MODE_COLLECTION_ENABLED
import com.parsfilo.contentapp.core.firebase.ANALYTICS_SCHEMA_VERSION
import com.parsfilo.contentapp.core.firebase.AnalyticsParamKey
import com.parsfilo.contentapp.core.firebase.AnalyticsUserPropertyKey
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.RuntimeObservability
import com.parsfilo.contentapp.core.firebase.RuntimeReleaseContext
import com.parsfilo.contentapp.core.firebase.appcheck.FirebaseAppCheckInstaller
import com.parsfilo.contentapp.core.firebase.config.EndpointsProvider
import com.parsfilo.contentapp.core.firebase.push.PushRegistrationManager
import com.parsfilo.contentapp.core.firebase.push.PushRegistrationSyncWorker
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.audio.data.AudioCachePrefetcher
import com.parsfilo.contentapp.feature.billing.BillingManager
import com.parsfilo.contentapp.feature.counter.alarm.ZikirReminderScheduler
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.widget.PrayerTimesWidgetReceiver
import com.parsfilo.contentapp.feature.prayertimes.worker.PrayerTimesRefreshWorker
import com.parsfilo.contentapp.monetization.AppOpenLifecycleCoordinator
import com.parsfilo.contentapp.product.AppProductDefinition
import com.parsfilo.contentapp.product.ContentFamily
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    private val productDefinition by lazy { AppProductDefinition.current }

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var appAnalytics: AppAnalytics

    @Inject
    lateinit var runtimeObservability: RuntimeObservability

    @Inject
    lateinit var appCheckInstaller: FirebaseAppCheckInstaller

    @Inject
    lateinit var prayerAlarmScheduler: PrayerAlarmScheduler

    @Inject
    lateinit var zikirReminderScheduler: ZikirReminderScheduler

    @Inject
    lateinit var pushRegistrationManager: PushRegistrationManager

    @Inject
    lateinit var audioCachePrefetcher: AudioCachePrefetcher

    @Inject
    lateinit var endpointsProvider: EndpointsProvider

    @Inject
    lateinit var appOpenLifecycleCoordinator: AppOpenLifecycleCoordinator

    override fun onCreate() {
        super.onCreate()

        // Timber must be initialized as early as possible to capture startup logs.
        if (BuildConfig.DEBUG) {
            Timber.plant(FixedTagDebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }
        installGlobalExceptionLogging()
        registerDebugActivityLifecycleLogging()
        Timber.i(
            "App onCreate started flavor=%s buildType=%s package=%s debug=%s",
            productDefinition.flavorId,
            BuildConfig.BUILD_TYPE,
            packageName,
            BuildConfig.DEBUG,
        )

        // Consent Mode keeps storage/ad signals denied by manifest default until UMP resolves
        // the user's choices. Keep SDK collection enabled so denied-consent traffic can still
        // produce privacy-safe signals and to recover installs where an older build persisted
        // setAnalyticsCollectionEnabled(false) across app sessions.
        // Crashlytics remains independent and continues collecting crashes.
        appAnalytics.setAnalyticsCollectionEnabled(ADVANCED_CONSENT_MODE_COLLECTION_ENABLED)

        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        runtimeObservability.configure(
            RuntimeReleaseContext(
                packageName = packageName,
                flavor = productDefinition.flavorId,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                buildType = BuildConfig.BUILD_TYPE,
                releaseRevision = BuildConfig.RELEASE_REVISION,
                releaseTrack = BuildConfig.RELEASE_TRACK,
            ),
        )

        // App Check must be installed early to protect Firebase endpoints (Firestore, Functions, etc.)
        appCheckInstaller.install()
        endpointsProvider.prefetchAsync()
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                pushRegistrationManager.subscribeToTopics(DEFAULT_FCM_TOPICS)
            }.onFailure { error ->
                Timber.w(error, "Failed to subscribe to default FCM topics")
            }
        }
        PushRegistrationSyncWorker.schedule(this)

        // Audio flavors: download once after first launch and keep local for offline playback.
        val productAudioFileName = productDefinition.audioFileName.orEmpty()
        if (shouldPrefetchFlavorAudio(productAudioFileName)) {
            ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    audioCachePrefetcher.prefetchIfNeeded(
                        packageName = packageName,
                        fallbackAudioFileName = productAudioFileName,
                        prefetchAllAudioOnFirstLaunch =
                            shouldPrefetchAllAudioOnFirstLaunch(
                                productDefinition,
                            ),
                    )
                }.onFailure { error ->
                    Timber.w(error, "Audio prefetch failed at startup")
                }
            }
        }

        // Analytics defaults (consent-aware): collection starts disabled on cold start and is
        // re-enabled by AdManager only after consent is granted. User properties/default params
        // are still set here so runtime enablement starts with stable metadata.
        appAnalytics.setUserProperty(AnalyticsUserPropertyKey.FLAVOR, productDefinition.flavorId)
        appAnalytics.setUserProperty(AnalyticsUserPropertyKey.BUILD_TYPE, BuildConfig.BUILD_TYPE)
        appAnalytics.setUserProperty(
            AnalyticsUserPropertyKey.APP_LANG,
            Locale.getDefault().toLanguageTag(),
        )
        appAnalytics.setUserProperty(AnalyticsUserPropertyKey.TZ, TimeZone.getDefault().id)
        appAnalytics.setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, "unknown")
        appAnalytics.setUserProperty(AnalyticsUserPropertyKey.AGE_GATE_STATUS, "unknown")
        appAnalytics.setUserProperty(AnalyticsUserPropertyKey.IS_PREMIUM, "false")
        appAnalytics.setDefaultEventParameters(
            Bundle().apply {
                putString(AnalyticsParamKey.FLAVOR_ID, productDefinition.flavorId)
                putString(AnalyticsParamKey.APP_VERSION, BuildConfig.VERSION_NAME)
                putString(AnalyticsParamKey.BUILD_TYPE, BuildConfig.BUILD_TYPE)
                putLong(AnalyticsParamKey.SCHEMA_VERSION, ANALYTICS_SCHEMA_VERSION.toLong())
                putString(AnalyticsParamKey.CONSENT_STATE, "unknown")
                putString(AnalyticsParamKey.SUBSCRIPTION_STATUS, "loading")
            },
        )
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            billingManager.subscriptionState.collect { subscriptionState ->
                val premium = subscriptionState is SubscriptionState.Active
                val subscriptionStatus =
                    when (subscriptionState) {
                        SubscriptionState.Unknown -> "unknown"
                        SubscriptionState.Loading -> "loading"
                        is SubscriptionState.Active -> "active"
                        SubscriptionState.Inactive -> "inactive"
                        is SubscriptionState.Error -> "error"
                    }
                appAnalytics.setUserProperty(
                    AnalyticsUserPropertyKey.IS_PREMIUM,
                    if (premium) "true" else "false",
                )
                appAnalytics.setUserProperty(
                    AnalyticsUserPropertyKey.SUBSCRIPTION_STATUS,
                    subscriptionStatus,
                )
                appAnalytics.setDefaultEventParameter(
                    AnalyticsParamKey.SUBSCRIPTION_STATUS,
                    subscriptionStatus,
                )
            }
        }

        appOpenLifecycleCoordinator.register(this)

        // BillingManager lifecycle yönetimi:
        // Uygulama arka plana geçince dinleyiciyi bırak, öne gelince yeniden bağlan.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    billingManager.endConnection()
                }

                override fun onStart(owner: LifecycleOwner) {
                    billingManager.startConnection()
                }
            },
        )

        if (productDefinition.isPrayerTimesFlavor) {
            PrayerTimesRefreshWorker.schedule(this)
            ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
                prayerAlarmScheduler.scheduleNextForCurrentFlavor()
                runCatching { PrayerTimesWidgetReceiver.refreshAll(this@App) }
                    .onFailure { error ->
                        Timber.w(error, "Prayer widget refresh failed at startup")
                    }
            }
        }

        if (productDefinition.contentFamily == ContentFamily.ZIKIR_COUNTER) {
            ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    zikirReminderScheduler.scheduleOrCancelFromPreferences()
                    zikirReminderScheduler.scheduleStreakCheckWorker()
                }.onFailure { error ->
                    Timber.w(error, "Failed to sync zikirmatik reminder schedule at startup")
                }
            }
        }
        Timber.i("App onCreate completed flavor=%s", productDefinition.flavorId)
    }

    private fun installGlobalExceptionLogging() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(
                throwable,
                "Uncaught exception thread=%s",
                thread.name,
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun registerDebugActivityLifecycleLogging() {
        if (!BuildConfig.DEBUG) return
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    Timber.d(
                        "Lifecycle created activity=%s savedState=%s",
                        activity::class.java.simpleName,
                        savedInstanceState != null,
                    )
                }

                override fun onActivityStarted(activity: Activity) {
                    Timber.d("Lifecycle started activity=%s", activity::class.java.simpleName)
                }

                override fun onActivityResumed(activity: Activity) {
                    Timber.d("Lifecycle resumed activity=%s", activity::class.java.simpleName)
                }

                override fun onActivityPaused(activity: Activity) {
                    Timber.d("Lifecycle paused activity=%s", activity::class.java.simpleName)
                }

                override fun onActivityStopped(activity: Activity) {
                    Timber.d("Lifecycle stopped activity=%s", activity::class.java.simpleName)
                }

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) {
                    Timber.d("Lifecycle saveState activity=%s", activity::class.java.simpleName)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    Timber.d("Lifecycle destroyed activity=%s", activity::class.java.simpleName)
                }
            },
        )
    }

    private companion object {
        private const val DEFAULT_CONTENT_AUDIO_FILE_NAME = "content_audio.mp3"
        private val DEFAULT_FCM_TOPICS =
            listOf(
                "dini-bildirim",
                "talep",
            )
    }

    private fun shouldPrefetchFlavorAudio(audioFileName: String): Boolean =
        audioFileName.trim().lowercase() != DEFAULT_CONTENT_AUDIO_FILE_NAME

    private fun shouldPrefetchAllAudioOnFirstLaunch(productDefinition: AppProductDefinition): Boolean =
        productDefinition.hasCapability("prefetch_all_audio_first_launch")
}

private const val DEBUG_TIMBER_TAG = "timber_log"

/**
 * Forces all Timber logs to a single Logcat tag so filtering is easier during development.
 */
class FixedTagDebugTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        if (!isLoggable(tag, priority)) return
        val payload =
            buildString {
                if (!tag.isNullOrBlank()) {
                    append('[')
                    append(tag)
                    append("] ")
                }
                append(message)
                if (t != null) {
                    append('\n')
                    append(Log.getStackTraceString(t))
                }
            }
        Log.println(priority, DEBUG_TIMBER_TAG, payload)
    }
}

/**
 * Production Timber Tree — warning/error logs go to Crashlytics logs; only ERROR exceptions are recorded as issues.
 * Debug and info logs are suppressed in release builds.
 */
class CrashlyticsTree : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        if (!shouldForwardToProductionCrashReporting(priority)) return

        val crashlytics = FirebaseCrashlytics.getInstance()
        val safeTag = sanitizeLogMessage(tag ?: "App")
        val safeMessage = sanitizeLogMessage(message)
        crashlytics.log("$safeTag: $safeMessage")

        // Preserve the original stack for diagnostics without forwarding its message,
        // cause chain, or suppressed exceptions, which may contain user-provided data.
        if (priority >= Log.ERROR) {
            t?.let { crashlytics.recordException(it.toPrivacySafeThrowable()) }
        }
    }
}
