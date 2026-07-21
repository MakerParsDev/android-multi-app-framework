package com.parsfilo.contentapp.feature.content.ui.prayer

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import com.parsfilo.contentapp.core.model.Prayer
import com.parsfilo.contentapp.core.model.PrayerVerse
import com.parsfilo.contentapp.feature.content.R
import com.parsfilo.contentapp.feature.content.ui.shouldInsertNativeAdAfterVerse

@Composable
fun PrayerDetailRoute(
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    onModeChanged: (DisplayMode, DisplayMode) -> Unit = { _, _ -> },
    onAudioFileChanged: (String?) -> Unit = {},
    audioPlayerContent: @Composable () -> Unit = {},
    nativeAdContent: @Composable () -> Unit = {},
    isDebug: Boolean = false,
    viewModel: PrayerDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState) {
        val mediaKey = (uiState as? PrayerDetailUiState.Success)?.prayer?.sureMedya ?: return@LaunchedEffect
        onAudioFileChanged(mediaKey)
    }
    PrayerDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onModeSelected = { newMode ->
            val currentMode = (uiState as? PrayerDetailUiState.Success)?.displayMode
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
        isDebug = isDebug
    )
}

@Composable
fun PrayerDetailScreen(
    uiState: PrayerDetailUiState,
    onBackClick: () -> Unit = {},
    onModeSelected: (DisplayMode) -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    audioPlayerContent: @Composable () -> Unit = {},
    nativeAdContent: @Composable () -> Unit = {},
    isDebug: Boolean = false
) {
    val dimens = LocalDimens.current
    val motion = LocalMotion.current
    val colorScheme = MaterialTheme.colorScheme
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty().lowercase()
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val screenBackground = if (isLightTheme) colorScheme.background else colorScheme.primary
    val dividerColor = if (isLightTheme) {
        colorScheme.outline.copy(alpha = 0.35f)
    } else {
        colorScheme.secondary.copy(alpha = 0.5f)
    }

    when (uiState) {
        PrayerDetailUiState.Loading -> {
            PrayerDetailLoadingSkeleton()
        }
        is PrayerDetailUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Hata: ${uiState.throwable.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is PrayerDetailUiState.Success -> {
            val prayerTitle = when {
                language.startsWith("de") && uiState.prayer.sureAdiDE.isNotBlank() -> uiState.prayer.sureAdiDE
                language.startsWith("en") && uiState.prayer.sureAdiEN.isNotBlank() -> uiState.prayer.sureAdiEN
                else -> uiState.prayer.sureAdiTR
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(screenBackground)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = motion.durationMedium,
                            easing = motion.emphasizedEasing
                        )
                    )
            ) {
                // Header with Prayer Name and action icons
                PrayerDetailHeader(
                    prayerName = prayerTitle,
                    onBackClick = onBackClick,
                    onSettingsClick = onSettingsClick,
                    onRewardsClick = onRewardsClick
                )

                // Gold divider
                HorizontalDivider(
                    thickness = dimens.stroke,
                    color = dividerColor,
                    modifier = Modifier.padding(horizontal = dimens.space24)
                )

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
                    val verses = uiState.prayer.ayetler
                    verses.forEachIndexed { index, verse ->
                        item(key = "verse_${verse.ayetID}") {
                            PrayerVerseItem(
                                verse = verse,
                                displayMode = uiState.displayMode,
                                fontSize = uiState.fontSize
                            )
                        }

                        // Kısa içerikte (<=5) ilk ayetten sonra, uzun içerikte her 5 ayette bir reklam
                        if (uiState.shouldShowAds && shouldInsertNativeAdAfterVerse(index, verses.size)) {
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

@Composable
fun PrayerDetailHeader(
    prayerName: String,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {}
) {
    val dimens = LocalDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = dimens.space6, bottom = dimens.space4)
            .padding(horizontal = dimens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Sol: Geri ikonu
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(dimens.iconXl)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }

        // Orta: Başlık
        Text(
            text = prayerName,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        // Sağ: Ödüller ikonu
        IconButton(
            onClick = onRewardsClick,
            modifier = Modifier.size(dimens.iconXl)
        ) {
            Icon(
                imageVector = Icons.Filled.CardGiftcard,
                contentDescription = "Ödüller",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }
    }
}

@Composable
fun PrayerVerseItem(
    verse: PrayerVerse,
    displayMode: DisplayMode,
    fontSize: Int
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty().lowercase()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.space4, horizontal = dimens.space6)
            .background(
                color = if (isLightTheme) colorScheme.surface else colorScheme.surface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(dimens.radiusMedium)
            )
            .border(
                width = dimens.strokeThin,
                color = colorScheme.secondary.copy(alpha = 0.24f),
                shape = RoundedCornerShape(dimens.radiusMedium)
            )
            .padding(dimens.space14)
    ) {
        when (displayMode) {
            DisplayMode.ARABIC -> {
                Text(
                    text = verse.ayetAR,
                    fontSize = (fontSize + 4).sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    lineHeight = (fontSize + 8).sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DisplayMode.LATIN -> {
                Text(
                    text = verse.ayetLAT,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = (fontSize + 4).sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DisplayMode.TURKISH -> {
                val translationText = when {
                    language.startsWith("de") && verse.ayetDE.isNotBlank() -> verse.ayetDE
                    language.startsWith("en") && verse.ayetEN.isNotBlank() -> verse.ayetEN
                    else -> verse.ayetTR
                }
                Text(
                    text = translationText,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = (fontSize + 4).sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PrayerDetailLoadingSkeleton() {
    val dimens = LocalDimens.current
    val shimmer = rememberPrayerDetailSkeletonBrush()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = dimens.space8, vertical = dimens.space8),
        verticalArrangement = Arrangement.spacedBy(dimens.space10)
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            brush = shimmer
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            brush = shimmer
        )
        repeat(4) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp),
                brush = shimmer
            )
        }
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            brush = shimmer
        )
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
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(brush)
    )
}

@Composable
private fun rememberPrayerDetailSkeletonBrush(): Brush {
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "prayer_detail_skeleton")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motion.durationSlow * 4,
                easing = motion.emphasizedEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "prayer_detail_skeleton_translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.45f),
            base.copy(alpha = 0.9f),
            base.copy(alpha = 0.45f)
        ),
        start = Offset(translate - 420f, 0f),
        end = Offset(translate, 240f)
    )
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
            .padding(horizontal = dimens.space6, vertical = dimens.space2)
            .padding(bottom = dimens.space48 + dimens.space2),
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
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val selectedContainer = colorScheme.secondary
    val selectedText = colorScheme.onSecondary
    val unselectedContainer = if (isLightTheme) colorScheme.surfaceVariant else colorScheme.primary.copy(alpha = 0.6f)
    val unselectedText = if (isLightTheme) colorScheme.onSurfaceVariant else colorScheme.onBackground.copy(alpha = 0.7f)
    val selectedBorder = if (isLightTheme) {
        colorScheme.secondary.copy(alpha = 0.4f)
    } else {
        colorScheme.onBackground.copy(alpha = 0.6f)
    }

    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier
            .height(dimens.space40)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = dimens.stroke + dimens.strokeThin,
                        color = selectedBorder,
                        shape = RoundedCornerShape(dimens.radiusXLarge)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(dimens.radiusXLarge),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) selectedContainer else unselectedContainer
        ),
        contentPadding = PaddingValues(horizontal = dimens.space6),
        elevation = if (isSelected) ButtonDefaults.buttonElevation(defaultElevation = dimens.space4) else ButtonDefaults.buttonElevation(defaultElevation = dimens.elevationNone)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) selectedText else unselectedText
        )
    }
}

@Preview(showBackground = true, name = "Prayer Detail Loading")
@Composable
private fun PrayerDetailLoadingPreview() {
    AppTheme(flavorName = "namazsurelerivedualarsesli") {
        PrayerDetailScreen(
            uiState = PrayerDetailUiState.Loading,
            onModeSelected = {}
        )
    }
}

@Preview(showBackground = true, name = "Prayer Detail Success")
@Composable
private fun PrayerDetailSuccessPreview() {
    val samplePrayer = Prayer(
        sureID = 1,
        sureAdiAR = "الْفَاتِحَة",
        sureAdiEN = "Al-Fatiha",
        sureAdiTR = "Fatiha Suresi",
        ayetSayisi = 7,
        sureMedya = "FATIHA.MP3",
        ayetler = listOf(
            PrayerVerse(
                ayetID = 1,
                ayetAR = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                ayetLAT = "Bismillahirrahmanirrahim",
                ayetTR = "Rahman ve Rahim olan Allah'ın adıyla."
            ),
            PrayerVerse(
                ayetID = 2,
                ayetAR = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
                ayetLAT = "Elhamdülillahi rabbil alemin",
                ayetTR = "Hamd alemlerin Rabbi Allah'a mahsustur."
            )
        )
    )
    AppTheme(flavorName = "namazsurelerivedualarsesli") {
        PrayerDetailScreen(
            uiState = PrayerDetailUiState.Success(
                prayer = samplePrayer,
                displayMode = DisplayMode.ARABIC,
                fontSize = 30,
                shouldShowAds = false
            ),
            onModeSelected = {}
        )
    }
}
