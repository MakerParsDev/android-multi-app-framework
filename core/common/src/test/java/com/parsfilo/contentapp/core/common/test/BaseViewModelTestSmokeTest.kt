package com.parsfilo.contentapp.core.common.test

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTestSmokeTest : BaseViewModelTest() {
    @Test
    fun `main dispatcher is replaced in tests`() =
        runTest(testDispatcher) {
            var ran = false
            CoroutineScope(Dispatchers.Main).launch {
                ran = true
            }

            assertThat(ran).isFalse()
            advanceUntilIdle()
            assertThat(ran).isTrue()
        }
}
