package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.counter.R

@Composable
fun CounterHeaderBar(
    onSettingsClick: () -> Unit,
    onRewardsClick: () -> Unit,
    onReminderClick: () -> Unit,
    onHistoryClick: () -> Unit,
    isHapticEnabled: Boolean,
    isSoundEnabled: Boolean,
    onToggleHaptic: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenZikirList: (() -> Unit)? = null,
    onAddCustomZikir: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
) {
    val d = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = d.space6, vertical = d.space4),
        color = colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(
            bottomStart = d.radiusLarge,
            bottomEnd = d.radiusLarge,
        ),
        tonalElevation = d.elevationMedium,
        shadowElevation = d.elevationHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.space6, vertical = d.space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(d.iconXl)
                    .background(colorScheme.secondaryContainer.copy(alpha = 0.24f), CircleShape)
                    .border(
                        width = d.stroke,
                        color = colorScheme.secondary.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.counter_menu_settings),
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(d.iconMd),
                )
            }

            Text(
                text = stringResource(R.string.counter_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = d.space8),
            )

            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier
                        .size(d.iconXl)
                        .background(colorScheme.secondaryContainer.copy(alpha = 0.24f), CircleShape)
                        .border(
                            width = d.stroke,
                            color = colorScheme.secondary.copy(alpha = 0.35f),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.counter_menu_more),
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(d.iconMd),
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.counter_menu_rewards)) },
                        leadingIcon = { Icon(Icons.Filled.CardGiftcard, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onRewardsClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.counter_reminder_title)) },
                        leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onReminderClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.counter_history_title)) },
                        leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onHistoryClick()
                        },
                    )
                    onOpenZikirList?.let { openList ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.counter_open_zikir_list)) },
                            leadingIcon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                            onClick = {
                                expanded = false
                                openList()
                            },
                        )
                    }
                    onAddCustomZikir?.let { addCustom ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.counter_add_zikir)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = {
                                expanded = false
                                addCustom()
                            },
                        )
                    }
                    onContinue?.let { continueCounter ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.counter_continue)) },
                            onClick = {
                                expanded = false
                                continueCounter()
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isHapticEnabled) {
                                    stringResource(R.string.counter_settings_haptic) + " ✓"
                                } else {
                                    stringResource(R.string.counter_settings_haptic)
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onToggleHaptic()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isSoundEnabled) {
                                    stringResource(R.string.counter_settings_sound) + " ✓"
                                } else {
                                    stringResource(R.string.counter_settings_sound)
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onToggleSound()
                        },
                    )
                }
            }
        }
    }
}
