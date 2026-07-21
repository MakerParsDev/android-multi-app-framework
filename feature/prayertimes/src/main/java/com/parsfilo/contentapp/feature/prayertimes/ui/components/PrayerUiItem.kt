package com.parsfilo.contentapp.feature.prayertimes.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant

internal data class PrayerUiItem(
    val key: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

internal fun prayerItemsForVariant(variant: PrayerAppVariant): List<PrayerUiItem> {
    return when (variant) {
        PrayerAppVariant.NAMAZ_VAKITLERI -> listOf(
            PrayerUiItem(PrayerAlarmScheduler.PRAYER_IMSAK, R.string.prayertimes_sahur_imsak, R.drawable.ic_prayer_imsak),
            PrayerUiItem(PrayerAlarmScheduler.PRAYER_GUNES, R.string.prayertimes_sabah_gunes, R.drawable.ic_prayer_gunes),
            PrayerUiItem(PrayerAlarmScheduler.PRAYER_OGLE, R.string.prayertimes_ogle, R.drawable.ic_prayer_ogle),
            PrayerUiItem(PrayerAlarmScheduler.PRAYER_IKINDI, R.string.prayertimes_ikindi, R.drawable.ic_prayer_ikindi),
            PrayerUiItem(PrayerAlarmScheduler.PRAYER_AKSAM, R.string.prayertimes_aksam, R.drawable.ic_prayer_aksam),
            PrayerUiItem(PrayerAlarmScheduler.PRAYER_YATSI, R.string.prayertimes_yatsi, R.drawable.ic_prayer_yatsi),
        )
    }
}
