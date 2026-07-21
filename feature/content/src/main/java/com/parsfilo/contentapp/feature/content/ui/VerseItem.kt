package com.parsfilo.contentapp.feature.content.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.content.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerseItem(
    verse: Verse,
    displayMode: DisplayMode,
    fontSize: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty().lowercase()
    val cardColor = if (isLightTheme) colorScheme.surface else colorScheme.surface.copy(alpha = 0.96f)
    val borderColor = colorScheme.secondary.copy(alpha = if (isLightTheme) 0.45f else 0.35f)
    val badgeBg = if (isLightTheme) colorScheme.primary.copy(alpha = 0.82f) else colorScheme.primaryContainer.copy(alpha = 0.85f)
    val badgeText = if (isLightTheme) colorScheme.onPrimary else colorScheme.onPrimaryContainer

    fun pickTranslation(): String {
        // Prefer app language translation if present, otherwise fall back to TR.
        return when {
            language.startsWith("de") && verse.german.isNotBlank() -> verse.german
            language.startsWith("en") && verse.english.isNotBlank() -> verse.english
            else -> verse.turkish
        }
    }

    val verseText = when (displayMode) {
        DisplayMode.ARABIC -> verse.arabic
        DisplayMode.LATIN -> verse.latin
        DisplayMode.TURKISH -> pickTranslation()
    }

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
            )
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    copyToClipboard(context, verseText)
                }
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

                Text(
                    text = verseText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.6).sp,
                        fontWeight = FontWeight.Normal
                    ),
                    textAlign = textAlignment,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, verseText: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("verse_text", verseText))
    Toast.makeText(context, context.getString(R.string.content_verse_copied), Toast.LENGTH_SHORT).show()
}
