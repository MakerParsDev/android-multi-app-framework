package com.parsfilo.contentapp.core.common.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * Base class for ViewModel unit tests.
 *
 * Provides:
 * - [testDispatcher] — a [StandardTestDispatcher] that replaces [Dispatchers.Main]
 * - Automatic setup/teardown of [Dispatchers.Main]
 *
 * Usage:
 * ```kotlin
 * class MyViewModelTest : BaseViewModelTest() {
 *     private lateinit var viewModel: MyViewModel
 *
 *     @Before
 *     override fun setUp() {
 *         super.setUp()
 *         viewModel = MyViewModel(...)
 *     }
 *
 *     @Test
 *     fun `initial state is loading`() = runTest {
 *         // ...
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseViewModelTest {
    protected val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    open fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    open fun tearDown() {
        Dispatchers.resetMain()
    }
}
