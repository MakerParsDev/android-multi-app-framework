package com.parsfilo.contentapp.feature.prayertimes.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerLocationSelection
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose Yes/No buttons; all locations are disposable in-memory fixtures. */
@RunWith(AndroidJUnit4::class)
class PrayerLocationConfirmationUiDeviceTest {
    @get:Rule val rule = createComposeRule()

    private val acceptedA = PrayerLocationSelection(1, 2, 7, "Accepted A")
    private val proposedB = PrayerLocationSelection(1, 2, 8, "Proposed B")
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(onConfirm: () -> Unit, onReject: () -> Unit) {
        assertEquals("Tests must run only in the standalone feature test host", TEST_PACKAGE, context.packageName)
        rule.setContent {
            AppTheme(flavorName = "namazvakitleri") {
                PrayerTimesScreen(
                    appName = "Namaz Vakitleri – isolated UI test",
                    variant = PrayerAppVariant.NAMAZ_VAKITLERI,
                    uiState = state,
                    onAlarmEnabledChanged = {},
                    onAlarmOffsetChanged = {},
                    onAlarmPrayerKeysChanged = {},
                    onAlarmSoundUriChanged = {},
                    onTestAlarmSound = {},
                    onAutoModeClick = {},
                    onManualModeClick = {},
                    onRefresh = {},
                    onResolveByDeviceLocation = {},
                    onConfirmResolvedLocation = onConfirm,
                    onRejectResolvedLocation = onReject,
                    onOpenLocationPicker = {},
                    onOpenRewards = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }

    private var state by mutableStateOf(
        PrayerTimesUiState(
            mode = PrayerTimesMode.MANUAL,
            selection = acceptedA,
            pendingResolvedLocationName = proposedB.displayName,
        ),
    )

    @Test fun noDismissesProposalWithoutChangingAcceptedDistrict() {
        var rejected = 0
        var confirmed = 0
        render(
            onConfirm = { confirmed++ },
            onReject = {
                rejected++
                state = state.copy(pendingResolvedLocationName = null)
            },
        )
        val title = context.getString(R.string.prayertimes_resolved_confirmation_title, proposedB.displayName)
        rule.onNodeWithText(title).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.prayertimes_resolved_confirmation_no)).performClick()
        rule.waitForIdle()
        assertEquals(1, rejected)
        assertEquals(0, confirmed)
        assertEquals(acceptedA, state.selection)
        rule.onNodeWithText(title).assertDoesNotExist()
    }

    @Test fun yesInvokesConfirmationAndUpdatesOnlySyntheticState() {
        var confirmed = 0
        var rejected = 0
        render(
            onConfirm = {
                confirmed++
                state = state.copy(selection = proposedB, mode = PrayerTimesMode.AUTO, pendingResolvedLocationName = null)
            },
            onReject = { rejected++ },
        )
        val title = context.getString(R.string.prayertimes_resolved_confirmation_title, proposedB.displayName)
        rule.onNodeWithText(title).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.prayertimes_resolved_confirmation_yes)).performClick()
        rule.waitForIdle()
        assertEquals(1, confirmed)
        assertEquals(0, rejected)
        assertEquals(proposedB, state.selection)
        rule.onNodeWithText(title).assertDoesNotExist()
    }

    private companion object {
        const val TEST_PACKAGE = "com.parsfilo.contentapp.feature.prayertimes.test"
    }
}
