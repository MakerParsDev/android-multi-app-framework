package com.parsfilo.contentapp.monetization

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsConfigValidatorTest {
    private val validator = AdsConfigValidator()

    @Test
    fun `throws when any ad unit is missing`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                validator.validateResolvedIds(
                    ids = validIds.copy(native = ""),
                    isDebugBuild = false,
                    useTestAds = false,
                )
            }

        assertTrue(error.message?.contains("Missing native ad unit id") == true)
    }

    @Test
    fun `throws when debug build resolves to production ids`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                validator.validateResolvedIds(
                    ids = validIds,
                    isDebugBuild = true,
                    useTestAds = false,
                )
            }

        assertTrue(error.message?.contains("Debug build must not resolve to production") == true)
    }

    @Test
    fun `throws when test ads mode resolves to non test ids`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                validator.validateResolvedIds(
                    ids = validIds,
                    isDebugBuild = false,
                    useTestAds = true,
                )
            }

        assertTrue(error.message?.contains("Test-ads build resolved to non-test") == true)
    }

    @Test
    fun `throws when release build resolves to Google test ids`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                validator.validateResolvedIds(
                    ids = testIds,
                    isDebugBuild = false,
                    useTestAds = false,
                )
            }

        assertTrue(
            error.message?.contains(
                "Release/configured production build resolved to Google test ids",
            ) ==
                true,
        )
    }

    @Test
    fun `passes when test ads mode resolves to Google test ids`() {
        validator.validateResolvedIds(
            ids = testIds,
            isDebugBuild = true,
            useTestAds = true,
        )
    }

    @Test
    fun `passes when release mode resolves to production ids`() {
        validator.validateResolvedIds(
            ids = validIds,
            isDebugBuild = false,
            useTestAds = false,
        )
    }

    private val validIds =
        AppAdUnitIds.Ids(
            banner = "ca-app-pub-3312485084079132/banner",
            interstitial = "ca-app-pub-3312485084079132/interstitial",
            native = "ca-app-pub-3312485084079132/native",
            rewarded = "ca-app-pub-3312485084079132/rewarded",
            rewardedInterstitial = "ca-app-pub-3312485084079132/rewarded-interstitial",
            appOpen = "ca-app-pub-3312485084079132/app-open",
        )

    private val testIds =
        AppAdUnitIds.Ids(
            banner = "${AppAdUnitIds.GOOGLE_TEST_PUBLISHER_PREFIX}/6300978111",
            interstitial = "${AppAdUnitIds.GOOGLE_TEST_PUBLISHER_PREFIX}/1033173712",
            native = "${AppAdUnitIds.GOOGLE_TEST_PUBLISHER_PREFIX}/2247696110",
            rewarded = "${AppAdUnitIds.GOOGLE_TEST_PUBLISHER_PREFIX}/5224354917",
            rewardedInterstitial = "${AppAdUnitIds.GOOGLE_TEST_PUBLISHER_PREFIX}/5354046379",
            appOpen = "${AppAdUnitIds.GOOGLE_TEST_PUBLISHER_PREFIX}/9257395921",
        )
}
