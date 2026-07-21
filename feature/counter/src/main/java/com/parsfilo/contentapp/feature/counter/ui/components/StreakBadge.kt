package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.counter.R

@Composable
fun StreakBadge(
    streak: Int,
    todayTotal: Int,
    modifier: Modifier = Modifier,
) {
    val d = LocalDimens.current
    val streakAnim = animateIntAsState(targetValue = streak, label = "streak_anim")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(d.space8),
    ) {
        if (streakAnim.value > 0) {
            val badgeColor = when {
                streakAnim.value >= 30 -> MaterialTheme.colorScheme.tertiary
                streakAnim.value >= 7 -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            val icon = if (streakAnim.value >= 30) "💎" else "🔥"
            Text(
                text = "$icon ${
                    pluralStringResource(
                        R.plurals.counter_streak_label,
                        streakAnim.value,
                        streakAnim.value,
                    )
                }",
                modifier = Modifier
                    .background(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(d.radiusPill),
                    )
                    .padding(horizontal = d.space10, vertical = d.space6),
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        Text(
            text = androidx.compose.ui.res.stringResource(
                R.string.counter_today_total,
                java.text.DecimalFormat("#,###").format(todayTotal).replace(',', '.'),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = d.space6),
        )
    }
}
