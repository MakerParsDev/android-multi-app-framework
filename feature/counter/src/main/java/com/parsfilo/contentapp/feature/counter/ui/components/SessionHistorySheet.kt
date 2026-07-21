package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistorySheet(
    sessions: List<ZikirSession>,
    isPremium: Boolean,
    historyUnlockedForSession: Boolean,
    onUnlockWithAd: () -> Unit,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val d = LocalDimens.current
    val visibleSessions = if (isPremium || historyUnlockedForSession) sessions else sessions.take(10)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space10),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.counter_history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (!isPremium && !historyUnlockedForSession && sessions.size > 10) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.counter_history_premium_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                AppButton(
                    text = androidx.compose.ui.res.stringResource(R.string.counter_unlock_history_with_ad),
                    onClick = onUnlockWithAd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (visibleSessions.isEmpty()) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.counter_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = d.space16),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = d.space24),
                    verticalArrangement = Arrangement.spacedBy(d.space8),
                ) {
                    itemsIndexed(visibleSessions, key = { _, item -> item.id }) { index, session ->
                        SessionHistoryItem(session)
                        if (!isPremium && content != null && (index + 1) % 3 == 0) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryItem(session: ZikirSession) {
    val d = LocalDimens.current
    AppCard {
        Column(modifier = Modifier.padding(d.space12), verticalArrangement = Arrangement.spacedBy(d.space4)) {
            Text(
                text = "📿 ${session.latinText}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${session.completedCount}/${session.targetCount} ${if (session.isComplete) "✅" else ""}",
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = formatTimestamp(session.completedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.counter_session_seconds_format,
                    session.durationSeconds.toInt().coerceAtLeast(0),
                    session.durationSeconds,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}
