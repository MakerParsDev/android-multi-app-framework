package com.parsfilo.contentapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesDetailHeader
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesListHeader
import com.parsfilo.contentapp.feature.content.ui.prayer.PrayerDetailHeader
import com.parsfilo.contentapp.feature.content.ui.prayer.PrayerListHeader
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import com.parsfilo.contentapp.feature.content.R as ContentR

/** Covers content headers not rendered by the standard ContentScreen reader. */
@RunWith(AndroidJUnit4::class)
class ContentSecondaryHeaderA11yTest {
    @get:Rule val rule = createComposeRule()

    private fun verifyLabels(
        showSettings: Boolean,
        language: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        assumeTrue(BuildConfig.FLAVOR_NAME == "esmaulhusna")
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        // Locale changes are confined to this Compose tree, never the emulator or user settings.
        val localizedContext = baseContext.createConfigurationContext(configuration)
        rule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                AppTheme(flavorName = "esmaulhusna") { content() }
            }
        }
        if (showSettings) {
            rule.onNodeWithContentDescription(
                localizedContext.getString(ContentR.string.content_open_settings),
            ).assertHasClickAction()
        }
        rule.onNodeWithContentDescription(
            localizedContext.getString(ContentR.string.content_open_rewards),
        ).assertHasClickAction()
    }

    @Test fun miracleListHasLocalizedHeaderControls() = verifyLabels(showSettings = true, language = "de-DE") {
        MiraclesListHeader(title = "Esma")
    }

    @Test fun miracleDetailHasLocalizedRewardsControl() = verifyLabels(showSettings = false, language = "de-DE") {
        MiraclesDetailHeader(prayerName = "Esma")
    }

    @Test fun prayerListHasLocalizedHeaderControls() = verifyLabels(showSettings = true, language = "en-US") {
        PrayerListHeader()
    }

    @Test fun prayerDetailHasLocalizedRewardsControl() = verifyLabels(showSettings = false, language = "en-US") {
        PrayerDetailHeader(prayerName = "Dua")
    }
}
