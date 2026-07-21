package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdShowStampRecorderTest {
    @Test
    fun `records only once`() {
        assertThat(shouldRecordImpressionStamp(false)).isTrue()
        assertThat(shouldRecordImpressionStamp(true)).isFalse()
    }
}
