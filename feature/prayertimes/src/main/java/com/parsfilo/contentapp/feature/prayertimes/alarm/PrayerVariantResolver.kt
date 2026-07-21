package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.Context
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant

internal object PrayerVariantResolver {
    fun resolve(context: Context): PrayerAppVariant = PrayerAppVariant.NAMAZ_VAKITLERI
}
