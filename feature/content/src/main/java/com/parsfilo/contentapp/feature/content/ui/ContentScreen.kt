package com.parsfilo.contentapp.feature.content.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.parsfilo.contentapp.feature.ads.ui.BannerAd
import com.parsfilo.contentapp.feature.content.R

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
    LaunchedEffect(Unit) {
        viewModel.ensureArabicDefault()
    }

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
    onRetry: () -> Unit = {}
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
            val showTopBanner = uiState.shouldShowAds && shouldShowTopBannerForScrollableContent(verses.size)
            val showInlineFeedAds = uiState.shouldShowAds && shouldPreferInlineFeedAds(verses.size)
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
            ) {
                // Header with App Name and action icons
                AppHeader(
                    appName = appName,
                    onSettingsClick = onSettingsClick,
                    onRewardsClick = onRewardsClick,
                )

                if (showTopBanner) {
                    BannerAd(
                        adUnitId = bannerAdUnitId,
                        showPlacementLabels = false,
                        modifier = Modifier.padding(horizontal = dimens.space6)
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
                    // Audio Player
                    audioPlayerContent()

                    Spacer(modifier = Modifier.height(dimens.space4))

                    // Verse Cards + Native Ads (her 5 ayetten sonra)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
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
                    // Bottom Mode Selector
                    BottomModeSelector(
                        currentMode = uiState.displayMode,
                        onModeSelected = onModeSelected
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeader(
    appName: String,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val dimens = LocalDimens.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space6, vertical = dimens.space4),
        color = colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(
            bottomStart = dimens.radiusLarge,
            bottomEnd = dimens.radiusLarge
        ),
        tonalElevation = dimens.elevationMedium,
        shadowElevation = dimens.elevationHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space6, vertical = dimens.space6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol: Ayarlar ikonu
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(dimens.iconXl)
                    .background(colorScheme.secondaryContainer.copy(alpha = 0.24f), CircleShape)
                    .border(
                        width = dimens.stroke,
                        color = colorScheme.secondary.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Ayarlar",
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(dimens.iconMd)
                )
            }

            // Orta: Başlık
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            // Sağ: Ödüller ikonu
            IconButton(
                onClick = onRewardsClick,
                modifier = Modifier
                    .size(dimens.iconXl)
                    .background(colorScheme.secondaryContainer.copy(alpha = 0.24f), CircleShape)
                    .border(
                        width = dimens.stroke,
                        color = colorScheme.secondary.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = "Ödüller",
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(dimens.iconMd)
                )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space6, vertical = dimens.space2),
        horizontalArrangement = Arrangement.spacedBy(dimens.space8)
    ) {
        ModeButton(
            text = stringResource(R.string.content_mode_arabic),
            isSelected = currentMode == DisplayMode.ARABIC,
            onClick = { onModeSelected(DisplayMode.ARABIC) },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = stringResource(R.string.content_mode_latin),
            isSelected = currentMode == DisplayMode.LATIN,
            onClick = { onModeSelected(DisplayMode.LATIN) },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = stringResource(R.string.content_mode_turkish),
            isSelected = currentMode == DisplayMode.TURKISH,
            onClick = { onModeSelected(DisplayMode.TURKISH) },
            modifier = Modifier.weight(1f)
        )
    }
}

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
                .height(dimens.topBarHeight + dimens.space20)
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

    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier
            .height(dimens.buttonHeight)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = dimens.stroke + dimens.strokeThin,
                        color = colorScheme.outline.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(dimens.radiusXLarge)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(dimens.radiusXLarge),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = dimens.space6),
        elevation = if (isSelected) ButtonDefaults.buttonElevation(defaultElevation = dimens.space4) else ButtonDefaults.buttonElevation(defaultElevation = dimens.elevationNone)
    ) {
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant
        )
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
