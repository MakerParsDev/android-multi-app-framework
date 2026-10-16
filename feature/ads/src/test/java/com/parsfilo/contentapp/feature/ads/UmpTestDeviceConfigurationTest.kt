package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UmpTestDeviceConfigurationTest {
    @Test
    fun acceptsOnly32HexHashedIdsInDebugBuild() {
        val hash = "ABCDEF0123456789ABCDEF0123456789"
        assertThat(registeredUmpTestDeviceHash(debugBuild = true, configuredHash = hash))
            .isEqualTo(hash)
        assertThat(registeredUmpTestDeviceHash(debugBuild = false, configuredHash = hash))
            .isNull()
    }

    @Test
    fun rejectsMissingOrMalformedDeviceIdentifiers() {
        for (hash in listOf("", "abc", " ", "ABCDEF0123456789ABCDEF012345678!", "ABCDEF0123456789ABCDEF01234567890")) {
            assertThat(registeredUmpTestDeviceHash(debugBuild = true, configuredHash = hash))
                .isNull()
        }
    }
}
