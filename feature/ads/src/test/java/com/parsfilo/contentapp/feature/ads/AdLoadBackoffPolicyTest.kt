package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class AdLoadBackoffPolicyTest {

    @Test
    fun `no-fill increases backoff and caps`() {
        var state = AdLoadBackoffState()
        val now = 1_000L
        repeat(10) { index ->
            state = AdLoadBackoffPolicy.onLoadFailure(
                nowMillis = now,
                current = state,
                errorCode = 3,
                random = Random(index),
            )
            assertThat(state.nextLoadAllowedAtMillis).isGreaterThan(now)
        }
        assertThat(state.failureStreak).isEqualTo(10)
        assertThat(state.nextLoadAllowedAtMillis - now).isAtMost(360_000L)
    }

    @Test
    fun `success resets backoff`() {
        val failed = AdLoadBackoffPolicy.onLoadFailure(
            nowMillis = 1_000L,
            current = AdLoadBackoffState(),
            errorCode = 3,
            random = Random(1),
        )
        val reset = AdLoadBackoffPolicy.onLoadSuccess()
        assertThat(failed.failureStreak).isGreaterThan(0)
        assertThat(reset.failureStreak).isEqualTo(0)
        assertThat(reset.nextLoadAllowedAtMillis).isEqualTo(0L)
    }

    @Test
    fun `canLoad false before next window`() {
        val state = AdLoadBackoffState(failureStreak = 1, nextLoadAllowedAtMillis = 5_000L)
        assertThat(AdLoadBackoffPolicy.canLoad(4_999L, state)).isFalse()
        assertThat(AdLoadBackoffPolicy.canLoad(5_000L, state)).isTrue()
    }
}
