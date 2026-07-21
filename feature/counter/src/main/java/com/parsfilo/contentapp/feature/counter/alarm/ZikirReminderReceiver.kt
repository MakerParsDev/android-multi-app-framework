package com.parsfilo.contentapp.feature.counter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ZikirReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var zikirReminderNotifier: ZikirReminderNotifier

    @Inject
    lateinit var zikirReminderScheduler: ZikirReminderScheduler

    @Inject
    lateinit var zikirRepository: ZikirRepository

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.d("ZikirReminderReceiver triggered action=%s", action)
        val pendingResult = goAsync()

        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        receiverScope.launch {
            try {
                if (action == ZikirReminderScheduler.ACTION_ZIKIR_REMINDER) {
                    val todayTotal = zikirRepository.getTodayTotalCount().first()
                    Timber.d("Zikir daily reminder fired todayTotal=%d", todayTotal)
                    zikirReminderNotifier.showDailyReminder(todayTotalCount = todayTotal)
                    zikirReminderScheduler.scheduleOrCancelFromPreferences()
                }
            } finally {
                Timber.d("ZikirReminderReceiver finished action=%s", action)
                pendingResult.finish()
            }
        }
    }
}
