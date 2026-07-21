package com.parsfilo.contentapp.feature.content.ui.prayer

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.designsystem.tokens.LocalMotion
import com.parsfilo.contentapp.core.model.Prayer
import com.parsfilo.contentapp.core.model.PrayerVerse
import com.parsfilo.contentapp.feature.ads.ui.BannerAd
import com.parsfilo.contentapp.feature.content.ui.shouldInsertInlineFeedAdAfterItem
import com.parsfilo.contentapp.feature.content.ui.shouldPreferInlineFeedAds
import com.parsfilo.contentapp.feature.content.ui.shouldShowTopBannerForScrollableContent

@Composable
fun PrayerListRoute(
    onPrayerClick: (Int) -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    showVerseCount: Boolean = true,
    bannerAdUnitId: String,
    viewModel: PrayerListViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PrayerListScreen(
        uiState = uiState,
        onPrayerClick = onPrayerClick,
        onSettingsClick = onSettingsClick,
        onRewardsClick = onRewardsClick,
        showVerseCount = showVerseCount,
        bannerAdUnitId = bannerAdUnitId,
    ) {
        content()
    }
}

@Composable
fun PrayerListScreen(
    uiState: PrayerListUiState,
    onPrayerClick: (Int) -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    showVerseCount: Boolean = true,
    bannerAdUnitId: String,
    content: @Composable () -> Unit = {},
) {
    val dimens = LocalDimens.current
    val motion = LocalMotion.current
    val colorScheme = MaterialTheme.colorScheme

    when (uiState) {
        PrayerListUiState.Loading -> {
            PrayerListLoadingSkeleton()
        }
        is PrayerListUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Veri yüklenirken hata oluştu: ${uiState.throwable.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is PrayerListUiState.Success -> {
            val prayers = uiState.prayers
            val showTopBanner = uiState.shouldShowAds && shouldShowTopBannerForScrollableContent(prayers.size)
            val showInlineFeedAds = uiState.shouldShowAds && shouldPreferInlineFeedAds(prayers.size)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = motion.durationMedium,
                            easing = motion.emphasizedEasing
                        )
                    )
            ) {
                // Header
                PrayerListHeader(
                    onSettingsClick = onSettingsClick,
                    onRewardsClick = onRewardsClick
                )

                // Gold divider
                HorizontalDivider(
                    thickness = dimens.stroke,
                    color = colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = dimens.space24)
                )

                if (showTopBanner) {
                    // Banner reklam
                    BannerAd(
                        adUnitId = bannerAdUnitId,
                        modifier = Modifier.padding(horizontal = dimens.space6)
                    )
                }

                // Liste
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = dimens.space12, top = dimens.space16, end = dimens.space12, bottom = dimens.space8),
                    verticalArrangement = Arrangement.spacedBy(dimens.space8)
                ) {
                    prayers.forEachIndexed { index, prayer ->
                        item(key = "prayer_${prayer.sureID}_$index") {
                            PrayerListItem(
                                prayer = prayer,
                                onClick = { onPrayerClick(prayer.sureID) },
                                showVerseCount = showVerseCount,
                            )
                        }

                        if (showInlineFeedAds && shouldInsertInlineFeedAdAfterItem(index, prayers.size)) {
                            item(key = "prayer_native_ad_$index") {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerListLoadingSkeleton() {
    val dimens = LocalDimens.current
    val shimmer = rememberPrayerListSkeletonBrush()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = dimens.space12, vertical = dimens.space8),
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
                .height(52.dp),
            brush = shimmer
        )
        repeat(6) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
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
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(brush)
    )
}

@Composable
private fun rememberPrayerListSkeletonBrush(): Brush {
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "prayer_list_skeleton")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motion.durationSlow * 4,
                easing = motion.emphasizedEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "prayer_list_skeleton_translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.45f),
            base.copy(alpha = 0.9f),
            base.copy(alpha = 0.45f)
        ),
        start = Offset(translate - 400f, 0f),
        end = Offset(translate, 220f)
    )
}

@Composable
fun PrayerListHeader(
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {}
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(colorScheme.surface)
            .padding(top = dimens.space12, bottom = dimens.space4)
            .padding(horizontal = dimens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Sol: Ayarlar ikonu
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(dimens.iconXl)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Ayarlar",
                tint = colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }

        // Orta: Başlık
        Text(
            text = "Namaz Sureleri ve Duaları",
            color = colorScheme.onSurface,
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
                tint = colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }
    }
}

@Composable
fun PrayerListItem(
    prayer: Prayer,
    onClick: () -> Unit,
    showVerseCount: Boolean = true,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty().lowercase()
    val title = when {
        language.startsWith("de") && prayer.sureAdiDE.isNotBlank() -> prayer.sureAdiDE
        language.startsWith("en") && prayer.sureAdiEN.isNotBlank() -> prayer.sureAdiEN
        else -> prayer.sureAdiTR
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(dimens.radiusMedium)
            )
            .border(
                width = dimens.stroke,
                color = colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(dimens.radiusMedium)
            )
            .clickable(onClick = onClick)
            .padding(dimens.space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Türkçe isim (ana başlık)
            Text(
                text = title,
                color = colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(dimens.space4))

            // Arapça isim (alt başlık)
            Text(
                text = prayer.sureAdiAR,
                color = colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )

            if (showVerseCount) {
                Spacer(modifier = Modifier.height(dimens.space4))

                // Ayet sayısı
                Text(
                    text = "${prayer.ayetSayisi} Ayet",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        // Chevron ikonu
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Detay",
            tint = colorScheme.secondary,
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}

@Preview(showBackground = true, name = "Prayer List Loading")
@Composable
private fun PrayerListLoadingPreview() {
    AppTheme(flavorName = "namazsurelerivedualarsesli") {
        PrayerListScreen(
            uiState = PrayerListUiState.Loading,
            onPrayerClick = {},
            bannerAdUnitId = "test"
        )
    }
}

@Preview(showBackground = true, name = "Prayer List Success")
@Composable
private fun PrayerListSuccessPreview() {
    val samplePrayers = listOf(
        Prayer(
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
                )
            )
        ),
        Prayer(
            sureID = 2,
            sureAdiAR = "آيَةُ الْكُرْسِيِّ",
            sureAdiEN = "Ayat al-Kursi",
            sureAdiTR = "Ayetel Kürsi",
            ayetSayisi = 1,
            sureMedya = "AYETELKURSI.MP3",
            ayetler = emptyList()
        )
    )
    AppTheme(flavorName = "namazsurelerivedualarsesli") {
        PrayerListScreen(
            uiState = PrayerListUiState.Success(
                prayers = samplePrayers,
                shouldShowAds = false
            ),
            onPrayerClick = {},
            bannerAdUnitId = "test"
        )
    }
}
