package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prayerAlarmNotifier: PrayerAlarmNotifier

    @Inject
    lateinit var prayerAlarmScheduler: PrayerAlarmScheduler

    @Inject
    lateinit var prayerPreferencesDataSource: PrayerPreferencesDataSource

    override fun onReceive(
        context: Context?,
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
        val pendingResult = goAsync()

        val receiverJob = SupervisorJob()
        val receiverScope = CoroutineScope(receiverJob + Dispatchers.IO)

        receiverScope.launch {
            try {
                runCatching {
                    val variant = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_VARIANT)
                        ?.let { runCatching { PrayerAppVariant.valueOf(it) }.getOrNull() }
                        ?: context?.let { PrayerVariantResolver.resolve(it) }
                        ?: PrayerAppVariant.NAMAZ_VAKITLERI

                    val payload = PrayerAlarmPayload(
                        prayerKey = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_KEY).orEmpty(),
                        timeHm = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_TIME_HM).orEmpty(),
                        localDate = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_LOCAL_DATE).orEmpty(),
                        triggerAtMillis = intent.getLongExtra(
                            PrayerAlarmScheduler.EXTRA_TRIGGER_AT,
                            System.currentTimeMillis(),
                        ),
                    )

                    val soundUri = prayerPreferencesDataSource.preferences.first().alarmSoundUri
                    prayerAlarmNotifier.show(payload = payload, variant = variant, soundUri = soundUri)
                    prayerAlarmScheduler.scheduleNext(variant)
                    Timber.d("PrayerAlarmReceiver handled and scheduled next alarm variant=%s", variant.name)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.w(error, "Prayer alarm receiver failed")
                }
            } finally {
                receiverJob.cancel()
                Timber.d("PrayerAlarmReceiver finished")
                pendingResult.finish()
            }
        }
    }
}
