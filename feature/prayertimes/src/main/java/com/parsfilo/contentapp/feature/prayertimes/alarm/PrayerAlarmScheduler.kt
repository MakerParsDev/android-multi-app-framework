package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AnalyticsEventName
import com.parsfilo.contentapp.core.firebase.AnalyticsParamKey
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prayerTimesDao: PrayerTimesDao,
    private val prayerPreferencesDataSource: PrayerPreferencesDataSource,
    private val appAnalytics: AppAnalytics,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun scheduleNextForCurrentFlavor(): Boolean {
        return scheduleNext(PrayerVariantResolver.resolve(context))
    }

    suspend fun scheduleNext(variant: PrayerAppVariant): Boolean = withContext(ioDispatcher) {
        Timber.d("Prayer alarm scheduling started variant=%s", variant.name)
        val preferences = prayerPreferencesDataSource.preferences.first()
        if (!preferences.alarmEnabled) {
            Timber.d("Prayer alarm scheduling skipped: alarm disabled in preferences")
            cancelScheduledAlarm()
            return@withContext false
        }

        val districtId = preferences.selectedDistrictId
            ?: run {
                Timber.d("Prayer alarm scheduling skipped: district is not selected")
                cancelScheduledAlarm()
                return@withContext false
            }
        val selectedPrayerKeys = preferences.selectedAlarmPrayerKeys
        val offsetMinutes = preferences.alarmOffsetMinutes.coerceIn(0, MAX_OFFSET_MINUTES)

        val todayIso = todayIso()
        val tomorrowIso = shiftIsoDate(todayIso)
        val nowMillis = System.currentTimeMillis()
        val items = prayerTimesDao.getPrayerTimes(
            districtId = districtId,
            fromDate = todayIso,
            toDate = tomorrowIso,
        )

        val nextAlarm = findNextAlarm(
            items = items,
            variant = variant,
            selectedPrayerKeys = selectedPrayerKeys,
            offsetMinutes = offsetMinutes,
            nowMillis = nowMillis,
        ) ?: run {
            cancelScheduledAlarm()
            Timber.d("Prayer alarm not scheduled; no upcoming time in cache.")
            return@withContext false
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager == null) {
            Timber.w("AlarmManager is unavailable; prayer alarm not scheduled.")
            return@withContext false
        }

        // Ensure any previous alarm (possibly scheduled for a different prayer key) is removed.
        cancelScheduledAlarm()

        val pendingIntent = buildPendingIntent(
            variant = variant,
            payload = nextAlarm,
            flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return@withContext false

        alarmManager.cancel(pendingIntent)
        setAlarm(alarmManager, nextAlarm.triggerAtMillis, pendingIntent)
        Timber.d(
            "Prayer alarm scheduled variant=%s prayerKey=%s localDate=%s timeHm=%s triggerAt=%d",
            variant.name,
            nextAlarm.prayerKey,
            nextAlarm.localDate,
            nextAlarm.timeHm,
            nextAlarm.triggerAtMillis,
        )

        appAnalytics.logEvent(
            AnalyticsEventName.PRAYER_ALARM_SCHEDULED,
            Bundle().apply {
                putString(AnalyticsParamKey.VARIANT, variant.name)
                putString(AnalyticsParamKey.PRAYER_KEY, nextAlarm.prayerKey)
                putString(AnalyticsParamKey.LOCAL_DATE, nextAlarm.localDate)
                putString(AnalyticsParamKey.TIME_HM, nextAlarm.timeHm)
                putInt(AnalyticsParamKey.OFFSET_MINUTES, offsetMinutes)
            },
        )
        true
    }

    fun cancelScheduledAlarm() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        Timber.d("Cancelling all scheduled prayer alarms")

        ALL_PRAYER_KEYS.forEach { prayerKey ->
            val pendingIntent = buildPendingIntent(
                variant = null,
                payload = null,
                prayerKey = prayerKey,
                flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: return@forEach
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        // Backward compatibility: clear alarm scheduled with old single requestCode strategy.
        val legacyIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_ALARM
        }
        val legacyPendingIntent = PendingIntent.getBroadcast(
            context,
            LEGACY_REQUEST_CODE_ALARM,
            legacyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        legacyPendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        Timber.d("Prayer alarm cancellation completed")
    }

    private fun findNextAlarm(
        items: List<PrayerTimeEntity>,
        variant: PrayerAppVariant,
        selectedPrayerKeys: Set<String>,
        offsetMinutes: Int,
        nowMillis: Long,
    ): PrayerAlarmPayload? {
        val allowedPrayerKeys = selectedPrayerKeys.ifEmpty { defaultPrayerKeysForVariant(variant) }
        return items.asSequence()
            .flatMap { entity ->
                prayerTimesForVariant(entity, variant).asSequence().mapNotNull { (prayerKey, timeHm) ->
                    if (!allowedPrayerKeys.contains(prayerKey)) return@mapNotNull null
                    val prayerAtMillis =
                        parseLocalDateTime(entity.localDate, timeHm) ?: return@mapNotNull null
                    val triggerAtMillis = prayerAtMillis - offsetMinutes * ONE_MINUTE_MS
                    if (triggerAtMillis <= nowMillis + MIN_SCHEDULE_AHEAD_MS) return@mapNotNull null
                    PrayerAlarmPayload(
                        prayerKey = prayerKey,
                        timeHm = timeHm,
                        localDate = entity.localDate,
                        triggerAtMillis = triggerAtMillis,
                    )
                }
            }
            .minByOrNull { it.triggerAtMillis }
    }

    private fun prayerTimesForVariant(
        entity: PrayerTimeEntity,
        variant: PrayerAppVariant,
    ): List<Pair<String, String>> {
        return when (variant) {
            PrayerAppVariant.NAMAZ_VAKITLERI -> listOf(
                PRAYER_IMSAK to entity.imsak,
                PRAYER_GUNES to entity.gunes,
                PRAYER_OGLE to entity.ogle,
                PRAYER_IKINDI to entity.ikindi,
                PRAYER_AKSAM to entity.aksam,
                PRAYER_YATSI to entity.yatsi,
            )
        }
    }

    private fun defaultPrayerKeysForVariant(variant: PrayerAppVariant): Set<String> {
        return when (variant) {
            PrayerAppVariant.NAMAZ_VAKITLERI -> {
                setOf(PRAYER_IMSAK, PRAYER_GUNES, PRAYER_OGLE, PRAYER_IKINDI, PRAYER_AKSAM, PRAYER_YATSI)
            }
        }
    }

    private fun todayIso(): String {
        return SimpleDateFormat(DATE_PATTERN, Locale.US).format(System.currentTimeMillis())
    }

    private fun shiftIsoDate(baseIso: String): String {
        val parser = SimpleDateFormat(DATE_PATTERN, Locale.US).apply { isLenient = false }
        val baseDate = runCatching { parser.parse(baseIso) }.getOrNull() ?: return baseIso
        val calendar = Calendar.getInstance().apply {
            time = baseDate
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return parser.format(calendar.time)
    }

    private fun parseLocalDateTime(
        localDate: String,
        timeHm: String,
    ): Long? {
        val parser = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US).apply { isLenient = false }
        return runCatching { parser.parse("$localDate $timeHm")?.time }.getOrNull()
    }

    private fun buildPendingIntent(
        variant: PrayerAppVariant?,
        payload: PrayerAlarmPayload?,
        prayerKey: String? = null,
        flags: Int,
    ): PendingIntent? {
        val resolvedPrayerKey = payload?.prayerKey ?: prayerKey
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_ALARM
            if (variant != null) {
                putExtra(EXTRA_VARIANT, variant.name)
            }
            if (!resolvedPrayerKey.isNullOrBlank()) {
                putExtra(EXTRA_PRAYER_KEY, resolvedPrayerKey)
            }
            if (payload != null) {
                putExtra(EXTRA_TIME_HM, payload.timeHm)
                putExtra(EXTRA_LOCAL_DATE, payload.localDate)
                putExtra(EXTRA_TRIGGER_AT, payload.triggerAtMillis)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(resolvedPrayerKey),
            intent,
            flags,
        )
    }

    private fun requestCodeFor(prayerKey: String?): Int = prayerAlarmRequestCodeFor(prayerKey)

    private fun setAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        Timber.d("Setting prayer alarm with setAndAllowWhileIdle triggerAt=%d", triggerAtMillis)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    companion object {
        const val ACTION_FIRE_ALARM = "com.parsfilo.contentapp.feature.prayertimes.ACTION_FIRE_ALARM"
        const val EXTRA_VARIANT = "extra_variant"
        const val EXTRA_PRAYER_KEY = "extra_prayer_key"
        const val EXTRA_TIME_HM = "extra_time_hm"
        const val EXTRA_LOCAL_DATE = "extra_local_date"
        const val EXTRA_TRIGGER_AT = "extra_trigger_at"

        const val PRAYER_IMSAK = "imsak"
        const val PRAYER_GUNES = "gunes"
        const val PRAYER_OGLE = "ogle"
        const val PRAYER_IKINDI = "ikindi"
        const val PRAYER_AKSAM = "aksam"
        const val PRAYER_YATSI = "yatsi"

        private val ALL_PRAYER_KEYS = listOf(
            PRAYER_IMSAK,
            PRAYER_GUNES,
            PRAYER_OGLE,
            PRAYER_IKINDI,
            PRAYER_AKSAM,
            PRAYER_YATSI,
        )

        internal const val REQUEST_CODE_IMSAK = 41_031
        internal const val REQUEST_CODE_GUNES = 41_032
        internal const val REQUEST_CODE_OGLE = 41_033
        internal const val REQUEST_CODE_IKINDI = 41_034
        internal const val REQUEST_CODE_AKSAM = 41_035
        internal const val REQUEST_CODE_YATSI = 41_036
        internal const val LEGACY_REQUEST_CODE_ALARM = 41_031

        private const val DATE_PATTERN = "yyyy-MM-dd"
        private const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm"
        private const val MIN_SCHEDULE_AHEAD_MS = 15_000L
        private const val ONE_MINUTE_MS = 60_000L
        private const val MAX_OFFSET_MINUTES = 120
    }
}

internal fun prayerAlarmRequestCodeFor(prayerKey: String?): Int {
    return when (prayerKey) {
        PrayerAlarmScheduler.PRAYER_IMSAK -> PrayerAlarmScheduler.REQUEST_CODE_IMSAK
        PrayerAlarmScheduler.PRAYER_GUNES -> PrayerAlarmScheduler.REQUEST_CODE_GUNES
        PrayerAlarmScheduler.PRAYER_OGLE -> PrayerAlarmScheduler.REQUEST_CODE_OGLE
        PrayerAlarmScheduler.PRAYER_IKINDI -> PrayerAlarmScheduler.REQUEST_CODE_IKINDI
        PrayerAlarmScheduler.PRAYER_AKSAM -> PrayerAlarmScheduler.REQUEST_CODE_AKSAM
        PrayerAlarmScheduler.PRAYER_YATSI -> PrayerAlarmScheduler.REQUEST_CODE_YATSI
        else -> PrayerAlarmScheduler.LEGACY_REQUEST_CODE_ALARM
    }
}

data class PrayerAlarmPayload(
    val prayerKey: String,
    val timeHm: String,
    val localDate: String,
    val triggerAtMillis: Long,
)
