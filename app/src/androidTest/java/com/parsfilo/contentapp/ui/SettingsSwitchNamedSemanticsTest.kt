package com.parsfilo.contentapp.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.parsfilo.contentapp.feature.content.R as ContentR
import com.parsfilo.contentapp.feature.quran.R as QuranR
import com.parsfilo.contentapp.feature.settings.R as SettingsR

/**
 * A setting's checked value, control role and localized name must belong to the same
 * accessibility node. Synthetic fresh-install debug app only, never a user's installed app.
 */
@RunWith(AndroidJUnit4::class)
class SettingsSwitchNamedSemanticsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun notificationAndThemeSwitchesExposeNameStateAndSingleAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Quran opens from its sura-list header, not the standalone Content header.
        val openSettings =
            if (BuildConfig.FLAVOR_NAME == "kuran_kerim") {
                context.getString(QuranR.string.quran_open_settings)
            } else {
                context.getString(ContentR.string.content_open_settings)
            }
        val notifications = context.getString(SettingsR.string.settings_notifications)
        val darkMode = context.getString(SettingsR.string.settings_dark_mode)

        rule.waitUntil(30_000) {
            rule.onAllNodesWithContentDescription(openSettings)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(openSettings).performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription(darkMode)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The original implementation split the checkable parent from the named child.
        // Both properties must now appear on exactly one target per setting.
        listOf(notifications, darkMode).forEach { label ->
            rule.onAllNodesWithContentDescription(label).assertCountEquals(1)
            rule.onNodeWithContentDescription(label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
                .assertHasClickAction()
        }
    }
}
