package com.parsfilo.contentapp.feature.ads.ui

import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens

// Google'ın resmi Jetpack Compose Demo'sundan uyarlanan
// Native Ad Compose sarmalayıcıları.
//
// @see <a href="https://github.com/googleads/googleads-mobile-android-examples/tree/main/kotlin/advanced/JetpackComposeDemo">JetpackComposeDemo</a>

/**
 * NativeAdView referansını alt composable'lara ileten CompositionLocal.
 */
internal val LocalNativeAdView = staticCompositionLocalOf<NativeAdView?> { null }

/**
 * NativeAdView Compose sarmalayıcısı.
 * Tüm native reklam asset'leri bu composable içinde tanımlanmalıdır.
 */
@Composable
fun NativeAdViewCompose(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    val currentNativeAd by rememberUpdatedState(nativeAd)
    AndroidView(
        factory = { context ->
            val composeView = ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            NativeAdView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                clipToPadding = true
                clipChildren = true
                addView(composeView)
            }
        },
        modifier = modifier,
        update = { view ->
            val composeView = view.getChildAt(0) as? ComposeView
            composeView?.setContent {
                CompositionLocalProvider(LocalNativeAdView provides view) {
                    currentContent()
                }
            }
            // setNativeAd() triggers requestLayout() internally; posting to next frame
            // avoids "performMeasureAndLayout called during measure layout" crash.
            view.post { view.setNativeAd(currentNativeAd) }
        },
    )
}

/** Headline asset sarmalayıcısı */
@Composable
fun NativeAdHeadlineView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdHeadlineView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.headlineView = view
            view.setContent(content)
        },
    )
}

/** Body asset sarmalayıcısı */
@Composable
fun NativeAdBodyView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdBodyView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.bodyView = view
            view.setContent(content)
        },
    )
}

/** Icon asset sarmalayıcısı */
@Composable
fun NativeAdIconView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdIconView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.iconView = view
            view.setContent(content)
        },
    )
}

/** Call-to-Action asset sarmalayıcısı */
@Composable
fun NativeAdCallToActionView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdCallToActionView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.callToActionView = view
            view.setContent(content)
        },
    )
}

/** Star rating asset sarmalayıcısı */
@Composable
fun NativeAdStarRatingView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdStarRatingView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.starRatingView = view
            view.setContent(content)
        },
    )
}

/** Store asset sarmalayıcısı */
@Composable
fun NativeAdStoreView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdStoreView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.storeView = view
            view.setContent(content)
        },
    )
}

/** Price asset sarmalayıcısı */
@Composable
fun NativeAdPriceView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdPriceView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.priceView = view
            view.setContent(content)
        },
    )
}

/** Advertiser asset sarmalayıcısı */
@Composable
fun NativeAdAdvertiserView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdAdvertiserView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            nativeAdView.advertiserView = view
            view.setContent(content)
        },
    )
}

/** MediaView sarmalayıcısı — video ve resim içeriği gösterir */
@Composable
fun NativeAdMediaView(
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER,
) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdMediaView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context ->
            MediaView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        },
        modifier = modifier,
        update = { view ->
            nativeAdView.mediaView = view
            view.setImageScaleType(scaleType)
        },
    )
}

/** AdChoices sarmalayıcısı — SDK tarafından otomatik doldurulur */
@Composable
fun NativeAdChoicesView(modifier: Modifier = Modifier) {
    val nativeAdView = LocalNativeAdView.current
        ?: throw IllegalStateException("NativeAdChoicesView must be inside NativeAdViewCompose")
    AndroidView(
        factory = { context ->
            AdChoicesView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                minimumWidth = 15
                minimumHeight = 15
            }
        },
        modifier = modifier,
        update = { view -> nativeAdView.adChoicesView = view },
    )
}

/**
 * Reklam etiketi — Google politikalarına uygun "Reklam" badge'i.
 */
@Composable
fun NativeAdAttribution(
    modifier: Modifier = Modifier,
    text: String = "Reklam",
    shape: Shape = ButtonDefaults.shape,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    padding: PaddingValues? = null,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val resolvedPadding = padding ?: PaddingValues(horizontal = dimens.space6, vertical = dimens.space2)
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        colorScheme.primary
    } else {
        containerColor
    }
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        colorScheme.secondary
    } else {
        contentColor
    }

    Box(modifier = modifier.background(resolvedContainerColor, shape).padding(resolvedPadding)) {
        Text(color = resolvedContentColor, text = text)
    }
}

/**
 * Native reklam butonu — Compose'un standart Button'u tıklama handler'ını
 * geçersiz kıldığı için Box tabanlı buton kullanılır.
 *
 * @see <a href="https://github.com/googleads/googleads-mobile-android-examples">Google Ads Compose Demo</a>
 */
@Composable
fun NativeAdButton(
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = ButtonDefaults.shape,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    padding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    val colorScheme = MaterialTheme.colorScheme
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        colorScheme.secondary
    } else {
        containerColor
    }
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        colorScheme.primary
    } else {
        contentColor
    }
    Box(modifier = modifier.background(resolvedContainerColor, shape).padding(padding)) {
        Text(color = resolvedContentColor, text = text)
    }
}
