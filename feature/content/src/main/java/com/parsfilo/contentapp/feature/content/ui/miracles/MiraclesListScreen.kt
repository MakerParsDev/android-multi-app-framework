package com.parsfilo.contentapp.feature.content.ui.miracles

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.designsystem.tokens.LocalMotion
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.ui.BannerAd
import com.parsfilo.contentapp.feature.content.R
import com.parsfilo.contentapp.feature.content.ui.shouldInsertInlineFeedAdAfterItem
import com.parsfilo.contentapp.feature.content.ui.shouldPreferInlineFeedAds
import com.parsfilo.contentapp.feature.content.ui.shouldShowTopBannerForScrollableContent
import kotlinx.coroutines.launch

@Composable
fun MiraclesListRoute(
    onPrayerClick: (Int) -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    nativeAdContent: @Composable () -> Unit = {},
    audioPlayerContent: (@Composable () -> Unit)? = null,
    onPlayAllAudioClick: (() -> Unit)? = null,
    bannerAdUnitId: String,
    variant: MiraclesContentVariant = MiraclesContentVariant.MUCIZEDUALAR,
    headerTitle: String = "",
    viewModel: MiraclesListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MiraclesListScreen(
        uiState = uiState,
        onPrayerClick = onPrayerClick,
        onSettingsClick = onSettingsClick,
        onRewardsClick = onRewardsClick,
        nativeAdContent = nativeAdContent,
        audioPlayerContent = audioPlayerContent,
        onPlayAllAudioClick = onPlayAllAudioClick,
        bannerAdUnitId = bannerAdUnitId,
        variant = variant,
        headerTitle = headerTitle,
    )
}

@Composable
fun MiraclesListScreen(
    uiState: MiraclesListUiState,
    onPrayerClick: (Int) -> Unit,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    nativeAdContent: @Composable () -> Unit = {},
    audioPlayerContent: (@Composable () -> Unit)? = null,
    onPlayAllAudioClick: (() -> Unit)? = null,
    bannerAdUnitId: String,
    variant: MiraclesContentVariant = MiraclesContentVariant.MUCIZEDUALAR,
    headerTitle: String = "",
) {
    val dimens = LocalDimens.current
    val motion = LocalMotion.current
    val colorScheme = MaterialTheme.colorScheme

    when (uiState) {
        MiraclesListUiState.Loading -> {
            MiraclesListLoadingSkeleton()
        }
        is MiraclesListUiState.Error -> {
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
        is MiraclesListUiState.Success -> {
            val prayers = uiState.prayers
            var searchExpanded by rememberSaveable { mutableStateOf(false) }
            var searchQuery by rememberSaveable { mutableStateOf("") }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val isEsmaSearchOpen = variant == MiraclesContentVariant.ESMAUL_HUSNA && searchExpanded
            val visible = remember(prayers, isEsmaSearchOpen, searchQuery) {
                if (isEsmaSearchOpen) {
                    findEsmaNames(prayers, searchQuery)
                } else {
                    prayers.withIndex().toList()
                }
            }
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
                MiraclesListHeader(
                    title = headerTitle.ifBlank { stringResource(R.string.miracles_default_title) },
                    onSettingsClick = onSettingsClick,
                    onRewardsClick = onRewardsClick,
                    showSearch = variant == MiraclesContentVariant.ESMAUL_HUSNA,
                    onSearchClick = { searchExpanded = !searchExpanded },
                )

                HorizontalDivider(
                    thickness = dimens.stroke,
                    color = colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = dimens.space24)
                )

                if (isEsmaSearchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { value ->
                            searchQuery = value
                            scope.launch { listState.scrollToItem(0) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.space12, vertical = dimens.space8)
                            .testTag("esma_search_input"),
                        singleLine = true,
                        label = { Text(stringResource(R.string.esma_search_label)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    scope.launch { listState.scrollToItem(0) }
                                }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.esma_search_clear),
                                    )
                                }
                            }
                        },
                    )
                }

                if (showTopBanner) {
                    BannerAd(
                        adUnitId = bannerAdUnitId,
                        placement = AdPlacement.BANNER_CONTENT_LIST,
                    )
                }

                if (variant == MiraclesContentVariant.ESMAUL_HUSNA &&
                    onPlayAllAudioClick != null &&
                    audioPlayerContent != null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.space12)
                    ) {
                        FilledTonalButton(
                            onClick = onPlayAllAudioClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("miracles_play_all_audio")
                        ) {
                            Text(text = stringResource(R.string.miracles_play_all_audio))
                        }
                        audioPlayerContent()
                    }
                    Spacer(modifier = Modifier.height(dimens.space8))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .testTag("miracles_list")
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = dimens.space12, top = dimens.space16, end = dimens.space12, bottom = dimens.space8),
                    verticalArrangement = Arrangement.spacedBy(dimens.space8)
                ) {
                    if (isEsmaSearchOpen && visible.isEmpty()) {
                        item(key = "esma_no_search_results") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(dimens.space24),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(dimens.space8),
                            ) {
                                Text(
                                    text = stringResource(R.string.esma_search_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(onClick = {
                                    searchQuery = ""
                                    scope.launch { listState.scrollToItem(0) }
                                }) { Text(stringResource(R.string.esma_search_clear)) }
                            }
                        }
                    }
                    visible.forEach { (index, prayer) ->
                        item(key = "miracle_$index") {
                            MiraclesListItem(
                                prayer = prayer,
                                index = index + 1,
                                variant = variant,
                                onClick = { onPrayerClick(index) },
                                modifier =
                                    if (index == 0) {
                                        Modifier.testTag("miracles_first_item")
                                    } else {
                                        Modifier
                                    },
                            )
                        }

                        if ((!isEsmaSearchOpen || searchQuery.isBlank()) &&
                            showInlineFeedAds &&
                            shouldInsertInlineFeedAdAfterItem(index, prayers.size)
                        ) {
                            item(key = "miracles_native_ad_$index") {
                                nativeAdContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiraclesListLoadingSkeleton() {
    val dimens = LocalDimens.current
    val shimmer = rememberMiraclesListSkeletonBrush()
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
                    .height(92.dp),
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
private fun rememberMiraclesListSkeletonBrush(): Brush {
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "miracles_list_skeleton")
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
        label = "miracles_list_skeleton_translate"
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
fun MiraclesListHeader(
    title: String,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    showSearch: Boolean = false,
    onSearchClick: () -> Unit = {},
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
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(dimens.iconXl)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.content_open_settings),
                tint = colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }

        Text(
            text = title,
            color = colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (showSearch) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(dimens.iconXl).testTag("esma_search_toggle")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.esma_search_label),
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
        }

        IconButton(
            onClick = onRewardsClick,
            modifier = Modifier.size(dimens.iconXl)
        ) {
            Icon(
                imageVector = Icons.Filled.CardGiftcard,
                contentDescription = stringResource(R.string.content_open_rewards),
                tint = colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }
    }
}

@Composable
fun MiraclesListItem(
    prayer: MiraclesPrayer,
    index: Int,
    variant: MiraclesContentVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
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
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.space32 + dimens.space4)
                    .background(colorScheme.primaryContainer, RoundedCornerShape(dimens.radiusSmall)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    color = colorScheme.onPrimaryContainer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.size(dimens.space12))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (variant) {
                        MiraclesContentVariant.MUCIZEDUALAR -> prayer.duaIsim
                        MiraclesContentVariant.ESMAUL_HUSNA -> prayer.duaLatinOkunus.ifBlank { prayer.duaIsim }
                    },
                    color = colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(dimens.space4))

                val subtitle = when (variant) {
                    MiraclesContentVariant.MUCIZEDUALAR -> prayer.duaAciklama
                    MiraclesContentVariant.ESMAUL_HUSNA -> prayer.duaArapca
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = if (variant == MiraclesContentVariant.ESMAUL_HUSNA) 16.sp else 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Detay",
            tint = colorScheme.secondary,
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}

@Preview(showBackground = true, name = "Miracles List Loading")
@Composable
private fun MiraclesListLoadingPreview() {
    AppTheme(flavorName = "mucizedualar") {
        MiraclesListScreen(
            uiState = MiraclesListUiState.Loading,
            onPrayerClick = {},
            bannerAdUnitId = "test",
            headerTitle = "Mucize Dualar",
        )
    }
}

@Preview(showBackground = true, name = "Miracles List Success")
@Composable
private fun MiraclesListSuccessPreview() {
    val samplePrayers = listOf(
        MiraclesPrayer(
            duaIsim = "Sıkıntı Duası",
            duaAciklama = "Sıkıntı anında okunması tavsiye edilen dua.",
            duaBesmele = "Bismillahirrahmanirrahim",
            duaArapca = "لَا إِلَٰهَ إِلَّا أَنْتَ سُبْحَانَكَ"
        ),
        MiraclesPrayer(
            duaIsim = "Korunma Duası",
            duaAciklama = "Kötülüklerden korunmak için okunur.",
            duaBesmele = "Bismillahirrahmanirrahim",
            duaArapca = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ"
        )
    )
    AppTheme(flavorName = "mucizedualar") {
        MiraclesListScreen(
            uiState = MiraclesListUiState.Success(
                prayers = samplePrayers,
                shouldShowAds = false
            ),
            onPrayerClick = {},
            bannerAdUnitId = "test",
            headerTitle = "Mucize Dualar",
        )
    }
}
