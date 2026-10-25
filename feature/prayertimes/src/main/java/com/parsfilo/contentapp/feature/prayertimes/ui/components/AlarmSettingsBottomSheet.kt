package com.parsfilo.contentapp.feature.prayertimes.ui.components

import android.app.AlarmManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAlarmScheduleStatus
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAlarmSettings
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant

internal data class AlarmSoundOption(
    val title: String,
    val uri: String?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmSettingsBottomSheet(
    variant: PrayerAppVariant,
    settings: PrayerAlarmSettings,
    alarmScheduleStatus: PrayerAlarmScheduleStatus,
    hasLocation: Boolean,
    onChooseLocationOrRefresh: () -> Unit,
    onDismissRequest: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onOffsetChanged: (Int) -> Unit,
    onPrayerKeysChanged: (Set<String>) -> Unit,
    onSoundSelected: (String?) -> Unit,
    onTestSound: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }

    fun hasExactAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager?.canScheduleExactAlarms() == true
    var exactAccess by remember { mutableStateOf(hasExactAccess()) }
    DisposableEffect(lifecycleOwner, alarmManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exactAccess = hasExactAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val defaultSoundTitle = stringResource(R.string.prayertimes_alarm_sound_default)
    val soundOptions = rememberSystemAlarmSounds(defaultSoundTitle)
    val allOptions = prayerItemsForVariant(variant)
    val selectedKeys = settings.selectedPrayerKeys.ifEmpty {
        allOptions.map { it.key }.toSet()
    }
    var expanded by remember { mutableStateOf(false) }
    var selectedSoundUri by remember(settings.soundUri) { mutableStateOf(settings.soundUri) }
    val selectedSound =
        soundOptions.firstOrNull { it.uri == selectedSoundUri } ?: soundOptions.firstOrNull()

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.prayertimes_alarm_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                AssistChip(
                    onClick = { onEnabledChanged(!settings.enabled) },
                    label = {
                        Text(
                            text = if (settings.enabled) {
                                stringResource(R.string.prayertimes_alarm_enabled)
                            } else {
                                stringResource(R.string.prayertimes_alarm_disabled)
                            },
                        )
                    },
                )
            }

            Text(
                text = stringResource(R.string.prayertimes_alarm_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (settings.enabled) {
                val scheduleText = when (alarmScheduleStatus) {
                    PrayerAlarmScheduleStatus.SCHEDULED -> R.string.prayertimes_alarm_scheduled
                    PrayerAlarmScheduleStatus.NOT_SCHEDULED -> {
                        if (hasLocation) {
                            R.string.prayertimes_alarm_schedule_unavailable
                        } else {
                            R.string.prayertimes_alarm_choose_location
                        }
                    }
                    PrayerAlarmScheduleStatus.UNKNOWN -> R.string.prayertimes_alarm_unverified
                    PrayerAlarmScheduleStatus.DISABLED -> R.string.prayertimes_alarm_disabled
                }
                Text(
                    text = stringResource(scheduleText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (alarmScheduleStatus == PrayerAlarmScheduleStatus.NOT_SCHEDULED) {
                    AppButton(
                        text = stringResource(
                            if (hasLocation) {
                                R.string.prayertimes_refresh
                            } else {
                                R.string.prayertimes_change_location
                            },
                        ),
                        onClick = onChooseLocationOrRefresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = stringResource(
                        if (exactAccess) {
                            R.string.prayertimes_alarm_exact_enabled
                        } else {
                            R.string.prayertimes_alarm_exact_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!exactAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AppButton(
                        text = stringResource(R.string.prayertimes_alarm_exact_settings),
                        onClick = {
                            // Opens only on explicit user intent. Permission remains optional.
                            val packageUri = Uri.parse("package:${context.packageName}")
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = packageUri
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }.recoverCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.prayertimes_alarm_exact_settings_unavailable,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = stringResource(
                        R.string.prayertimes_alarm_offset_value, settings.offsetMinutes
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        text = "-5",
                        onClick = { onOffsetChanged((settings.offsetMinutes - 5).coerceAtLeast(0)) },
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = "+5",
                        onClick = { onOffsetChanged((settings.offsetMinutes + 5).coerceAtMost(60)) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = stringResource(R.string.prayertimes_alarm_prayers),
                    style = MaterialTheme.typography.titleSmall,
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allOptions.forEach { option ->
                        val selected = selectedKeys.contains(option.key)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = selectedKeys.toMutableSet()
                                if (selected) {
                                    next.remove(option.key)
                                } else {
                                    next.add(option.key)
                                }
                                if (next.isNotEmpty()) {
                                    onPrayerKeysChanged(next)
                                }
                            },
                            label = { Text(text = stringResource(option.labelRes)) },
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedSound?.title.orEmpty(),
                        onValueChange = {},
                        label = { Text(text = stringResource(R.string.prayertimes_alarm_sound_title)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            )
                            .fillMaxWidth(),
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        soundOptions.forEach { sound ->
                            DropdownMenuItem(
                                text = { Text(sound.title) },
                                onClick = {
                                    selectedSoundUri = sound.uri
                                    onSoundSelected(sound.uri)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                AppButton(
                    text = stringResource(R.string.prayertimes_alarm_sound_test),
                    onClick = { onTestSound(selectedSoundUri) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun rememberSystemAlarmSounds(defaultTitle: String): List<AlarmSoundOption> {
    val context = LocalContext.current
    return remember(defaultTitle) {
        val options = mutableListOf(
            AlarmSoundOption(
                title = defaultTitle,
                uri = null,
            )
        )
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_ALARM)
        }
        val seenUris = mutableSetOf<String>()
        val cursor = manager.cursor ?: return@remember options
        cursor.use {
            while (it.moveToNext()) {
                val uri =
                    runCatching { manager.getRingtoneUri(it.position) }.getOrNull()?.toString()
                        ?.trim().orEmpty()
                if (uri.isBlank() || !seenUris.add(uri)) continue

                val title = it.getString(RingtoneManager.TITLE_COLUMN_INDEX)?.trim().orEmpty()
                if (title.isBlank()) continue

                options += AlarmSoundOption(title = title, uri = uri)
            }
        }
        options
    }
}
