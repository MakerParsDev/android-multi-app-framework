package com.parsfilo.contentapp.feature.messages.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.auth.AuthManager
import com.parsfilo.contentapp.feature.messages.data.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    private val repository = mockk<MessageRepository>()
    private val authManager = mockk<AuthManager>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.getMessages() } returns flowOf(emptyList())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `double tap during pending write sends only once`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.sendMessage("subject", "body", "GENERAL") } coAnswers { gate.await() }
        val viewModel = MessagesViewModel(repository, authManager)

        viewModel.sendMessage(" subject ", " body ", "GENERAL")
        viewModel.sendMessage(" subject ", " body ", "GENERAL")
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Sending)
        runCurrent()
        coVerify(exactly = 1) { repository.sendMessage("subject", "body", "GENERAL") }
        viewModel.resetSendState()
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Sending)

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Sent)
        coVerify(exactly = 1) { repository.sendMessage("subject", "body", "GENERAL") }
    }

    @Test fun `failed send reports error and explicit retry succeeds`() = runTest(dispatcher) {
        coEvery { repository.sendMessage(any(), any(), any()) } throws IllegalStateException("simulated offline")
        val viewModel = MessagesViewModel(repository, authManager)
        viewModel.sendMessage("one", "two", "GENERAL")
        advanceUntilIdle()
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Error)

        coEvery { repository.sendMessage(any(), any(), any()) } returns Unit
        viewModel.sendMessage("one", "two", "GENERAL")
        advanceUntilIdle()
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Sent)
        coVerify(exactly = 2) { repository.sendMessage("one", "two", "GENERAL") }
    }

    @Test fun `blank subject or message never sends`() = runTest(dispatcher) {
        val viewModel = MessagesViewModel(repository, authManager)
        viewModel.sendMessage("", "body", "GENERAL")
        viewModel.sendMessage("subject", "  ", "GENERAL")
        advanceUntilIdle()
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Idle)
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any()) }
    }

    @Test fun `reset clears error and sent but not pending write`() = runTest(dispatcher) {
        coEvery { repository.sendMessage(any(), any(), any()) } returns Unit
        val viewModel = MessagesViewModel(repository, authManager)
        viewModel.sendMessage("subject", "body", "GENERAL")
        advanceUntilIdle()
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Sent)
        viewModel.resetSendState()
        assertThat(viewModel.sendState.value).isEqualTo(MessageSendState.Idle)
    }
}
