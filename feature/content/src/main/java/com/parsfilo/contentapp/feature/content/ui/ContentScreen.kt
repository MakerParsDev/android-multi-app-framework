package com.parsfilo.contentapp.feature.content.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.designsystem.tokens.LocalMotion
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.ui.BannerAd
import com.parsfilo.contentapp.feature.content.R
import kotlinx.coroutines.launch

@Composable
fun ContentRoute(
    appName: String,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    onModeChanged: (DisplayMode, DisplayMode) -> Unit = { _, _ -> },
    audioPlayerContent: @Composable () -> Unit = {},
    nativeAdContent: @Composable () -> Unit = {},
    bannerAdUnitId: String,
    onRetry: () -> Unit = {},
    viewModel: ContentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ContentScreen(
        appName = appName,
        uiState = uiState,
        onModeSelected = { newMode ->
            val currentMode = (uiState as? ContentUiState.Success)?.displayMode
            if (currentMode != newMode) {
                viewModel.updateDisplayMode(newMode)
                if (currentMode != null) {
                    onModeChanged(currentMode, newMode)
                }
            }
        },
        onSettingsClick = onSettingsClick,
        onRewardsClick = onRewardsClick,
        onSavePlace = viewModel::saveReadingPlace,
        onClearPlace = viewModel::clearReadingPlace,
        initialViewportVerseId = viewModel.lastVisibleVerseId,
        onVisibleVerseChanged = viewModel::updateVisibleVerse,
        audioPlayerContent = audioPlayerContent,
        nativeAdContent = nativeAdContent,
        bannerAdUnitId = bannerAdUnitId,
        onRetry = {
            viewModel.reload()
            onRetry()
        }
    )
}

@Composable
fun ContentScreen(
    appName: String,
    uiState: ContentUiState,
    onModeSelected: (DisplayMode) -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    audioPlayerContent: @Composable () -> Unit = {},
    nativeAdContent: @Composable () -> Unit = {},
    bannerAdUnitId: String,
    onRetry: () -> Unit = {},
    onSavePlace: (Int) -> Unit = {},
    onClearPlace: () -> Unit = {},
    initialViewportVerseId: Int = 0,
    onVisibleVerseChanged: (Int) -> Unit = {},
) {
    val dimens = LocalDimens.current
    val motion = LocalMotion.current

    when (uiState) {
        ContentUiState.Loading -> {
            ContentLoadingSkeleton(appName = appName)
        }
        is ContentUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimens.space24),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.content_error_friendly),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(dimens.space12))
                AppButton(
                    text = stringResource(R.string.content_retry),
                    onClick = onRetry
                )
            }
        }
        is ContentUiState.Success -> {
            val colorScheme = MaterialTheme.colorScheme
            val verses = uiState.verses
            val listState = rememberLazyListState()
            val readerScrollState = rememberScrollState()
            val scope = rememberCoroutineScope()
            var showVerseJump by rememberSaveable { mutableStateOf(false) }
            var verseQuery by rememberSaveable { mutableStateOf("") }
            var jumpSaveAnchor by remember { mutableStateOf<JumpSaveAnchor?>(null) }
            val hasLongContent = verses.size >= 15
            val showTopBanner = uiState.shouldShowAds && shouldShowTopBannerForScrollableContent(verses.size)
            val showInlineFeedAds = uiState.shouldShowAds && shouldPreferInlineFeedAds(verses.size)
            val savedId = uiState.savedVerseId
            val savedDestination = savedId?.let { id ->
                contentLazyItemIndexForVerse(verses, id, showInlineFeedAds)
            }
            LaunchedEffect(verses, showInlineFeedAds) {
                if (verses.isNotEmpty()) {
                    contentLazyItemIndexForVerse(verses, initialViewportVerseId, showInlineFeedAds)
                        ?.let { listState.scrollToItem(it) }
                    snapshotFlow {
                        val verseKey = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            (item.key as? String)?.startsWith("verse_") == true
                        }?.key as? String
                        listState.isScrollInProgress to verseKey
                    }.collect { (scrolling, key) ->
                        if (scrolling) {
                            key?.removePrefix("verse_")?.toIntOrNull()?.let(onVisibleVerseChanged)
                        }
                    }
                }
            }
            // A short window must not let audio and actions consume the entire verse viewport.
            val density = LocalDensity.current
            val windowSize = LocalWindowInfo.current.containerSize
            val compactHeight = shouldUseCompactReader(
                heightDp = with(density) { windowSize.height.toDp().value },
                fontScale = density.fontScale,
            )
            // Short portrait screens still need the top banner and single-row
            // audio player before verses. Defer tools only in short landscape.
            val compactLandscape = shouldDeferReaderControlsForLandscape(
                compactHeight = compactHeight,
                isLandscape = windowSize.width > windowSize.height,
            )
            val bannerPosition = resolveReaderBannerPosition(showTopBanner, compactLandscape)
            Column(
                modifier = Modifier
                    .testTag("content_detail")
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = motion.durationMedium,
                            easing = motion.emphasizedEasing
                        )
                    )
                    .then(if (compactHeight) Modifier.verticalScroll(readerScrollState) else Modifier)
            ) {
                // Header with App Name and action icons
                AppHeader(
                    appName = appName,
                    onSettingsClick = onSettingsClick,
                    onRewardsClick = onRewardsClick,
                    onReaderToolsClick =
                        if (compactLandscape) {
                            { scope.launch { readerScrollState.animateScrollTo(readerScrollState.maxValue) } }
                        } else {
                            null
                        },
                    onJumpToVerseClick =
                        if (hasLongContent) {
                            {
                                verseQuery = ""
                                showVerseJump = true
                            }
                        } else {
                            null
                        },
                    onSaveReadingPlaceClick =
                        if (hasLongContent) {
                            {
                                val layout = listState.layoutInfo
                                val visible = layout.visibleItemsInfo.map { item ->
                                    VisibleContentItem(item.key, item.offset, item.size)
                                }
                                val id = verseToSaveAfterJump(
                                    visible = visible,
                                    viewportStart = layout.viewportStartOffset,
                                    viewportEnd = layout.viewportEndOffset,
                                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                                    jumpAnchor = jumpSaveAnchor,
                                )
                                if (id != null && verses.any { it.id == id }) onSavePlace(id)
                                jumpSaveAnchor = null
                            }
                        } else {
                            null
                        },
                    onResumeReadingPlaceClick =
                        if (savedId != null && savedDestination != null) {
                            {
                                scope.launch {
                                    listState.scrollToItem(savedDestination)
                                    onVisibleVerseChanged(savedId)
                                    jumpSaveAnchor = JumpSaveAnchor(
                                        verseId = savedId,
                                        firstVisibleItemIndex = listState.firstVisibleItemIndex,
                                        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    savedReadingVerseId = if (hasLongContent) savedId else null,
                    onClearReadingPlaceClick = if (hasLongContent && savedId != null) onClearPlace else null,
                )

                // Restore the top banner on normal screens, above audio and verses.
                // On short windows, reserve the initial viewport for the reader.
                if (bannerPosition == ReaderBannerPosition.ABOVE_VERSES) {
                    BannerAd(
                        adUnitId = bannerAdUnitId,
                        placement = AdPlacement.BANNER_CONTENT_DETAIL,
                        showPlacementLabels = false,
                    )
                }

                // Gold divider
                HorizontalDivider(
                    thickness = dimens.stroke,
                    color = colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = dimens.space24)
                )

                if (verses.isEmpty()) {
                    // Placeholder for flavors with no content yet
                    Box(
                        modifier = Modifier
                            .testTag("content_list")
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(dimens.space32)
                        ) {
                            Text(
                                text = "\u2726",
                                fontSize = 48.sp,
                                color = colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(dimens.space16))
                            Text(
                                text = stringResource(R.string.content_empty_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(dimens.space8))
                            Text(
                                text = stringResource(R.string.content_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Audio Player: keep controls with the top banner in portrait.
                    if (!compactLandscape) {
                        audioPlayerContent()
                        Spacer(modifier = Modifier.height(dimens.space4))
                    }

                    // Verse Cards + Native Ads (her 5 ayetten sonra)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .testTag("content_list")
                            .then(if (compactHeight) Modifier.height(260.dp) else Modifier.weight(1f))
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = dimens.space6, vertical = dimens.space2)
                    ) {
                        verses.forEachIndexed { index, verse ->
                            item(key = "verse_${verse.id}") {
                                VerseItem(
                                    verse = verse,
                                    displayMode = uiState.displayMode,
                                    fontSize = uiState.fontSize,
                                    modifier =
                                        if (index == 0) {
                                            Modifier.testTag("content_first_item")
                                        } else {
                                            Modifier
                                        },
                                )
                            }

                            if (showInlineFeedAds && shouldInsertNativeAdAfterVerse(index, verses.size)) {
                                item(key = "native_ad_$index") {
                                    nativeAdContent()
                                }
                            }
                        }
                    }
                    if (showVerseJump && hasLongContent) {
                        val number = canonicalVerseId(verseQuery)
                        val destination = number?.let {
                            contentLazyItemIndexForVerse(verses, it, showInlineFeedAds)
                        }
                        AlertDialog(
                            onDismissRequest = { showVerseJump = false },
                            title = { Text(stringResource(R.string.content_jump_to_verse)) },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = verseQuery,
                                        onValueChange = { verseQuery = it },
                                        label = { Text(stringResource(R.string.content_verse_number)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        isError = verseQuery.isNotEmpty() && destination == null,
                                        modifier = Modifier.testTag("content_verse_number"),
                                    )
                                    if (verseQuery.isNotEmpty() && destination == null) {
                                        Text(
                                            stringResource(R.string.content_verse_not_found),
                                            color = colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = destination != null,
                                    onClick = {
                                        val target = destination ?: return@TextButton
                                        val verseId = number ?: return@TextButton
                                        showVerseJump = false
                                        scope.launch {
                                            listState.scrollToItem(target)
                                            onVisibleVerseChanged(verseId)
                                            jumpSaveAnchor = JumpSaveAnchor(
                                                verseId = verseId,
                                                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                                                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                                            )
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.content_jump_action))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showVerseJump = false }) {
                                    Text(stringResource(R.string.content_jump_cancel))
                                }
                            },
                        )
                    }

                    if (compactLandscape) {
                        // Keep the initial landscape viewport for verses.
                        audioPlayerContent()
                    }

                    // Short windows retain a lower banner to prioritize readable verses.
                    if (bannerPosition == ReaderBannerPosition.AFTER_VERSES) {
                        BannerAd(
                            adUnitId = bannerAdUnitId,
                            placement = AdPlacement.BANNER_CONTENT_DETAIL,
                            showPlacementLabels = false,
                        )
                    }

                    BottomModeSelector(
                        currentMode = uiState.displayMode,
                        onModeSelected = onModeSelected
                    )
                    if (compactHeight) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth().testTag("content_back_to_reading"),
                            onClick = { scope.launch { readerScrollState.animateScrollTo(0) } },
                        ) {
                            Text(stringResource(R.string.content_back_to_reading))
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldDeferReaderControlsForLandscape(compactHeight: Boolean, isLandscape: Boolean): Boolean =
    compactHeight && isLandscape

internal fun shouldStackContentHeaderTitle(fontScale: Float): Boolean = fontScale >= 1.3f

@Composable
fun AppHeader(
    appName: String,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    onReaderToolsClick: (() -> Unit)? = null,
    onJumpToVerseClick: (() -> Unit)? = null,
    onSaveReadingPlaceClick: (() -> Unit)? = null,
    onResumeReadingPlaceClick: (() -> Unit)? = null,
    savedReadingVerseId: Int? = null,
    onClearReadingPlaceClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dimens = LocalDimens.current
    val largeText = shouldStackContentHeaderTitle(LocalDensity.current.fontScale)
    val titleStyle = if (largeText) {
        // Keep the full localized page title readable while prioritizing
        // actual reading space at high system font scales.
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = 20.sp,
            lineHeight = 26.sp,
        )
    }

    val settingsAction: @Composable () -> Unit = {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(dimens.iconXl)
                .background(colorScheme.secondaryContainer.copy(alpha = 0.18f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.content_open_settings),
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(dimens.iconMd)
            )
        }
    }

    val headerTitle: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            text = appName,
            style = titleStyle.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = if (largeText) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.testTag("content_header_title")
        )
    }

    val readerAction: @Composable () -> Unit = {
        if (
            onReaderToolsClick != null ||
            onJumpToVerseClick != null ||
            onSaveReadingPlaceClick != null ||
            onClearReadingPlaceClick != null
        ) {
            var readerMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { readerMenuExpanded = true },
                    modifier = Modifier.size(dimens.iconXl).testTag("content_reader_tools"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.content_reader_tools),
                        tint = colorScheme.onPrimaryContainer,
                    )
                }
                DropdownMenu(
                    expanded = readerMenuExpanded,
                    onDismissRequest = { readerMenuExpanded = false },
                ) {
                    if (onJumpToVerseClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.content_jump_to_verse)) },
                            onClick = {
                                readerMenuExpanded = false
                                onJumpToVerseClick()
                            },
                            modifier = Modifier.testTag("content_go_to_verse"),
                        )
                    }
                    if (onSaveReadingPlaceClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.content_save_reading_place)) },
                            onClick = {
                                readerMenuExpanded = false
                                onSaveReadingPlaceClick()
                            },
                            modifier = Modifier.testTag("content_save_current_verse"),
                        )
                    }
                    if (onResumeReadingPlaceClick != null && savedReadingVerseId != null) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.content_resume_at_verse, savedReadingVerseId))
                            },
                            onClick = {
                                readerMenuExpanded = false
                                onResumeReadingPlaceClick()
                            },
                            modifier = Modifier.testTag("content_resume_reading"),
                        )
                    } else if (savedReadingVerseId != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.content_saved_verse_missing)) },
                            onClick = {},
                            enabled = false,
                        )
                    }
                    if (onClearReadingPlaceClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.content_clear_saved_place)) },
                            onClick = {
                                readerMenuExpanded = false
                                onClearReadingPlaceClick()
                            },
                            modifier = Modifier.testTag("content_clear_saved_place"),
                        )
                    }
                    if (onReaderToolsClick != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.content_reader_tools)) },
                            onClick = {
                                readerMenuExpanded = false
                                onReaderToolsClick()
                            },
                            modifier = Modifier.testTag("content_show_reading_controls"),
                        )
                    }
                }
            }
        }
    }

    val rewardsAction: @Composable () -> Unit = {
        IconButton(
            onClick = onRewardsClick,
            modifier = Modifier
                .size(dimens.iconXl)
                .background(colorScheme.secondaryContainer.copy(alpha = 0.18f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.CardGiftcard,
                contentDescription = stringResource(R.string.content_open_rewards),
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(dimens.iconMd)
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space6, vertical = dimens.space4),
        color = colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(
            bottomStart = dimens.radiusLarge,
            bottomEnd = dimens.radiusLarge
        ),
        tonalElevation = dimens.elevationLow,
        shadowElevation = dimens.elevationLow
    ) {
        if (largeText) {
            // Keep 48dp action targets while giving long localized words the
            // full header width at large accessibility font scales.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.space6, vertical = dimens.space2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    settingsAction()
                    Spacer(modifier = Modifier.weight(1f))
                    readerAction()
                    rewardsAction()
                }
                headerTitle(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.space4, vertical = dimens.space2),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.space6, vertical = dimens.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                settingsAction()
                headerTitle(Modifier.weight(1f).padding(horizontal = dimens.space4))
                readerAction()
                rewardsAction()
            }
        }
    }
}

@Composable
fun BottomModeSelector(
    currentMode: DisplayMode,
    onModeSelected: (DisplayMode) -> Unit
) {
    val dimens = LocalDimens.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space6, vertical = dimens.space2)
    ) {
        val scrollModes = shouldScrollReaderModes(maxWidth.value, LocalDensity.current.fontScale)
        val scrollState = rememberScrollState()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (scrollModes) Modifier.horizontalScroll(scrollState) else Modifier)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space8),
        ) {
            val itemModifier =
                if (scrollModes) {
                    Modifier.width(scrollableReaderModeWidthDp(LocalDensity.current.fontScale).dp)
                } else {
                    Modifier.weight(1f)
                }
            ModeButton(
                text = stringResource(R.string.content_mode_arabic),
                isSelected = currentMode == DisplayMode.ARABIC,
                onClick = { onModeSelected(DisplayMode.ARABIC) },
                modifier = itemModifier,
            )
            ModeButton(
                text = stringResource(R.string.content_mode_latin),
                isSelected = currentMode == DisplayMode.LATIN,
                onClick = { onModeSelected(DisplayMode.LATIN) },
                modifier = itemModifier,
            )
            ModeButton(
                text = stringResource(R.string.content_mode_turkish),
                isSelected = currentMode == DisplayMode.TURKISH,
                onClick = { onModeSelected(DisplayMode.TURKISH) },
                modifier = itemModifier,
            )
        }
    }
}

// Large text makes the stacked reading modes and audio controls taller even on
// nominally medium-height windows. Keep the whole reader vertically reachable.
internal fun shouldUseCompactReader(heightDp: Float, fontScale: Float = 1f): Boolean =
    heightDp < 420f || (fontScale >= 1.5f && heightDp < 720f)

// Horizontally scroll the mode selector instead of stacking three tall rows.
// Preserve 48dp minimum touch targets and large-text accessibility.
internal fun shouldScrollReaderModes(widthDp: Float, fontScale: Float): Boolean =
    widthDp < 360f || (fontScale >= 1.3f && widthDp < 600f)

// Scrolled mode chips need enough width for translated labels at large font scales.
internal fun scrollableReaderModeWidthDp(fontScale: Float): Float = 120f * fontScale.coerceAtLeast(1f)

@Composable
private fun ContentLoadingSkeleton(appName: String) {
    val dimens = LocalDimens.current
    val shimmer = rememberContentSkeletonBrush()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space6, vertical = dimens.space4)
                .height(dimens.topBarHeight + dimens.space8)
                .clip(
                    RoundedCornerShape(
                        bottomStart = dimens.radiusLarge,
                        bottomEnd = dimens.radiusLarge
                    )
                )
                .background(shimmer)
        ) {
            Text(
                text = appName,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }

        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space6, vertical = dimens.space4)
                .height(52.dp),
            brush = shimmer
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space6)
                .height(76.dp),
            brush = shimmer
        )
        repeat(4) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.space6, vertical = dimens.space4)
                    .height(128.dp),
                brush = shimmer
            )
        }
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    brush: Brush
) {
    val dimens = LocalDimens.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusLarge))
            .background(brush)
    )
}

@Composable
private fun rememberContentSkeletonBrush(): Brush {
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "content_skeleton")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motion.durationSlow * 4,
                easing = motion.emphasizedEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "content_skeleton_translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.5f),
            base.copy(alpha = 0.9f),
            base.copy(alpha = 0.5f)
        ),
        start = Offset(translate - 450f, 0f),
        end = Offset(translate, 260f)
    )
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val dimens = LocalDimens.current
    val shape = RoundedCornerShape(dimens.radiusXLarge)

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = dimens.stroke + dimens.strokeThin,
                        color = colorScheme.outline.copy(alpha = 0.7f),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant,
        contentColor = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        shadowElevation = dimens.elevationNone
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = dimens.space6, vertical = dimens.space4),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Preview(showBackground = true, name = "Content Loading")
@Composable
private fun ContentScreenLoadingPreview() {
    AppTheme(flavorName = "yasinsuresi") {
        ContentScreen(
            appName = "Yasin Suresi",
            uiState = ContentUiState.Loading,
            onModeSelected = {},
            bannerAdUnitId = "test"
        )
    }
}

@Preview(showBackground = true, name = "Content Success")
@Composable
private fun ContentScreenSuccessPreview() {
    AppTheme(flavorName = "yasinsuresi") {
        ContentScreen(
            appName = "Yasin Suresi",
            uiState = ContentUiState.Success(
                verses = listOf(
                    Verse(
                        id = 1,
                        arabic = "يس",
                        latin = "Yā Sīn",
                        turkish = "Ya Sin"
                    ),
                    Verse(
                        id = 2,
                        arabic = "وَالْقُرْآنِ الْحَكِيمِ",
                        latin = "Vel kur'ânil hakîm",
                        turkish = "Hikmet dolu Kur'an'a andolsun."
                    ),
                ),
                displayMode = DisplayMode.ARABIC,
                fontSize = 30,
                shouldShowAds = false
            ),
            onModeSelected = {},
            bannerAdUnitId = "test"
        )
    }
}
