package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdAgeGateStatusTest {
    @Test
    fun `unknown and invalid values map to unknown`() {
        assertThat(AdAgeGateStatus.fromStorage(null)).isEqualTo(AdAgeGateStatus.UNKNOWN)
        assertThat(AdAgeGateStatus.fromStorage("INVALID")).isEqualTo(AdAgeGateStatus.UNKNOWN)
    }

    @Test
    fun `known values map correctly`() {
        assertThat(AdAgeGateStatus.fromStorage("UNDER_16")).isEqualTo(AdAgeGateStatus.AGE_13_TO_15)
        assertThat(AdAgeGateStatus.fromStorage("UNDER_13")).isEqualTo(AdAgeGateStatus.UNDER_13)
        assertThat(AdAgeGateStatus.fromStorage("AGE_13_TO_15")).isEqualTo(AdAgeGateStatus.AGE_13_TO_15)
        assertThat(AdAgeGateStatus.fromStorage("AGE_16_OR_OVER")).isEqualTo(AdAgeGateStatus.AGE_16_OR_OVER)
    }
}
