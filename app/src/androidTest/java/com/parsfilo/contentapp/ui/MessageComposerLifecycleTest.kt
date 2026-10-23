package com.parsfilo.contentapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.messages.R
import com.parsfilo.contentapp.feature.messages.ui.MessageSendState
import com.parsfilo.contentapp.feature.messages.ui.MessagesScreen
import com.parsfilo.contentapp.feature.messages.ui.MessagesUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageComposerLifecycleTest {
    @get:Rule val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val title get() = context.getString(R.string.messages_send)
    private val subject get() = context.getString(R.string.messages_subject)
    private val body get() = context.getString(R.string.messages_body)
    private val cancel get() = context.getString(R.string.cancel)
    private val send get() = context.getString(R.string.send)

    private fun openComposer(
        state: MessageSendState = MessageSendState.Idle,
        sendMessage: (String, String, String) -> Unit = { _, _, _ -> },
    ) {
        rule.setContent {
            AppTheme(flavorName = "namazvakitleri") {
                MessagesScreen(
                    uiState = MessagesUiState.Success(emptyList()),
                    sendState = state,
                    onSendMessage = sendMessage,
                )
            }
        }
        rule.onNodeWithText(title).performClick()
    }

    @Test fun cancelClosesComposerWithoutSending() {
        var sends = 0
        openComposer(sendMessage = { _, _, _ -> sends++ })
        rule.onNodeWithText(subject).assertExists()
        rule.onNodeWithText(cancel).performClick()
        rule.onNodeWithText(subject).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, sends) }
    }

    @Test fun systemBackClosesComposerWithoutSending() {
        var sends = 0
        openComposer(sendMessage = { _, _, _ -> sends++ })
        rule.onNodeWithText(subject).assertExists()
        Espresso.pressBack()
        rule.onNodeWithText(subject).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, sends) }
    }

    @Test fun errorRetainsDraftAndAllowsRetry() {
        var state by mutableStateOf<MessageSendState>(MessageSendState.Idle)
        var sends = 0
        rule.setContent {
            AppTheme(flavorName = "namazvakitleri") {
                MessagesScreen(
                    uiState = MessagesUiState.Success(emptyList()),
                    sendState = state,
                    onSendMessage = { _, _, _ ->
                        sends++
                        state = MessageSendState.Error
                    },
                    onResetSendState = { state = MessageSendState.Idle },
                )
            }
        }
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithText(subject).performTextInput("Private mock subject")
        rule.onNodeWithText(body).performTextInput("Private mock message")
        rule.onNodeWithText(send).performClick()
        rule.onNodeWithText(context.getString(R.string.messages_send_error)).assertExists()
        rule.onNodeWithText("Private mock subject").assertExists()
        rule.onNodeWithText("Private mock message").assertExists()
        rule.onNodeWithText(send).performClick()
        rule.runOnIdle { assertEquals(2, sends) }
    }

    @Test fun sendingDisablesDuplicateSubmitAndCancel() {
        var state by mutableStateOf<MessageSendState>(MessageSendState.Idle)
        var sends = 0
        rule.setContent {
            AppTheme(flavorName = "namazvakitleri") {
                MessagesScreen(
                    uiState = MessagesUiState.Success(emptyList()),
                    sendState = state,
                    onSendMessage = { _, _, _ ->
                        sends++
                        state = MessageSendState.Sending
                    },
                    onResetSendState = { state = MessageSendState.Idle },
                )
            }
        }
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithText(subject).performTextInput("Mock subject")
        rule.onNodeWithText(body).performTextInput("Mock message")
        rule.onNodeWithText(send).performClick()
        rule.onNodeWithText(send).assertIsNotEnabled()
        rule.onNodeWithText(cancel).assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1, sends) }
    }

    @Test
    fun cancelIsBlockedBeforeViewModelSendingStateRecomposes() {
        var sends = 0
        // Deliberately keep sendState Idle to reproduce the StateFlow/Compose gap.
        openComposer(sendMessage = { _, _, _ -> sends++ })
        rule.onNodeWithText(subject).performTextInput("Mock subject")
        rule.onNodeWithText(body).performTextInput("Mock message")
        rule.onNodeWithText(send).performClick()
        rule.onNodeWithText(send).assertIsNotEnabled()
        rule.onNodeWithText(cancel).assertIsNotEnabled()
        rule.onNodeWithText(subject).assertExists()
        rule.runOnIdle { assertEquals(1, sends) }
    }

    @Test fun acknowledgedSendClosesComposer() {
        var state by mutableStateOf<MessageSendState>(MessageSendState.Idle)
        var sends = 0
        rule.setContent {
            AppTheme(flavorName = "namazvakitleri") {
                MessagesScreen(
                    uiState = MessagesUiState.Success(emptyList()),
                    sendState = state,
                    onSendMessage = { _, _, _ ->
                        sends++
                        state = MessageSendState.Sent
                    },
                    onResetSendState = { state = MessageSendState.Idle },
                )
            }
        }
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithText(subject).performTextInput("Mock subject")
        rule.onNodeWithText(body).performTextInput("Mock message")
        rule.onNodeWithText(send).performClick()
        rule.onNodeWithText(subject).assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, sends) }
    }
}
