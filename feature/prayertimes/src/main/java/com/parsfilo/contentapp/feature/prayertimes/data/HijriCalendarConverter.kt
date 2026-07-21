package com.parsfilo.contentapp.feature.prayertimes.data

import java.util.Calendar
import java.util.Locale

object HijriCalendarConverter {
    fun toHijri(year: Int, month: Int, day: Int): Triple<Int, Int, Int> {
        val jd =
            (367 * year) - (7 * (year + (month + 9) / 12)) / 4 + (275 * month) / 9 + day + 1721013
        val z = jd.toLong()
        val a = z + 32082L
        val b = (4 * a + 3) / 1461
        val c = a - (1461 * b) / 4
        val d = (5 * c + 2) / 153
        val hijriDay = c - (153 * d + 2) / 5 + 1
        val hijriMonth = if (d < 10) d + 3 else d - 9
        val hijriYear = b - 4800 + if (hijriMonth < 3) 1 else 0
        return Triple(hijriYear.toInt() + 1, hijriMonth.toInt(), hijriDay.toInt())
    }

    fun todayHijriString(locale: Locale = Locale.forLanguageTag("tr-TR")): String {
        val calendar = Calendar.getInstance()
        val (hy, hm, hd) = toHijri(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        val month = monthName(hm, locale)
        return "$hd $month $hy"
    }

    fun monthName(month: Int, locale: Locale = Locale.forLanguageTag("tr-TR")): String {
        val tr = listOf(
            "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir",
            "Cemaziyelevvel", "Cemaziyelahir", "Recep", "Şaban",
            "Ramazan", "Şevval", "Zilkade", "Zilhicce",
        )
        val en = listOf(
            "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
        )
        val names = if (locale.language == "tr") tr else en
        return names.getOrElse(month - 1) { "" }
    }
}
