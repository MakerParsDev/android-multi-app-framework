package com.parsfilo.contentapp.core.firebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeObservabilityTest {
    private companion object {
        const val TEST_PACKAGE_NAME = "com.parsfilo.test"
    }

    @Test
    fun configureAttachesStableReleaseIdentity() {
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        val reporter = FirebaseRuntimeObservability(crashlytics)

        reporter.configure(
            RuntimeReleaseContext(
                packageName = TEST_PACKAGE_NAME,
                flavor = "test",
                versionCode = 42,
                versionName = "2.3.4",
                buildType = "release",
                releaseRevision = "abc123",
                releaseTrack = "production",
            ),
        )

        verify { crashlytics.setCustomKey("package_name", TEST_PACKAGE_NAME) }
        verify { crashlytics.setCustomKey("flavor", "test") }
        verify { crashlytics.setCustomKey("version_code", 42) }
        verify { crashlytics.setCustomKey("version_name", "2.3.4") }
        verify { crashlytics.setCustomKey("build_type", "release") }
        verify { crashlytics.setCustomKey("release_revision", "abc123") }
        verify { crashlytics.setCustomKey("release_track", "production") }
    }

    @Test
    fun eachSignalMapsToDistinctExceptionClass() {
        val classNames =
            RuntimeSignal.entries.map { signal ->
                runtimeSignalException(signal, "CODE", "message", null)::class.java.simpleName
            }

        assertEquals(RuntimeSignal.entries.size, classNames.toSet().size)
    }

    @Test
    fun attributesExcludeSecretLikeKeysAndNormalizeValues() {
        val normalized =
            normalizeRuntimeAttributes(
                mapOf(
                    " package name " to "  com.parsfilo.test  ",
                    "purchase_token" to "must-not-appear",
                    "Error Code" to " HTTP  500 ",
                ),
            )

        assertEquals(TEST_PACKAGE_NAME, normalized["package_name"])
        assertEquals("HTTP 500", normalized["error_code"])
        assertFalse(normalized.containsKey("purchase_token"))
    }

    @Test
    fun attributeLimitCountsAcceptedEntriesBeforeDuplicateKeysCollapse() {
        val attributes = linkedMapOf<String, String>()
        attributes["duplicate key"] = "first"
        attributes["duplicate-key"] = "second"
        repeat(10) { index -> attributes["key_$index"] = "value_$index" }
        attributes["after_limit"] = "must-not-appear"

        val normalized = normalizeRuntimeAttributes(attributes)

        assertEquals("second", normalized["duplicate_key"])
        assertFalse(normalized.containsKey("after_limit"))
        assertEquals(11, normalized.size)
    }

    @Test
    fun logOutputIsDeterministicAndContainsSignalIdentity() {
        val log =
            buildRuntimeSignalLog(
                signal = RuntimeSignal.REMOTE_CONFIG_FETCH,
                code = "FETCH_FAILED",
                message = "network",
                attributes = mapOf("z" to "last", "a" to "first"),
            )

        assertTrue(log.startsWith("runtime_signal=remote_config_fetch_failure"))
        assertTrue(log.indexOf("a=first") < log.indexOf("z=last"))
    }
}
