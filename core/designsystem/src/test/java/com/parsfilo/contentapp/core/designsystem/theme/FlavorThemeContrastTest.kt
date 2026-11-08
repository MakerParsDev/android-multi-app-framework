package com.parsfilo.contentapp.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-level design token contract; this is NOT a rendered screenshot test.
 * Real-device visual, large-font, dark-mode and TalkBack checks remain separate.
 */
class FlavorThemeContrastTest {
    private val flavorKeys = listOf(
        "namazvakitleri", "yasinsuresi", "fetihsuresi", "amenerrasulu",
        "ayetelkursi", "esmaulhusna", "kenzularsduasi", "insirahsuresi",
        "ismiazamduasi", "vakiasuresi", "namazsurelerivedualarsesli",
        "kuran_kerim", "kible", "mucizedualar", "zikirmatik",
        "bereketduasi", "nazarayeti",
    )

    private fun contrast(foreground: Color, background: Color): Float {
        val brighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (brighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun everyShippedFlavorHasItsOwnPalette() {
        assertEquals(17, flavorKeys.size)
        val palettes = flavorKeys.map(FlavorColors::forFlavor)
        assertEquals(flavorKeys.size, palettes.map { it.primary }.distinct().size)
    }

    @Test
    fun lightPrimaryOnPrimaryTextMeetsAaContrastAcrossAllFlavors() {
        flavorKeys.forEach { flavor ->
            val tokens = FlavorColors.forFlavor(flavor)
            assertTrue(flavor, contrast(tokens.onPrimary, tokens.primary) >= 4.5f)
        }
    }

    @Test
    fun darkPrimaryAccentMeetsAaTextContrastAcrossAllFlavors() {
        flavorKeys.forEach { flavor ->
            val accent = accessibleDarkAccent(FlavorColors.forFlavor(flavor).secondary)
            assertTrue(flavor, contrast(Color(0xFF08080F), accent) >= 4.5f)
        }
    }

    @Test
    fun alreadyAccessibleDarkAccentIsPreserved() {
        val accent = Color(0xFFC07818)
        assertEquals(accent, accessibleDarkAccent(accent))
    }
}
