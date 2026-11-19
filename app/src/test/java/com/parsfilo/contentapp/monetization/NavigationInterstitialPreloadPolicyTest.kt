package com.parsfilo.contentapp.monetization

import com.parsfilo.contentapp.product.ContentFamily
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationInterstitialPreloadPolicyTest {
    @Test
    fun prayerLibraryNeverPreloadsAnInterstitialItCannotDisplay() {
        assertFalse(shouldPreloadNavigationInterstitial(ContentFamily.PRAYER_LIBRARY))
    }

    @Test
    fun navigationInterstitialFamiliesRetainExistingWarmup() {
        ContentFamily.entries.filter { it != ContentFamily.PRAYER_LIBRARY }.forEach { family ->
            assertTrue(
                "Unexpected interstitial change for $family",
                shouldPreloadNavigationInterstitial(family),
            )
        }
    }
}
