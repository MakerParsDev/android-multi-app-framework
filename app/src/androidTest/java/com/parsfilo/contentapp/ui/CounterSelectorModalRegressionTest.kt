package com.parsfilo.contentapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.CounterUiState
import com.parsfilo.contentapp.feature.counter.ui.CounterScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-only regression: no user preferences or AlarmManager APIs are invoked. */
@RunWith(AndroidJUnit4::class)
class CounterSelectorModalRegressionTest {
    @get:Rule val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun label(id: Int) = context.getString(id)

    private fun showCounter(selector: Boolean = true) {
        var uiState by mutableStateOf(
            CounterUiState(
                showZikirSelector = selector,
                isLoading = false,
                isPremium = true,
            ),
        )
        rule.setContent {
            AppTheme(flavorName = "zikirmatik") {
                CounterScreen(
                    uiState = uiState,
                    onSettingsClick = {},
                    onRewardsClick = {},
                    onCounterTapped = {},
                    onResetCurrentCount = {},
                    onShareSession = {},
                    onZikirSelectorToggle = {
                        uiState = uiState.copy(showZikirSelector = !uiState.showZikirSelector)
                    },
                    onZikirSelected = {},
                    onAddCustomZikir = { _, _, _, _ -> },
                    onDeleteZikir = {},
                    onDismissSessionComplete = {},
                    onShareConfirmed = {},
                    onShareDismissed = {},
                    onShareTextCopied = {},
                    onReminderSettingsToggle = {
                        uiState = uiState.copy(
                            showReminderSettings = !uiState.showReminderSettings,
                            showSessionHistory = false,
                        )
                    },
                    onReminderSaved = {},
                    onSessionHistoryToggle = {
                        uiState = uiState.copy(
                            showSessionHistory = !uiState.showSessionHistory,
                            showReminderSettings = false,
                        )
                    },
                    onSessionHistoryDismiss = {
                        uiState = uiState.copy(showSessionHistory = false)
                    },
                    onUnlockHistoryWithAd = {},
                    onToggleHaptic = {},
                    onToggleSound = {},
                    onTargetChanged = {},
                    onFirstSessionReminderAction = {},
                    onFirstSessionReminderConsumed = {},
                )
            }
        }
    }

    private fun openMenu() {
        rule.onNodeWithContentDescription(label(R.string.counter_menu_more)).performClick()
    }

    @Test
    fun selectorReminderOpensOnFirstClickAndBackRestoresSelector() {
        showCounter()
        openMenu()
        rule.onNodeWithText(label(R.string.counter_reminder_title)).performClick()
        rule.onNodeWithText(label(R.string.counter_reminder_daily)).assertIsDisplayed()

        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText(label(R.string.counter_add_zikir)).assertExists()
        openMenu()
        rule.onNodeWithText(label(R.string.counter_continue)).performClick()
        rule.onNodeWithTag("counter_root").assertExists()
        rule.onNodeWithText(label(R.string.counter_reminder_daily)).assertDoesNotExist()
    }

    @Test
    fun selectorHistoryOpensImmediatelyAndNoDeferredSheetAfterContinue() {
        showCounter()
        openMenu()
        rule.onNodeWithText(label(R.string.counter_history_title)).performClick()
        rule.onNodeWithText(label(R.string.counter_history_empty)).assertIsDisplayed()

        Espresso.pressBack()
        rule.waitForIdle()
        openMenu()
        rule.onNodeWithText(label(R.string.counter_continue)).performClick()
        rule.onNodeWithTag("counter_root").assertExists()
        rule.onNodeWithText(label(R.string.counter_history_empty)).assertDoesNotExist()
    }

    @Test
    fun counterModeOffersReminderAndHistoryWithoutEnteringSelector() {
        showCounter(selector = false)
        openMenu()
        rule.onNodeWithText(label(R.string.counter_reminder_title)).performClick()
        rule.onNodeWithText(label(R.string.counter_reminder_daily)).assertExists()
        Espresso.pressBack()
        rule.waitForIdle()
        openMenu()
        rule.onNodeWithText(label(R.string.counter_history_title)).performClick()
        rule.onNodeWithText(label(R.string.counter_history_empty)).assertExists()
    }
}
