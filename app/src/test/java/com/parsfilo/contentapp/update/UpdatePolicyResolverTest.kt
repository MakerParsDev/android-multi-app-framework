package com.parsfilo.contentapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyResolverTest {
    private fun config(
        enabled: Boolean = true,
        globalEmergencyDisabled: Boolean = false,
        appEmergencyDisabled: Boolean = false,
        min: Long = 1,
        latest: Long = 1,
        mode: String = "none",
    ): RemoteUpdateConfig =
        RemoteUpdateConfig(
            updatesEnabled = enabled,
            globalEmergencyDisabled = globalEmergencyDisabled,
            appEmergencyDisabled = appEmergencyDisabled,
            minSupportedVersionCode = min,
            latestVersionCode = latest,
            updateMode = mode,
            title = "Title",
            message = "Message",
            updateButton = "Update",
            laterButton = "Later",
        )

    @Test
    fun `unmatched app fallback disables updates`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 4, cfg = config(enabled = false, min = 5, latest = 9))
        assertTrue(policy is UpdatePolicy.None)
    }

    @Test
    fun `global emergency switch disables updates before hard minimum`() {
        val policy =
            resolveUpdatePolicy(
                currentVersionCode = 4,
                cfg = config(globalEmergencyDisabled = true, min = 5, latest = 9),
            )
        assertTrue(policy is UpdatePolicy.None)
    }

    @Test
    fun `app emergency switch disables only matched app policy`() {
        val policy =
            resolveUpdatePolicy(
                currentVersionCode = 4,
                cfg = config(appEmergencyDisabled = true, min = 5, latest = 9),
            )
        assertTrue(policy is UpdatePolicy.None)
    }

    @Test
    fun `current below min supported resolves hard`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 4, cfg = config(min = 5, latest = 9))
        assertTrue(policy is UpdatePolicy.Hard)
    }

    @Test
    fun `none mode produces no prompt below latest`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 5, cfg = config(min = 5, latest = 6))
        assertTrue(policy is UpdatePolicy.None)
    }

    @Test
    fun `current at or above latest resolves none even in hard mode`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 10, cfg = config(min = 5, latest = 10, mode = "hard"))
        assertTrue(policy is UpdatePolicy.None)
    }

    @Test
    fun `hard mode hard-blocks only versions below latest`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 9, cfg = config(min = 5, latest = 10, mode = "hard"))
        assertTrue(policy is UpdatePolicy.Hard)
    }

    @Test
    fun `soft mode resolves soft below latest`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 9, cfg = config(min = 5, latest = 10, mode = "soft"))
        assertTrue(policy is UpdatePolicy.Soft)
    }

    @Test
    fun `min supported hard remains highest priority even when mode soft`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 4, cfg = config(min = 5, latest = 99, mode = "soft"))
        assertTrue(policy is UpdatePolicy.Hard)
    }

    @Test
    fun `invalid range fails open without update prompt`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 4, cfg = config(min = 10, latest = 5, mode = "hard"))
        assertTrue(policy is UpdatePolicy.None)
    }

    @Test
    fun `invalid mode fails closed to no prompt`() {
        val policy = resolveUpdatePolicy(currentVersionCode = 9, cfg = config(min = 5, latest = 10, mode = "???"))
        assertTrue(policy is UpdatePolicy.None)
    }

    @Test
    fun `mode normalization trims and ignores case`() {
        assertEquals(UpdateMode.HARD, normalizeUpdateMode("  HaRd  "))
        assertEquals(UpdateMode.SOFT, normalizeUpdateMode("SOFT"))
        assertEquals(UpdateMode.NONE, normalizeUpdateMode(null))
    }

    @Test
    fun `valid remote version range is preserved`() {
        val range = sanitizeRemoteVersionRange(42, rawMinSupportedVersionCode = 20, rawLatestVersionCode = 41)
        assertEquals(20L, range.minSupportedVersionCode)
        assertEquals(41L, range.latestVersionCode)
        assertFalse(range.usedFallback)
    }

    @Test
    fun `missing remote range falls back to installed version`() {
        val range = sanitizeRemoteVersionRange(42, null, null)
        assertEquals(42L, range.minSupportedVersionCode)
        assertEquals(42L, range.latestVersionCode)
        assertTrue(range.usedFallback)
    }

    @Test
    fun `reversed remote range falls back to installed version`() {
        val range = sanitizeRemoteVersionRange(42, rawMinSupportedVersionCode = 60, rawLatestVersionCode = 50)
        assertEquals(42L, range.minSupportedVersionCode)
        assertEquals(42L, range.latestVersionCode)
        assertTrue(range.usedFallback)
    }

    @Test
    fun `implausibly distant remote version falls back to installed version`() {
        val range = sanitizeRemoteVersionRange(42, rawMinSupportedVersionCode = 1, rawLatestVersionCode = 20_043)
        assertEquals(42L, range.minSupportedVersionCode)
        assertEquals(42L, range.latestVersionCode)
        assertTrue(range.usedFallback)
    }

    @Test
    fun `debug snapshot summary includes safety decision`() {
        val cfg = config(min = 5, latest = 9, mode = "soft")
        val snapshot =
            UpdateDebugSnapshot(
                currentVersionCode = 7,
                config = cfg,
                resolvedPolicy = resolveUpdatePolicy(7, cfg),
            )

        assertEquals(
            "current=7, enabled=true, emergency=false, min=5, latest=9, mode=soft, policy=Soft",
            snapshot.toSummaryText(),
        )
    }
}
