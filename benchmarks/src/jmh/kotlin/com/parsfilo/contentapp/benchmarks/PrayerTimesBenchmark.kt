package com.parsfilo.contentapp.benchmarks

import com.parsfilo.contentapp.feature.prayertimes.data.HijriCalendarConverter
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerCachePolicy
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerDateTime
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.Locale

@State(Scope.Benchmark)
open class PrayerTimesBenchmark {
    private lateinit var days: List<PrayerTimesDay>
    private var nowMillis: Long = 0L

    @Setup
    fun setup() {
        // A cached 45-day window, as kept by PrayerCachePolicy.
        days =
            (0 until 45).map { offset ->
                val date = PrayerDateTime.shiftIsoDate("2026-03-01", offset)
                PrayerTimesDay(
                    localDate = date,
                    imsak = "05:12",
                    gunes = "06:40",
                    ogle = "13:15",
                    ikindi = "16:30",
                    aksam = "19:05",
                    yatsi = "20:28",
                )
            }
        // Middle of the window, so the scan has to parse every entry.
        nowMillis = PrayerDateTime.parseIsoHmToMillis("2026-03-20", "14:00")!!
    }

    @Benchmark
    fun findNextPrayer(): Any? = PrayerDateTime.findNextPrayer(days, nowMillis)

    @Benchmark
    fun cacheRefreshDecision(): Boolean =
        PrayerCachePolicy.shouldRefresh(
            nowMillis = nowMillis,
            lastSyncAtMillis = nowMillis - 3_600_000L,
            coverageEndIso = "2026-04-30",
            todayIso = "2026-03-20",
            hasTodayCache = true,
            force = false,
        )

    @Benchmark
    fun cacheDateWindow(): Pair<String, String> = PrayerCachePolicy.dateWindow("2026-03-20")

    @Benchmark
    fun formatCountdown(bh: Blackhole) {
        for (seconds in 0 until 3_600 step 37) {
            bh.consume(PrayerDateTime.formatCountdown(seconds * 1_000L))
        }
    }

    @Benchmark
    fun hijriConversionYear(bh: Blackhole) {
        val locale = Locale.forLanguageTag("tr-TR")
        for (month in 1..12) {
            for (day in 1..28) {
                val (_, hm, hd) = HijriCalendarConverter.toHijri(2026, month, day)
                bh.consume(hd)
                bh.consume(HijriCalendarConverter.monthName(hm, locale))
            }
        }
    }
}
