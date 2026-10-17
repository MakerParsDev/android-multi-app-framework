package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdPreloadRequestBudgetTest {
    @Test
    fun permitsAtMostTwelveOptionalRequestsPerRollingMinute() {
        val budget = AdPreloadRequestBudget()
        repeat(6) {
            assertThat(budget.tryReserve(count = 2, nowMs = 1_000L)).isTrue()
        }
        assertThat(budget.tryReserve(nowMs = 1_050L)).isFalse()
        assertThat(budget.tryReserve(nowMs = 61_000L)).isTrue()
    }

    @Test
    fun deniesBatchIfEntireBatchCannotFit() {
        val budget = AdPreloadRequestBudget()
        assertThat(budget.tryReserve(count = 11, nowMs = 1_000L)).isTrue()
        assertThat(budget.tryReserve(count = 2, nowMs = 1_001L)).isFalse()
        assertThat(budget.tryReserve(count = 1, nowMs = 1_001L)).isTrue()
    }

    @Test
    fun handlesClockRewindWithoutPermanentLockout() {
        val budget = AdPreloadRequestBudget()
        assertThat(budget.tryReserve(count = 12, nowMs = 90_000L)).isTrue()
        assertThat(budget.tryReserve(nowMs = 1_000L)).isTrue()
    }
}
