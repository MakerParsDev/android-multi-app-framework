package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.counter.R

@Composable
fun DailyProgressSection(
    todayTotal: Int,
    dailyGoal: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val d = LocalDimens.current
    val progressAnim = animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "daily_progress")

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(d.space12),
            verticalArrangement = Arrangement.spacedBy(d.space8),
        ) {
            LinearProgressIndicator(
                progress = { progressAnim.value },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (todayTotal >= dailyGoal) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.counter_goal_complete),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            } else {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.counter_daily_goal_progress,
                        formatNumber(todayTotal),
                        formatNumber(dailyGoal),
                        (progressAnim.value * 100f).toInt(),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private fun formatNumber(value: Int): String {
    return java.text.DecimalFormat("#,###").format(value).replace(',', '.')
}
