package com.parsfilo.contentapp.feature.content.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.content.R

@Composable
fun VerseItem(
    verse: Verse,
    displayMode: DisplayMode,
    fontSize: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty().lowercase()
    val cardColor = if (isLightTheme) colorScheme.surface else colorScheme.surface.copy(alpha = 0.96f)
    val borderColor = colorScheme.secondary.copy(alpha = if (isLightTheme) 0.45f else 0.35f)
    val badgeBg = if (isLightTheme) colorScheme.primary.copy(alpha = 0.82f) else colorScheme.primaryContainer.copy(alpha = 0.85f)
    val badgeText = if (isLightTheme) colorScheme.onPrimary else colorScheme.onPrimaryContainer

    val translation = selectVerseTranslation(verse, language)

    val verseText = when (displayMode) {
        DisplayMode.ARABIC -> verse.arabic
        DisplayMode.LATIN -> verse.latin
        DisplayMode.TURKISH -> translation.text
    }

    val copySuccessMessage = stringResource(R.string.content_verse_copied)
    val copyActionLabel = stringResource(
        when (displayMode) {
            DisplayMode.ARABIC -> R.string.content_copy_arabic
            DisplayMode.LATIN -> R.string.content_copy_latin
            DisplayMode.TURKISH -> if (translation.isTurkishFallback) {
                R.string.content_copy_turkish_translation
            } else {
                R.string.content_copy_translation
            }
        }
    )

    val textAlignment = when (displayMode) {
        DisplayMode.ARABIC -> TextAlign.End
        else -> TextAlign.Start
    }

    val layoutDirection = if (displayMode == DisplayMode.ARABIC) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space10, vertical = dimens.space4)
            .border(
                width = dimens.stroke,
                color = borderColor,
                shape = RoundedCornerShape(dimens.radiusMedium)
            ),
        shape = RoundedCornerShape(dimens.radiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.elevationLow)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.space14, vertical = dimens.space14),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.iconMd)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = verse.id.toString(),
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(dimens.space10))

                Column(modifier = Modifier.weight(1f)) {
                    if (displayMode == DisplayMode.TURKISH && translation.isTurkishFallback) {
                        Text(
                            text = stringResource(R.string.content_translation_turkish_fallback),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = verseText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.6).sp,
                            fontWeight = FontWeight.Normal
                        ),
                        textAlign = textAlignment,
                        color = colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        copyToClipboard(context, verseText, copySuccessMessage)
                        view.announceForAccessibility(copySuccessMessage)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("verse_copy_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = copyActionLabel,
                        tint = colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, verseText: String, successMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("verse_text", verseText))
    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
}
