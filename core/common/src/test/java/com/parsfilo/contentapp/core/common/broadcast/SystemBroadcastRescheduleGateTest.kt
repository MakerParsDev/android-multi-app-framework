package com.parsfilo.contentapp.core.common.broadcast

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemBroadcastRescheduleGateTest {
    @Test
    fun `same key coalesces while different key proceeds`() = runTest {
        val gate = SystemBroadcastRescheduleGate()
        val release = CompletableDeferred<Unit>()
        val first = async { gate.runIfIdle("zikir") { release.await() } }
        runCurrent()

        val duplicate = gate.runIfIdle("zikir") { error("must not run") }
        val independent = gate.runIfIdle("prayer") { }
        release.complete(Unit)

        assertThat(duplicate).isFalse()
        assertThat(independent).isTrue()
        assertThat(first.await()).isTrue()
    }
}
