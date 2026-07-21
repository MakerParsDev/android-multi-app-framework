package com.parsfilo.contentapp.feature.counter.sharing

import android.content.Context
import android.content.Intent
import com.parsfilo.contentapp.feature.counter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZikirShareHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun buildShareText(
        latinText: String,
        arabicText: String,
        completedCount: Int,
        todayTotal: Int,
        currentStreak: Int,
    ): String {
        val streakPart = if (currentStreak >= 2) {
            "🔥 $currentStreak"
        } else {
            ""
        }
        return context.getString(
            R.string.share_text_template,
            completedCount,
            latinText,
            arabicText,
            formatNumber(todayTotal),
        ) + if (streakPart.isNotBlank()) "\n$streakPart" else ""
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.counter_share)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun formatNumber(n: Int): String {
        return String.format(Locale.US, "%,d", n).replace(",", ".")
    }
}
