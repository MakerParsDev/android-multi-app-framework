package com.parsfilo.contentapp.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.otherapps.model.OtherApp
import com.parsfilo.contentapp.feature.otherapps.ui.AppItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class OtherAppsInstallAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val apps = listOf(
        OtherApp(appName = "Ayetel Kürsi", packageName = "com.parsfilo.ayetelkursi", appIconUrl = ""),
        OtherApp(appName = "Yasin Suresi", packageName = "com.parsfilo.yasinsuresi", appIconUrl = ""),
    )

    @Test
    fun turkishInstallButtonsNameTheirOwnAppsAndRemainClickable() {
        verifyLabels("tr-TR", listOf("Ayetel Kürsi uygulamasını yükle", "Yasin Suresi uygulamasını yükle"))
    }

    @Test
    fun englishInstallButtonsNameTheirOwnAppsAndRemainClickable() {
        verifyLabels("en-US", listOf("Install Ayetel Kürsi", "Install Yasin Suresi"))
    }

    @Test
    fun germanInstallButtonsNameTheirOwnAppsAndRemainClickable() {
        verifyLabels("de-DE", listOf("Ayetel Kürsi installieren", "Yasin Suresi installieren"))
    }

    private fun verifyLabels(locale: String, accessibleLabels: List<String>) {
        val clickedApps = mutableListOf<String>()
        rule.setContent {
            val baseContext = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList(Locale.forLanguageTag(locale)))
            }
            CompositionLocalProvider(
                LocalContext provides baseContext.createConfigurationContext(configuration),
                LocalConfiguration provides configuration,
            ) {
                AppTheme(flavorName = "ayetelkursi") {
                    Column {
                        apps.forEach { app ->
                            AppItem(app = app, onClick = { clickedApps += app.appName })
                        }
                    }
                }
            }
        }
        accessibleLabels.forEach { description ->
            rule.onNodeWithContentDescription(description)
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
        }
        rule.runOnIdle {
            assertEquals(apps.map { it.appName }, clickedApps)
        }
    }
}
