package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class PrayerAlarmReceiver : BroadcastReceiver() {
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var dependenciesProvider: (Context) -> PrayerAlarmDependencies = ::resolveDependencies

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != PrayerAlarmScheduler.ACTION_FIRE_ALARM) return
        Timber.d(
            "PrayerAlarmReceiver triggered action=%s variant=%s prayerKey=%s localDate=%s timeHm=%s",
            intent.action,
            intent.getStringExtra(PrayerAlarmScheduler.EXTRA_VARIANT),
            intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_KEY),
            intent.getStringExtra(PrayerAlarmScheduler.EXTRA_LOCAL_DATE),
            intent.getStringExtra(PrayerAlarmScheduler.EXTRA_TIME_HM),
        )
        val dependencies = dependenciesProvider(context.applicationContext)
        val pendingResult = goAsync()

        val receiverJob = SupervisorJob()
        val receiverScope = CoroutineScope(receiverJob + ioDispatcher)

        receiverScope.launch {
            try {
                val variant = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_VARIANT)
                    ?.let { runCatching { PrayerAppVariant.valueOf(it) }.getOrNull() }
                    ?: PrayerVariantResolver.resolve(context)

                val payload = PrayerAlarmPayload(
                    prayerKey = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_KEY).orEmpty(),
                    timeHm = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_TIME_HM).orEmpty(),
                    localDate = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_LOCAL_DATE).orEmpty(),
                    triggerAtMillis = intent.getLongExtra(
                        PrayerAlarmScheduler.EXTRA_TRIGGER_AT,
                        System.currentTimeMillis(),
                    ),
                )
                deliverAndReschedule(dependencies, variant, payload)
            } finally {
                pendingResult.finish()
                receiverJob.cancel()
            }
        }
    }

    // Notification delivery is best-effort. Its failures must not discard the one-shot alarm chain.
    @Suppress("TooGenericExceptionCaught") // Receiver boundary isolates platform/DAO errors.
    private suspend fun deliverAndReschedule(
        dependencies: PrayerAlarmDependencies,
        variant: PrayerAppVariant,
        payload: PrayerAlarmPayload,
    ) {
        try {
            withTimeout(NOTIFICATION_TIMEOUT_MS) {
                val soundUri = dependencies.preferences.preferences.first().alarmSoundUri
                dependencies.notifier.show(payload = payload, variant = variant, soundUri = soundUri)
            }
        } catch (_: TimeoutCancellationException) {
            Timber.w("Prayer notification timed out variant=%s", variant.name)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Prayer notification failed variant=%s", variant.name)
        } finally {
            // A cancelled/failed notification should still attempt the next legitimate alarm.
            withContext(NonCancellable) {
                try {
                    val scheduled = withTimeout(SCHEDULING_TIMEOUT_MS) {
                        dependencies.scheduler.scheduleNext(variant)
                    }
                    if (!scheduled) {
                        Timber.w("Prayer next-alarm schedule unavailable variant=%s", variant.name)
                        // A legitimate user disable or unselected district is not an error.
                        // Missing future cache while alarms are enabled requires durable recovery.
                        val preferences = withTimeout(RECOVERY_ENQUEUE_TIMEOUT_MS) {
                            dependencies.preferences.preferences.first()
                        }
                        if (preferences.alarmEnabled && preferences.selectedDistrictId != null) {
                            queueRecovery(dependencies, variant)
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    Timber.w("Prayer next-alarm scheduling timed out variant=%s", variant.name)
                    queueRecovery(dependencies, variant)
                } catch (error: Exception) {
                    Timber.w(error, "Prayer next-alarm scheduling failed variant=%s", variant.name)
                    queueRecovery(dependencies, variant)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // An enqueue failure must not crash the broadcast receiver.
    private suspend fun queueRecovery(dependencies: PrayerAlarmDependencies, variant: PrayerAppVariant) {
        try {
            withTimeout(RECOVERY_ENQUEUE_TIMEOUT_MS) {
                dependencies.enqueueRecovery(variant)
            }
        } catch (_: TimeoutCancellationException) {
            Timber.w("Prayer recovery enqueue timed out variant=%s", variant.name)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Unable to enqueue durable prayer alarm recovery variant=%s", variant.name)
        }
    }

    private fun resolveDependencies(context: Context): PrayerAlarmDependencies {
        val entry = EntryPointAccessors.fromApplication(context, PrayerAlarmReceiverEntryPoint::class.java)
        return PrayerAlarmDependencies(
            scheduler = entry.prayerAlarmScheduler(),
            notifier = entry.prayerAlarmNotifier(),
            preferences = entry.prayerPreferencesDataSource(),
            enqueueRecovery = { variant -> PrayerAlarmRecoveryWorker.enqueue(context, variant) },
        )
    }

    internal companion object {
        const val NOTIFICATION_TIMEOUT_MS = 4_000L
        const val SCHEDULING_TIMEOUT_MS = 4_000L
        const val RECOVERY_ENQUEUE_TIMEOUT_MS = 1_500L
    }
}

internal data class PrayerAlarmDependencies(
    val scheduler: PrayerAlarmScheduler,
    val notifier: PrayerAlarmNotifier,
    val preferences: PrayerPreferencesDataSource,
    // No-op default is solely for injected isolated-test fakes; production always supplies queue.
    val enqueueRecovery: suspend (PrayerAppVariant) -> Unit = {},
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerAlarmReceiverEntryPoint {
    fun prayerAlarmScheduler(): PrayerAlarmScheduler

    fun prayerAlarmNotifier(): PrayerAlarmNotifier

    fun prayerPreferencesDataSource(): PrayerPreferencesDataSource
}
