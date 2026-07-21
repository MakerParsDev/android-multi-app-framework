package com.parsfilo.contentapp.feature.prayertimes.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerDateTime
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class PrayerTimesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PrayerTimesWidgetEntryPoint::class.java,
        )
        val prefs = deps.prayerPreferencesDataSource().preferences.first()
        val districtId = prefs.selectedDistrictId
        val nextPrayerLabelAndTime = runCatching {
            districtId?.let { district ->
                val today = PrayerDateTime.todayIso()
                val tomorrow = PrayerDateTime.shiftIsoDate(today, 1)
                val items = deps.prayerTimesDao().getPrayerTimes(
                    districtId = district,
                    fromDate = today,
                    toDate = tomorrow,
                )
                findNextPrayerForWidget(context, items)
            }
        }.getOrNull()
        val widgetTitle = context.getString(R.string.prayertimes_widget_title)
        val noNextPrayer = context.getString(R.string.prayertimes_widget_no_next)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    Text(
                        text = widgetTitle,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = nextPrayerLabelAndTime?.first ?: noNextPrayer,
                        style = TextStyle(color = GlanceTheme.colors.secondary),
                    )
                    Text(
                        text = nextPrayerLabelAndTime?.second ?: "--:--",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }

    private fun findNextPrayerForWidget(
        context: Context,
        items: List<PrayerTimeEntity>,
    ): Pair<String, String>? {
        val nowMillis = System.currentTimeMillis()
        val next = items.asSequence()
            .flatMap { day ->
                listOf(
                    Triple(PrayerAlarmScheduler.PRAYER_IMSAK, day.localDate, day.imsak),
                    Triple(PrayerAlarmScheduler.PRAYER_GUNES, day.localDate, day.gunes),
                    Triple(PrayerAlarmScheduler.PRAYER_OGLE, day.localDate, day.ogle),
                    Triple(PrayerAlarmScheduler.PRAYER_IKINDI, day.localDate, day.ikindi),
                    Triple(PrayerAlarmScheduler.PRAYER_AKSAM, day.localDate, day.aksam),
                    Triple(PrayerAlarmScheduler.PRAYER_YATSI, day.localDate, day.yatsi),
                ).asSequence()
            }
            .mapNotNull { (key, localDate, hm) ->
                val triggerMillis = PrayerDateTime.parseIsoHmToMillis(localDate, hm)
                    ?: return@mapNotNull null
                if (triggerMillis <= nowMillis) return@mapNotNull null
                Triple(key, hm, triggerMillis)
            }
            .minByOrNull { (_, _, triggerMillis) -> triggerMillis }
            ?: return null

        return labelForPrayer(context, next.first) to next.second
    }

    private fun labelForPrayer(context: Context, key: String): String {
        return when (key) {
            PrayerAlarmScheduler.PRAYER_IMSAK -> context.getString(R.string.prayertimes_sahur_imsak)
            PrayerAlarmScheduler.PRAYER_GUNES -> context.getString(R.string.prayertimes_sabah_gunes)
            PrayerAlarmScheduler.PRAYER_OGLE -> context.getString(R.string.prayertimes_ogle)
            PrayerAlarmScheduler.PRAYER_IKINDI -> context.getString(R.string.prayertimes_ikindi)
            PrayerAlarmScheduler.PRAYER_AKSAM -> context.getString(R.string.prayertimes_aksam)
            PrayerAlarmScheduler.PRAYER_YATSI -> context.getString(R.string.prayertimes_yatsi)
            else -> key
        }
    }
}

class PrayerTimesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerTimesWidget()

    companion object {
        suspend fun refreshAll(context: Context) {
            PrayerTimesWidget().updateAll(context)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerTimesWidgetEntryPoint {
    fun prayerTimesDao(): PrayerTimesDao

    fun prayerPreferencesDataSource(): PrayerPreferencesDataSource
}
