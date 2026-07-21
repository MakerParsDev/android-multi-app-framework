package com.parsfilo.contentapp.feature.counter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parsfilo.contentapp.core.common.broadcast.SystemBroadcastRescheduleActions
import com.parsfilo.contentapp.core.common.broadcast.SystemBroadcastRescheduleGate
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class ZikirSystemBroadcastReceiver : BroadcastReceiver() {
    internal var dependenciesProvider: (Context) -> ZikirSystemBroadcastDependencies = ::resolveDependencies

    @Suppress("TooGenericExceptionCaught") // Receiver boundary must always finish pendingResult.
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (!SystemBroadcastRescheduleActions.supports(action)) return

        val dependencies = dependenciesProvider(context.applicationContext)
        val pendingResult = goAsync()
        val receiverJob = SupervisorJob()
        CoroutineScope(receiverJob + dependencies.ioDispatcher).launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MS) {
                    dependencies.rescheduleGate.runIfIdle("zikir") {
                        dependencies.scheduler.scheduleOrCancelFromPreferences()
                        dependencies.scheduler.scheduleStreakCheckWorker()
                    }
                }
            } catch (_: TimeoutCancellationException) {
                Timber.w("Zikir system reschedule timed out action=%s", action)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Zikir system reschedule failed action=%s", action)
            } finally {
                pendingResult.finish()
                receiverJob.cancel()
            }
        }
    }

    private fun resolveDependencies(context: Context): ZikirSystemBroadcastDependencies {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ZikirSystemBroadcastReceiverEntryPoint::class.java,
        )
        return ZikirSystemBroadcastDependencies(
            scheduler = entryPoint.zikirReminderScheduler(),
            ioDispatcher = entryPoint.ioDispatcher(),
            rescheduleGate = entryPoint.systemBroadcastRescheduleGate(),
        )
    }

    internal companion object {
        const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}

internal data class ZikirSystemBroadcastDependencies(
    val scheduler: ZikirReminderScheduler,
    val ioDispatcher: CoroutineDispatcher,
    val rescheduleGate: SystemBroadcastRescheduleGate,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZikirSystemBroadcastReceiverEntryPoint {
    fun zikirReminderScheduler(): ZikirReminderScheduler

    @Dispatcher(AppDispatchers.IO)
    fun ioDispatcher(): CoroutineDispatcher

    fun systemBroadcastRescheduleGate(): SystemBroadcastRescheduleGate
}
