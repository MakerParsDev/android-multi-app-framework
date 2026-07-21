package com.parsfilo.contentapp.feature.ads.ui

import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.ads.nativead.NativeAd
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import java.util.Locale

private const val MAX_STARS = 5
private val NATIVE_MEDIUM_MIN_HEIGHT = 180.dp // Reduced to avoid taking too much screen real estate
private val NATIVE_MEDIA_HEIGHT = 110.dp // Tightened media height

@Composable
fun NativeAdItem(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
    isSmallFormat: Boolean = false,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme

    // Flavor temasından gelen renkler
    // Make ad background distinctly different from ordinary surface elements
    // Using a more solid and distinct color combination to avoid "Ads looking like content" policy violation.
    val cardBg = colorScheme.surfaceVariant
    val accentColor = colorScheme.secondary
    val textPrimary = colorScheme.onSurfaceVariant
    val textSecondary = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val borderColor = colorScheme.outlineVariant // Make border more distinct
    val ctaBg = colorScheme.primary
    val ctaText = colorScheme.onPrimary

    DisposableEffect(nativeAd) {
        // NativeAd lifecycle is manager-owned; UI must not destroy here to avoid double-destroy.
        onDispose { }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space12, vertical = dimens.space4),
        contentAlignment = Alignment.Center
    ) {
        NativeAdViewCompose(
            nativeAd = nativeAd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isSmallFormat) 0.dp else NATIVE_MEDIUM_MIN_HEIGHT)
                    .clip(RoundedCornerShape(dimens.radiusLarge))
                    .background(cardBg, RoundedCornerShape(dimens.radiusLarge))
                    .border(2.dp, borderColor, RoundedCornerShape(dimens.radiusLarge)), // Thicker border for clarity
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f)) // Make header distinct
                        .padding(horizontal = dimens.space12, vertical = dimens.space8),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NativeAdAttribution(
                        text = "Reklam / Ad", // Explicit attribution
                        containerColor = colorScheme.secondary, // Very prominent color
                        contentColor = colorScheme.onSecondary,
                    )
                    NativeAdChoicesView(modifier = Modifier.size(dimens.iconMd))
                }

                Spacer(modifier = Modifier.height(dimens.space2))

                // ── Icon + Headline + Star Rating ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    nativeAd.icon?.let { icon ->
                        NativeAdIconView(
                            modifier = Modifier
                                .size(dimens.iconXl)
                                .clip(CircleShape),
                        ) {
                            icon.drawable?.toBitmap()?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Ad Icon",
                                    modifier = Modifier.size(dimens.iconXl),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(dimens.space10))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        nativeAd.headline?.let { headline ->
                            NativeAdHeadlineView {
                                Text(
                                    text = headline,
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        nativeAd.starRating?.let { rating ->
                            NativeAdStarRatingView {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val fullStars = rating.toInt()
                                    val emptyStars = (MAX_STARS - fullStars).coerceAtLeast(0)
                                    Text(
                                        text = "★".repeat(fullStars) + "☆".repeat(emptyStars),
                                        color = accentColor,
                                        fontSize = 14.sp,
                                    )
                                    Spacer(modifier = Modifier.width(dimens.space4))
                                    Text(
                                        text = String.format(Locale.US, "%.1f", rating),
                                        color = textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimens.space6))

                if (!isSmallFormat) {
                    NativeAdMediaView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NATIVE_MEDIA_HEIGHT),
                        scaleType = ImageView.ScaleType.CENTER_CROP,
                    )

                    Spacer(modifier = Modifier.height(dimens.space6))
                }

                nativeAd.body?.let { body ->
                    NativeAdBodyView(modifier = Modifier.padding(horizontal = dimens.space12).padding(bottom = dimens.space6)) {
                        Text(
                            text = body,
                            color = textSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = borderColor.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = dimens.space12).padding(bottom = dimens.space6),
                )

                nativeAd.advertiser?.let { advertiser ->
                    NativeAdAdvertiserView(modifier = Modifier.padding(horizontal = dimens.space12).padding(bottom = dimens.space6)) {
                        Text(
                            text = advertiser,
                            color = textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ── Alt: Store / Price / CTA ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.space12).padding(bottom = dimens.space12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        nativeAd.store?.let { store ->
                            NativeAdStoreView {
                                Text(text = store, color = textSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(dimens.space8))
                        }
                        nativeAd.price?.let { price ->
                            NativeAdPriceView {
                                Text(
                                    text = price,
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    nativeAd.callToAction?.let { cta ->
                        NativeAdCallToActionView {
                            NativeAdButton(
                                text = cta,
                                containerColor = ctaBg,
                                contentColor = ctaText,
                            )
                        }
                    }
                }
            }
        }
    }
}
