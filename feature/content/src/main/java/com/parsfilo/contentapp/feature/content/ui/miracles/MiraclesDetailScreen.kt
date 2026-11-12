package com.parsfilo.contentapp.feature.content.ui.miracles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.designsystem.component.ReaderSelectionContainer
import com.parsfilo.contentapp.core.designsystem.component.readerPinchFontSize
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.designsystem.tokens.LocalMotion
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import com.parsfilo.contentapp.feature.content.R

@Composable
fun MiraclesDetailRoute(
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    variant: MiraclesContentVariant = MiraclesContentVariant.MUCIZEDUALAR,
    viewModel: MiraclesDetailViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MiraclesDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onSettingsClick = onSettingsClick,
        onRewardsClick = onRewardsClick,
        variant = variant,
        onFontSizeChange = viewModel::updateFontSize,
    ) {
        content()
    }
}

@Composable
fun MiraclesDetailScreen(
    uiState: MiraclesDetailUiState,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    variant: MiraclesContentVariant = MiraclesContentVariant.MUCIZEDUALAR,
    onFontSizeChange: (Int) -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val dimens = LocalDimens.current
    val uriHandler = LocalUriHandler.current
    val motion = LocalMotion.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val screenBackground = if (isLightTheme) colorScheme.background else colorScheme.primary
    val overlayCard = if (isLightTheme) {
        colorScheme.secondaryContainer.copy(alpha = 0.3f)
    } else {
        colorScheme.surface.copy(alpha = 0.15f)
    }
    val cardBackground = if (isLightTheme) colorScheme.surface else colorScheme.surface.copy(alpha = 0.95f)
    val dividerColor = if (isLightTheme) {
        colorScheme.outline.copy(alpha = 0.35f)
    } else {
        colorScheme.secondary.copy(alpha = 0.5f)
    }

    when (uiState) {
        MiraclesDetailUiState.Loading -> {
            MiraclesDetailLoadingSkeleton()
        }
        is MiraclesDetailUiState.Error -> {
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
        is MiraclesDetailUiState.Success -> {
            var displayedFontSize by remember(uiState.fontSize) { mutableIntStateOf(uiState.fontSize) }
            val readingScale = displayedFontSize / 20f
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
                MiraclesDetailHeader(
                    prayerName = when (variant) {
                        MiraclesContentVariant.MUCIZEDUALAR -> uiState.prayer.duaIsim
                        MiraclesContentVariant.ESMAUL_HUSNA -> uiState.prayer.duaLatinOkunus.ifBlank { uiState.prayer.duaIsim }
                    },
                    onBackClick = onBackClick,
                    onSettingsClick = onSettingsClick,
                    onRewardsClick = onRewardsClick
                )

                HorizontalDivider(
                    thickness = dimens.stroke,
                    color = dividerColor,
                    modifier = Modifier.padding(horizontal = dimens.space24)
                )

                LazyColumn(
                    modifier = Modifier
                        .testTag("miracles_detail")
                        .weight(1f)
                        .fillMaxWidth()
                        .readerPinchFontSize(
                            fontSize = displayedFontSize,
                            fontSizeRange = 14..40,
                            onPreview = { displayedFontSize = it },
                            onCommitted = onFontSizeChange,
                        ),
                    contentPadding = PaddingValues(horizontal = dimens.space12, vertical = dimens.space12)
                ) {
                    if (
                        variant == MiraclesContentVariant.MUCIZEDUALAR &&
                        uiState.prayer.originalArabicVerses.isNotEmpty()
                    ) {
                        item {
                            MiraclesDetailSectionCard(
                                label = stringResource(R.string.miracles_section_arabic),
                                contentBackground = cardBackground,
                                borderColor = colorScheme.secondary.copy(alpha = 0.3f),
                                copyText = uiState.prayer.originalArabicVerses.joinToString("\n") { it.arabic },
                                copyActionLabel = stringResource(R.string.content_copy_arabic),
                                copyTag = "miracles_copy_arabic",
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(dimens.space12)) {
                                    // Show original-text source before long passages (not only at the bottom).
                                    Text(
                                        text = stringResource(R.string.miracles_quran_tanzil_attribution),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.secondary,
                                        modifier = Modifier.clickable {
                                            uriHandler.openUri("https://tanzil.net/")
                                        },
                                    )
                                    uiState.prayer.originalArabicVerses.forEach { verse ->
                                        Text(
                                            text = verse.reference,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = verse.arabic,
                                            fontSize = (22 * readingScale).coerceAtLeast(12f).sp,
                                            lineHeight = (38 * readingScale).coerceAtLeast(16f).sp,
                                            color = colorScheme.onSurface,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(dimens.space12))
                        }
                    }

                    if (variant == MiraclesContentVariant.MUCIZEDUALAR) {
                        val hadithSupplication = uiState.prayer.originalArabicSupplication
                        val weakHadithSupplication = uiState.prayer.originalArabicWeakHadithSupplication
                        val veryWeakHadithSupplication = uiState.prayer.originalArabicVeryWeakHadithSupplication
                        val traditionalSupplication = uiState.prayer.originalArabicDocumentedTraditionalSupplication
                        val sourcedSupplication = hadithSupplication
                            ?: weakHadithSupplication
                            ?: veryWeakHadithSupplication
                            ?: traditionalSupplication
                            ?: uiState.prayer.originalArabicScholarlySupplication
                        sourcedSupplication?.let { supplication ->
                            item {
                                MiraclesDetailSectionCard(
                                    label = stringResource(R.string.miracles_section_arabic),
                                    contentBackground = cardBackground,
                                    borderColor = colorScheme.secondary.copy(alpha = 0.3f),
                                    copyText = supplication.arabic,
                                    copyActionLabel = stringResource(R.string.content_copy_arabic),
                                    copyTag = "miracles_copy_arabic",
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(dimens.space12)) {
                                        val sourceLabel = when {
                                            hadithSupplication != null -> R.string.miracles_hadith_source
                                            weakHadithSupplication != null -> R.string.miracles_weak_hadith_source
                                            veryWeakHadithSupplication != null -> R.string.miracles_very_weak_hadith_source
                                            traditionalSupplication != null -> R.string.miracles_traditional_source
                                            else -> R.string.miracles_scholarly_source
                                        }
                                        Text(
                                            text = stringResource(
                                                sourceLabel,
                                                supplication.reference,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.secondary,
                                            modifier = Modifier.clickable {
                                                uriHandler.openUri(supplication.sourceUrl)
                                            },
                                        )
                                        Text(
                                            text = supplication.arabic,
                                            fontSize = (22 * readingScale).coerceAtLeast(12f).sp,
                                            lineHeight = (38 * readingScale).coerceAtLeast(16f).sp,
                                            color = colorScheme.onSurface,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.space12))
                            }
                        }
                    }

                    if (variant == MiraclesContentVariant.MUCIZEDUALAR) {
                        item {
                            MiraclesDetailSectionCard(
                                label = stringResource(R.string.miracles_section_description),
                                labelAccent = true,
                                contentBackground = overlayCard,
                                borderColor = colorScheme.secondary.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = uiState.prayer.duaAciklama,
                                    color = colorScheme.onSurface,
                                    fontSize = (14 * readingScale).coerceAtLeast(12f).sp,
                                    lineHeight = (20 * readingScale).coerceAtLeast(16f).sp
                                )
                            }
                            Spacer(modifier = Modifier.height(dimens.space12))
                        }

                        // The Quran Arabic already includes its original basmala where applicable,
                        // but the legacy Latin basmala remains a separate pronunciation aid.
                        item {
                            MiraclesDetailSectionCard(
                                label = stringResource(R.string.miracles_section_besmele),
                                contentBackground = cardBackground,
                                borderColor = colorScheme.secondary.copy(alpha = 0.3f),
                            ) {
                                Text(
                                    text = uiState.prayer.duaBesmele,
                                    fontSize = (18 * readingScale).coerceAtLeast(12f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    lineHeight = (26 * readingScale).coerceAtLeast(16f).sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(dimens.space12))
                        }
                    } else {
                        item {
                            MiraclesDetailSectionCard(
                                label = stringResource(R.string.miracles_section_arabic),
                                contentBackground = cardBackground,
                                borderColor = colorScheme.secondary.copy(alpha = 0.3f),
                            ) {
                                Text(
                                    text = uiState.prayer.duaArapca,
                                    fontSize = (22 * readingScale).coerceAtLeast(12f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    lineHeight = (34 * readingScale).coerceAtLeast(16f).sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(dimens.space12))
                        }

                        item {
                            MiraclesDetailSectionCard(
                                label = stringResource(R.string.miracles_section_latin_pronunciation),
                                contentBackground = cardBackground,
                                borderColor = colorScheme.secondary.copy(alpha = 0.3f),
                            ) {
                                Text(
                                    text = uiState.prayer.duaLatinOkunus.ifBlank { uiState.prayer.duaBesmele },
                                    fontSize = (18 * readingScale).coerceAtLeast(12f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    lineHeight = (26 * readingScale).coerceAtLeast(16f).sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(dimens.space12))
                        }
                    }

                    item {
                        MiraclesDetailSectionCard(
                            label = if (variant == MiraclesContentVariant.MUCIZEDUALAR) {
                                stringResource(R.string.miracles_section_latin_pronunciation)
                            } else {
                                stringResource(R.string.miracles_section_description)
                            },
                            contentBackground = if (variant == MiraclesContentVariant.MUCIZEDUALAR) cardBackground else overlayCard,
                            borderColor = colorScheme.secondary.copy(alpha = if (variant == MiraclesContentVariant.MUCIZEDUALAR) 0.3f else 0.2f),
                            labelAccent = variant == MiraclesContentVariant.ESMAUL_HUSNA,
                            copyText = if (variant == MiraclesContentVariant.MUCIZEDUALAR) uiState.prayer.duaArapca else null,
                            copyActionLabel = stringResource(R.string.content_copy_latin),
                            copyTag = "miracles_copy_latin",
                        ) {
                            if (variant == MiraclesContentVariant.MUCIZEDUALAR) {
                                if (
                                    uiState.prayer.originalArabicVerses.isEmpty() &&
                                    uiState.prayer.originalArabicSupplication == null &&
                                    uiState.prayer.originalArabicScholarlySupplication == null &&
                                    uiState.prayer.originalArabicWeakHadithSupplication == null &&
                                    uiState.prayer.originalArabicVeryWeakHadithSupplication == null &&
                                    uiState.prayer.originalArabicDocumentedTraditionalSupplication == null &&
                                    !uiState.prayer.hasArabicScript
                                ) {
                                    Text(
                                        text = stringResource(R.string.miracles_original_arabic_unverified),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                }
                                Text(
                                    text = uiState.prayer.duaArapca,
                                    fontSize = (20 * readingScale).coerceAtLeast(12f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    textAlign = if (uiState.prayer.hasArabicScript) TextAlign.End else TextAlign.Start,
                                    lineHeight = (32 * readingScale).coerceAtLeast(16f).sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = uiState.prayer.duaAciklama,
                                    color = colorScheme.onSurface,
                                    fontSize = (14 * readingScale).coerceAtLeast(12f).sp,
                                    lineHeight = (20 * readingScale).coerceAtLeast(16f).sp
                                )
                            }
                        }
                    }

                    // Keep the first meaningful explanation visible before a native ad.
                    // The same ad placement remains available below the reading content.
                    if (uiState.shouldShowAds) {
                        item(key = "miracles_native_ad") {
                            content()
                            Spacer(modifier = Modifier.height(dimens.space12))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiraclesDetailSectionCard(
    label: String,
    contentBackground: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    labelAccent: Boolean = false,
    copyText: String? = null,
    copyActionLabel: String = "",
    copyTag: String = "",
    content: @Composable () -> Unit,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    val copiedMessage = stringResource(R.string.miracles_text_copied)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = contentBackground,
                shape = RoundedCornerShape(dimens.radiusMedium)
            )
            .border(
                width = dimens.stroke,
                color = borderColor,
                shape = RoundedCornerShape(dimens.radiusMedium)
            )
            .padding(dimens.space16)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (labelAccent) colorScheme.secondary else colorScheme.onSurface,
                fontSize = if (labelAccent) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (!copyText.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        copyMiraclesText(context, copyText, copiedMessage)
                        view.announceForAccessibility(copiedMessage)
                    },
                    modifier = Modifier.size(48.dp).testTag(copyTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = copyActionLabel,
                        tint = colorScheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(dimens.space12))
        ReaderSelectionContainer { content() }
    }
}

private fun copyMiraclesText(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("prayer_text", text))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun MiraclesDetailLoadingSkeleton() {
    val dimens = LocalDimens.current
    val shimmer = rememberMiraclesDetailSkeletonBrush()
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
                .height(54.dp),
            brush = shimmer
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            brush = shimmer
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            brush = shimmer
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            brush = shimmer
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp),
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
private fun rememberMiraclesDetailSkeletonBrush(): Brush {
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "miracles_detail_skeleton")
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
        label = "miracles_detail_skeleton_translate"
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
fun MiraclesDetailHeader(
    prayerName: String,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {}
) {
    val dimens = LocalDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.space6, bottom = dimens.space4)
            .padding(horizontal = dimens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(dimens.space40)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_navigate_back),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }

        Text(
            text = prayerName,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimens.space4)
        )

        IconButton(
            onClick = onRewardsClick,
            modifier = Modifier.size(dimens.space40)
        ) {
            Icon(
                imageVector = Icons.Filled.CardGiftcard,
                contentDescription = stringResource(R.string.content_open_rewards),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(dimens.iconMd)
            )
        }
    }
}

@Preview(showBackground = true, name = "Miracles Detail Loading")
@Composable
private fun MiraclesDetailLoadingPreview() {
    AppTheme(flavorName = "mucizedualar") {
        MiraclesDetailScreen(
            uiState = MiraclesDetailUiState.Loading,
        )
    }
}

@Preview(showBackground = true, name = "Miracles Detail Success")
@Composable
private fun MiraclesDetailSuccessPreview() {
    AppTheme(flavorName = "mucizedualar") {
        MiraclesDetailScreen(
            uiState = MiraclesDetailUiState.Success(
                prayer = MiraclesPrayer(
                    duaIsim = "Şifa Duası",
                    duaAciklama = "Şifa niyetiyle okunur.",
                    duaBesmele = "Bismillahirrahmanirrahim",
                    duaArapca = "اللَّهُمَّ اشْفِ أَنْتَ الشَّافِي"
                ),
                shouldShowAds = false
            ),
        )
    }
}
