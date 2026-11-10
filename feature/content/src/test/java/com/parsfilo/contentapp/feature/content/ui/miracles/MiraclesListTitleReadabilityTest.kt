package com.parsfilo.contentapp.feature.content.ui.miracles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraclesListTitleReadabilityTest {
    @Test
    fun fullPrayerNameTakesPriorityForLargeSystemText() {
        assertFalse(shouldPrioritizeMiraclesPrayerTitle(MiraclesContentVariant.MUCIZEDUALAR, 1.0f))
        assertFalse(shouldPrioritizeMiraclesPrayerTitle(MiraclesContentVariant.MUCIZEDUALAR, 1.29f))
        assertTrue(shouldPrioritizeMiraclesPrayerTitle(MiraclesContentVariant.MUCIZEDUALAR, 1.3f))
        assertTrue(shouldPrioritizeMiraclesPrayerTitle(MiraclesContentVariant.MUCIZEDUALAR, 2.0f))
    }

    @Test
    fun headerTitleWrapsAtLargeSystemTextWithoutChangingNormalLayout() {
        assertEquals(1, miraclesHeaderTitleMaxLines(1.0f))
        assertEquals(1, miraclesHeaderTitleMaxLines(1.29f))
        assertEquals(2, miraclesHeaderTitleMaxLines(1.3f))
        assertEquals(2, miraclesHeaderTitleMaxLines(2.0f))
    }

    @Test
    fun esmaLayoutIsUnaffected() {
        assertFalse(shouldPrioritizeMiraclesPrayerTitle(MiraclesContentVariant.ESMAUL_HUSNA, 2.0f))
    }
}
