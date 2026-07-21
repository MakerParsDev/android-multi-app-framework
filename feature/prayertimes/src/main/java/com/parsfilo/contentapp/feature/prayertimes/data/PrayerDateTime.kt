package com.parsfilo.contentapp.feature.prayertimes.data

import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal object PrayerDateTime {
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val miladiDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    private val hourMinuteFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun todayIso(): String {
        return isoDateFormat.format(Calendar.getInstance().time)
    }

    fun shiftIsoDate(baseIso: String, deltaDays: Int): String {
        val baseDate = runCatching { isoDateFormat.parse(baseIso) }.getOrNull()
            ?: return baseIso
        val calendar = Calendar.getInstance().apply {
            time = baseDate
            add(Calendar.DAY_OF_YEAR, deltaDays)
        }
        return isoDateFormat.format(calendar.time)
    }

    fun parseMiladiToIso(value: String): String? {
        val parsed = runCatching { miladiDateFormat.parse(value) }.getOrNull() ?: return null
        return isoDateFormat.format(parsed)
    }

    fun dateIsoFromEpochMillis(value: Long): String {
        return isoDateFormat.format(value)
    }

    fun currentTimeHm(): String {
        return hourMinuteFormat.format(Calendar.getInstance().time)
    }

    fun currentDateTimeHm(): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
            .format(Calendar.getInstance().time)
    }

    fun findNextPrayer(
        days: List<PrayerTimesDay>,
        nowMillis: Long = System.currentTimeMillis(),
    ): NextPrayerInfo? {
        val sequence = days.asSequence()
            .flatMap { day ->
                listOf(
                    NextPrayerInfo(PRAYER_IMSAK, day.localDate, day.imsak),
                    NextPrayerInfo(PRAYER_GUNES, day.localDate, day.gunes),
                    NextPrayerInfo(PRAYER_OGLE, day.localDate, day.ogle),
                    NextPrayerInfo(PRAYER_IKINDI, day.localDate, day.ikindi),
                    NextPrayerInfo(PRAYER_AKSAM, day.localDate, day.aksam),
                    NextPrayerInfo(PRAYER_YATSI, day.localDate, day.yatsi),
                ).asSequence()
            }
            .mapNotNull { item ->
                val triggerMillis = parseIsoHmToMillis(item.localDate, item.timeHm)
                    ?: return@mapNotNull null
                if (triggerMillis <= nowMillis) return@mapNotNull null
                item.copy(triggerMillis = triggerMillis)
            }
        return sequence.minByOrNull { it.triggerMillis }
    }

    fun formatCountdown(remainingMillis: Long): String {
        val safe = remainingMillis.coerceAtLeast(0L) / 1_000L
        val hours = safe / 3_600L
        val minutes = (safe % 3_600L) / 60L
        val seconds = safe % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun parseIsoHmToMillis(localDate: String, hm: String): Long? {
        return runCatching { dateTimeFormat.parse("$localDate $hm")?.time }.getOrNull()
    }
}

internal data class NextPrayerInfo(
    val prayerKey: String,
    val localDate: String,
    val timeHm: String,
    val triggerMillis: Long = Long.MAX_VALUE,
)

internal const val PRAYER_IMSAK = "imsak"
internal const val PRAYER_GUNES = "gunes"
internal const val PRAYER_OGLE = "ogle"
internal const val PRAYER_IKINDI = "ikindi"
internal const val PRAYER_AKSAM = "aksam"
internal const val PRAYER_YATSI = "yatsi"
