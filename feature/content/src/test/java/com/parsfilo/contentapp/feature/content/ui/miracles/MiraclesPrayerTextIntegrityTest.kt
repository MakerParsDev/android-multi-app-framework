package com.parsfilo.contentapp.feature.content.ui.miracles

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
}
