package com.parsfilo.contentapp.feature.quran.ui.suradetail

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.quran.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focus-only synthetic layout reproducing the parent app's global bottom nav.
 * Does not open a Play-installed app or read real Qur'an/user data.
 */
@RunWith(AndroidJUnit4::class)
class QuranReaderKeyboardFocusTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun headerBookmarksTabMovesToReadingModesBeforeGlobalNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.parsfilo.contentapp.feature.quran.test", context.packageName)

        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                Scaffold(
                    bottomBar = {
                        Row {
                            repeat(5) { i ->
                                Button(onClick = {}) { Text("Global " + (i + 1)) }
                            }
                        }
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        QuranSuraDetailScreen(
                            state = QuranSuraDetailUiState(isLoading = false),
                            onBack = {},
                            onRetry = {},
                            onDisplayModeSelected = {},
                            onFontSizeChange = {},
                            onReciterSelected = {},
                            onPlayPauseAyah = { _, _ -> },
                            onDownloadAyah = {},
                            onToggleBookmark = {},
                            onAyahVisible = {},
                        )
                    }
                }
            }
        }

        val bookmarks = rule.onNodeWithContentDescription(
            context.getString(R.string.quran_open_bookmarks),
        )
        val firstMode = rule.onNodeWithText(context.getString(R.string.quran_mode_arabic))
        // Actual Android keyboard delivery, including focus placement on the test Activity.
        // Do not assume the test host starts its Tab sequence at the reader header.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var attempts = 0
        rule.waitForIdle()
        while (attempts < 24 && runCatching { bookmarks.assertIsFocused() }.isFailure) {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
            rule.waitForIdle()
            attempts++
        }
        bookmarks.assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        rule.waitForIdle()
        firstMode.assertIsFocused()
    }
}
