package com.parsfilo.contentapp.feature.quran.ui.surelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.ui.component.SuraListItem

@Composable
fun QuranSuraListRoute(
    onSuraClick: (Int) -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
    viewModel: QuranSuraListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    QuranSuraListScreen(
        state = uiState,
        onSuraClick = onSuraClick,
        onBookmarksClick = onBookmarksClick,
        onSettingsClick = onSettingsClick,
        onRewardsClick = onRewardsClick,
        onSearch = viewModel::onSearchQueryChange,
        onRetry = viewModel::retrySync,
        bannerAdContent = bannerAdContent,
        nativeAdContent = nativeAdContent,
    )
}

@Composable
fun QuranSuraListScreen(
    state: SuraListViewModelState,
    onSuraClick: (Int) -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
) {
    val meccanLabel = stringResource(R.string.quran_revelation_meccan)
    val medinanLabel = stringResource(R.string.quran_revelation_medinan)
    var isSearchExpanded by rememberSaveable { mutableStateOf(state.query.isNotBlank()) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = {
            QuranSuraListHeader(
                title = stringResource(R.string.quran_title),
                onSettingsClick = onSettingsClick,
                onSearchToggle = {
                    if (isSearchExpanded) {
                        onSearch("")
                    }
                    isSearchExpanded = !isSearchExpanded
                },
                onRewardsClick = onRewardsClick,
                onBookmarksClick = onBookmarksClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            if (state.shouldShowAds) {
                bannerAdContent?.invoke()
                Spacer(modifier = Modifier.height(8.dp))
            }

            AnimatedVisibility(visible = isSearchExpanded) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    placeholder = { Text(stringResource(R.string.quran_search_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    singleLine = true,
                )
            }

            val lastRead = state.lastRead
            if (lastRead != null) {
                AppCard(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .clickable { onSuraClick(lastRead.suraNumber) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.quran_last_read_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.quran_last_read_format,
                                lastRead.suraNumber,
                                lastRead.ayahNumber,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.isLoading && state.suras.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.quran_loading))
                }
                return@Column
            }

            if (state.errorMessage != null && state.suras.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.quran_error_load_suras),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = state.errorMessage)
                    Spacer(modifier = Modifier.height(12.dp))
                    AppButton(text = stringResource(R.string.quran_retry), onClick = onRetry)
                }
                return@Column
            }

            val suras = state.filteredSuras
            if (suras.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (state.suras.isEmpty()) {
                            stringResource(R.string.quran_empty_offline)
                        } else {
                            stringResource(R.string.quran_no_results)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (state.suras.isEmpty()) {
                            stringResource(R.string.quran_retry)
                        } else {
                            stringResource(R.string.quran_no_results_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                itemsIndexed(
                    items = suras,
                    key = { _, sura -> sura.number },
                ) { index, sura ->
                    SuraListItem(
                        sura = sura,
                        meccanLabel = meccanLabel,
                        medinanLabel = medinanLabel,
                        modifier = Modifier.clickable { onSuraClick(sura.number) },
                    )

                    if (state.shouldShowAds && nativeAdContent != null && (index + 1) % 6 == 0 && index < suras.lastIndex) {
                        nativeAdContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranSuraListHeader(
    title: String,
    onSettingsClick: () -> Unit,
    onSearchToggle: () -> Unit,
    onRewardsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dimens = LocalDimens.current
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = dimens.space6, vertical = 0.dp),
        color = colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(
            bottomStart = dimens.radiusLarge,
            bottomEnd = dimens.radiusLarge,
        ),
        tonalElevation = dimens.elevationMedium,
        shadowElevation = dimens.elevationHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space6, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(
                onClick = onSettingsClick,
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.quran_open_settings),
            )

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Box(contentAlignment = Alignment.CenterEnd) {
                HeaderIconButton(
                    onClick = { menuExpanded = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.quran_more_options),
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.quran_open_rewards)) },
                        onClick = {
                            menuExpanded = false
                            onRewardsClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.quran_open_bookmarks)) },
                        onClick = {
                            menuExpanded = false
                            onBookmarksClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.quran_toggle_search)) },
                        onClick = {
                            menuExpanded = false
                            onSearchToggle()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dimens = LocalDimens.current

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(colorScheme.secondaryContainer.copy(alpha = 0.24f), CircleShape)
            .border(
                width = dimens.stroke,
                color = colorScheme.secondary.copy(alpha = 0.35f),
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colorScheme.onPrimaryContainer,
        )
    }
}
