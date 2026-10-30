package com.parsfilo.contentapp.feature.content.ui.miracles

import com.parsfilo.contentapp.core.model.MiraclesOriginalVerse
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraclesPrayerTextIntegrityTest {
    private fun prayer(text: String) = MiraclesPrayer(
        duaIsim = "Example",
        duaAciklama = "",
        duaBesmele = "",
        duaArapca = text,
    )

    @Test
    fun latinTransliterationMustNotBeLabeledArabic() {
        assertFalse(prayer("Allahümme inni euzü bike").hasArabicScript)
    }

    @Test
    fun actualArabicScriptIsRecognized() {
        assertTrue(prayer("اللَّهُمَّ اشْفِ").hasArabicScript)
    }

    @Test
    fun blankLegacyDataDoesNotClaimArabicText() {
        assertFalse(prayer("").hasArabicScript)
    }

    @Test
    fun sourceVerifiedArabicStaysSeparateFromLegacyLatinPhonetics() {
        val record = MiraclesPrayer(
            duaIsim = "Example",
            duaAciklama = "",
            duaBesmele = "",
            duaArapca = "Allâhü lâ ilâhe illâ hüve",
            originalArabicVerses = listOf(
                MiraclesOriginalVerse(
                    reference = "2:255",
                    arabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ",
                ),
            ),
        )
        assertFalse(record.hasArabicScript)
        assertTrue(record.originalArabicVerses.single().arabic.isNotBlank())
        assertFalse(record.originalArabicIncludesBasmala)
    }

    @Test
    fun originalArabicBasmalaFlagDoesNotAlterStoredLatinText() {
        val latin = "İnnâ fetahnâ leke fethan mubînâ"
        val record = MiraclesPrayer(
            duaIsim = "Example",
            duaAciklama = "",
            duaBesmele = "Bismillahirrahmanirrahim",
            duaArapca = latin,
            originalArabicVerses = listOf(
                MiraclesOriginalVerse(
                    reference = "48:1",
                    arabic = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                ),
            ),
            originalArabicIncludesBasmala = true,
        )
        assertTrue(record.originalArabicIncludesBasmala)
        assertFalse(record.hasArabicScript)
        assertTrue(record.duaArapca == latin)
    }
}
