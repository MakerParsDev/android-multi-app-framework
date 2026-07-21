package com.parsfilo.contentapp.feature.prayertimes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerCity
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerCountry
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerDistrict
import com.parsfilo.contentapp.feature.prayertimes.ui.components.PrayerTimesBackground

@Composable
fun PrayerLocationPickerRoute(
    appName: String,
    onBackClick: () -> Unit,
    viewModel: PrayerLocationPickerViewModel = hiltViewModel(),
    content: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PrayerLocationPickerScreen(
        appName = appName,
        uiState = uiState,
        onBackClick = onBackClick,
        onCountrySelected = viewModel::onCountrySelected,
        onCitySelected = viewModel::onCitySelected,
        onDistrictSelected = viewModel::onDistrictSelected,
        onSave = { viewModel.saveSelection(onSaved = onBackClick) },
        onStepSelected = viewModel::onStepSelected,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrayerLocationPickerScreen(
    appName: String,
    uiState: PrayerLocationPickerUiState,
    onBackClick: () -> Unit,
    onCountrySelected: (PrayerCountry) -> Unit,
    onCitySelected: (PrayerCity) -> Unit,
    onDistrictSelected: (PrayerDistrict) -> Unit,
    onSave: () -> Unit,
    onStepSelected: (LocationStep) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val canSave = uiState.selectedCountry != null &&
        uiState.selectedCity != null &&
        uiState.selectedDistrict != null

    PrayerTimesBackground {
        Scaffold(
            containerColor = PrayerTimesColors.Background,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PrayerTimesColors.Background,
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.prayertimes_change_location),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = prayerHeadlineFontFamily(),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.prayertimes_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content?.invoke()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PrayerTimesDesignTokens.GlassSurface.copy(alpha = PrayerTimesDesignTokens.GLASS_ALPHA))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = prayerHeadlineFontFamily()),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    StepProgressBar(
                        step = uiState.step,
                        onStepSelected = onStepSelected,
                        canOpenCity = uiState.selectedCountry != null,
                        canOpenDistrict = uiState.selectedCity != null,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.selectedCountry?.let {
                            AssistChip(
                                onClick = { onStepSelected(LocationStep.COUNTRY) },
                                label = { Text(it.nameTr) },
                            )
                        }
                        uiState.selectedCity?.let {
                            AssistChip(
                                onClick = { onStepSelected(LocationStep.CITY) },
                                label = { Text(it.nameTr) },
                            )
                        }
                        uiState.selectedDistrict?.let {
                            AssistChip(
                                onClick = { onStepSelected(LocationStep.DISTRICT) },
                                label = { Text(it.nameTr) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = stringResource(R.string.prayertimes_search_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.65f),
                            unfocusedContainerColor = PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.45f),
                        ),
                    )
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (uiState.step) {
                        LocationStep.COUNTRY -> {
                            WizardList(
                                modifier = Modifier.weight(1f),
                                items = uiState.filteredCountries,
                                selectedId = uiState.selectedCountry?.id,
                                itemLabel = { it.nameTr },
                                itemId = { it.id },
                                onSelect = onCountrySelected,
                            )
                        }

                        LocationStep.CITY -> {
                            WizardList(
                                modifier = Modifier.weight(1f),
                                items = uiState.filteredCities,
                                selectedId = uiState.selectedCity?.id,
                                itemLabel = { it.nameTr },
                                itemId = { it.id },
                                onSelect = onCitySelected,
                            )
                        }

                        LocationStep.DISTRICT -> {
                            WizardList(
                                modifier = Modifier.weight(1f),
                                items = uiState.filteredDistricts,
                                selectedId = uiState.selectedDistrict?.id,
                                itemLabel = { it.nameTr },
                                itemId = { it.id },
                                onSelect = onDistrictSelected,
                            )
                        }
                    }
                }

                AppButton(
                    text = stringResource(R.string.prayertimes_save_selection),
                    onClick = onSave,
                    enabled = canSave,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrayerTimesDesignTokens.ActionPrimary,
                        contentColor = PrayerTimesDesignTokens.HeaderText,
                        disabledContainerColor = PrayerTimesDesignTokens.ActionPrimary.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StepProgressBar(
    step: LocationStep,
    onStepSelected: (LocationStep) -> Unit,
    canOpenCity: Boolean,
    canOpenDistrict: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppButton(
            text = stringResource(R.string.prayertimes_pick_country),
            onClick = { onStepSelected(LocationStep.COUNTRY) },
            shape = RoundedCornerShape(18.dp),
            colors = stepButtonColors(selected = step == LocationStep.COUNTRY),
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = stringResource(R.string.prayertimes_pick_city),
            onClick = { if (canOpenCity) onStepSelected(LocationStep.CITY) },
            enabled = canOpenCity,
            shape = RoundedCornerShape(18.dp),
            colors = stepButtonColors(selected = step == LocationStep.CITY),
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = stringResource(R.string.prayertimes_pick_district),
            onClick = { if (canOpenDistrict) onStepSelected(LocationStep.DISTRICT) },
            enabled = canOpenDistrict,
            shape = RoundedCornerShape(18.dp),
            colors = stepButtonColors(selected = step == LocationStep.DISTRICT),
            modifier = Modifier.weight(1f),
        )
    }

    Text(
        text = when (step) {
            LocationStep.COUNTRY -> stringResource(R.string.prayertimes_step_country)
            LocationStep.CITY -> stringResource(R.string.prayertimes_step_city)
            LocationStep.DISTRICT -> stringResource(R.string.prayertimes_step_district)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> WizardList(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedId: Int?,
    itemLabel: (T) -> String,
    itemId: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = items,
            key = { item -> itemId(item) },
        ) { item ->
            val selected = selectedId == itemId(item)
            Text(
                text = itemLabel(item),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .clickable { onSelect(item) }
                    .semantics {
                        contentDescription = itemLabel(item)
                    }
                    .background(
                        color = if (selected) {
                            PrayerTimesColors.AccentSoft
                        } else {
                            PrayerTimesColors.Surface
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                color = if (selected) {
                    PrayerTimesColors.Accent
                } else {
                    PrayerTimesColors.NeutralText
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
            )
        }
    }
}

@Composable
private fun stepButtonColors(selected: Boolean) = ButtonDefaults.buttonColors(
    containerColor = if (selected) {
        PrayerTimesColors.Accent
    } else {
        PrayerTimesColors.Surface
    },
    contentColor = if (selected) {
        PrayerTimesColors.Surface
    } else {
        PrayerTimesColors.NeutralText
    },
    disabledContainerColor = PrayerTimesColors.NeutralButton.copy(alpha = 0.65f),
)
