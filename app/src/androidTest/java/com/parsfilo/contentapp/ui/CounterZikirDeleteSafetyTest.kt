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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.CounterUiState
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import com.parsfilo.contentapp.feature.counter.ui.CounterScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic selector state only; does not read/write device preferences or alarm services. */
@RunWith(AndroidJUnit4::class)
class CounterZikirDeleteSafetyTest {
    @get:Rule val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun label(id: Int) = ctx.getString(id)

    private val active = zikir("test_a")
    private val unrelated = zikir("test_b")
    private var confirmedDeletes = 0

    private fun showCounter() {
        var state by mutableStateOf(
            CounterUiState(
                isLoading = false,
                isPremium = true,
                showZikirSelector = true,
                zikirList = listOf(active, unrelated),
                selectedZikir = active,
                currentCount = 17,
                sessionStartTime = 123L,
                isSessionActive = true,
            ),
        )
        rule.setContent {
            AppTheme(flavorName = "zikirmatik") {
                CounterScreen(
                    uiState = state,
                    onSettingsClick = {},
                    onRewardsClick = {},
                    onCounterTapped = {},
                    onResetCurrentCount = {},
                    onShareSession = {},
                    onZikirSelectorToggle = {},
                    onZikirSelected = {},
                    onAddCustomZikir = { _, _, _, _ -> },
                    onDeleteZikir = { item ->
                        confirmedDeletes++
                        state = state.copy(zikirList = state.zikirList.filterNot { it.key == item.key })
                    },
                    onDismissSessionComplete = {},
                    onShareConfirmed = {},
                    onShareDismissed = {},
                    onShareTextCopied = {},
                    onReminderSettingsToggle = {},
                    onReminderSaved = {},
                    onSessionHistoryToggle = {},
                    onSessionHistoryDismiss = {},
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

    private fun deletionFor(item: ZikirItem) =
        rule.onNodeWithContentDescription(ctx.getString(R.string.counter_delete_named, item.latinText))

    @Test fun unrelatedDeletionRequiresExplicitConfirmationAndSupportsCancel() {
        showCounter()
        deletionFor(unrelated).performClick()
        rule.onNodeWithText(label(R.string.counter_delete_confirm_message)).assertIsDisplayed()
        assertEquals(0, confirmedDeletes)
        rule.onNodeWithText(label(R.string.counter_cancel)).performClick()
        assertEquals(0, confirmedDeletes)
        rule.onNodeWithText(label(R.string.counter_delete_confirm_message)).assertDoesNotExist()
        deletionFor(unrelated).performClick()
        rule.onNodeWithTag("counter_delete_confirm_button").performClick()
        rule.waitForIdle()
        assertEquals(1, confirmedDeletes)
        deletionFor(unrelated).assertDoesNotExist()
        deletionFor(active).assertIsDisplayed()
    }

    @Test fun selectedActiveDeletionWarnsAboutLostUnfinishedCount() {
        showCounter()
        deletionFor(active).performClick()
        rule.onNodeWithText(ctx.getString(R.string.counter_delete_confirm_active, 17))
            .assertIsDisplayed()
        assertEquals(0, confirmedDeletes)
        rule.onNodeWithText(label(R.string.counter_cancel)).performClick()
        assertEquals(0, confirmedDeletes)
    }

    private fun zikir(key: String) = ZikirItem(
        key = key,
        arabicText = key,
        latinText = key,
        turkishMeaning = key,
        defaultTarget = 33,
        virtue = "",
        virtueSource = "",
    )
}
