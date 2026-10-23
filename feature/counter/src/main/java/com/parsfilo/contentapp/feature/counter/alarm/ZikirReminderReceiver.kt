package com.parsfilo.contentapp.feature.counter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
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

class ZikirReminderReceiver : BroadcastReceiver() {
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var dependenciesProvider: (Context) -> ZikirReminderDependencies = ::resolveDependencies

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ZikirReminderScheduler.ACTION_ZIKIR_REMINDER) return
        val dependencies = dependenciesProvider(context.applicationContext)
        val pendingResult = goAsync()
        val receiverJob = SupervisorJob()
        CoroutineScope(receiverJob + ioDispatcher).launch {
            try {
                deliverAndReschedule(dependencies)
            } finally {
                pendingResult.finish()
                receiverJob.cancel()
            }
        }
    }

    // Repository and notification errors must not prevent tomorrow's reminder.
    @Suppress("TooGenericExceptionCaught") // Receiver boundary isolates platform/DAO errors.
    private suspend fun deliverAndReschedule(dependencies: ZikirReminderDependencies) {
        try {
            withTimeout(NOTIFICATION_TIMEOUT_MS) {
                val todayTotal = dependencies.repository.getTodayTotalCount().first()
                dependencies.notifier.showDailyReminder(todayTotalCount = todayTotal)
            }
        } catch (_: TimeoutCancellationException) {
            Timber.w("Zikir notification timed out")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Zikir notification failed")
        } finally {
            withContext(NonCancellable) {
                try {
                    withTimeout(SCHEDULING_TIMEOUT_MS) {
                        dependencies.scheduler.scheduleOrCancelFromPreferences()
                    }
                } catch (_: TimeoutCancellationException) {
                    Timber.w("Zikir next-reminder scheduling timed out")
                    queueRecovery(dependencies)
                } catch (error: Exception) {
                    Timber.w(error, "Zikir next-reminder scheduling failed")
                    queueRecovery(dependencies)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // An enqueue failure must not crash the broadcast receiver.
    private suspend fun queueRecovery(dependencies: ZikirReminderDependencies) {
        try {
            withTimeout(RECOVERY_ENQUEUE_TIMEOUT_MS) {
                dependencies.enqueueRecovery()
            }
        } catch (_: TimeoutCancellationException) {
            Timber.w("Zikir reminder recovery enqueue timed out")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Unable to enqueue durable zikir reminder recovery")
        }
    }

    private fun resolveDependencies(context: Context): ZikirReminderDependencies {
        val entry = EntryPointAccessors.fromApplication(context, ZikirReminderReceiverEntryPoint::class.java)
        return ZikirReminderDependencies(
            scheduler = entry.zikirReminderScheduler(),
            notifier = entry.zikirReminderNotifier(),
            repository = entry.zikirRepository(),
            enqueueRecovery = { ZikirReminderRecoveryWorker.enqueue(context) },
        )
    }

    internal companion object {
        const val NOTIFICATION_TIMEOUT_MS = 4_000L
        const val SCHEDULING_TIMEOUT_MS = 4_000L
        const val RECOVERY_ENQUEUE_TIMEOUT_MS = 1_500L
    }
}

internal data class ZikirReminderDependencies(
    val scheduler: ZikirReminderScheduler,
    val notifier: ZikirReminderNotifier,
    val repository: ZikirRepository,
    // No-op default is solely for injected isolated-test fakes; production always supplies queue.
    val enqueueRecovery: suspend () -> Unit = {},
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZikirReminderReceiverEntryPoint {
    fun zikirReminderScheduler(): ZikirReminderScheduler

    fun zikirReminderNotifier(): ZikirReminderNotifier

    fun zikirRepository(): ZikirRepository
}
