package com.parsfilo.contentapp.feature.quran.ui.component

import android.os.Build
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.feature.quran.ui.suradetail.AyahDisplayState
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A native provider opening must not create a second Compose Copy menu. */
@RunWith(AndroidJUnit4::class)
class QuranReaderNativeToolbarDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun nativeToolbarSuppressesFallbackOnAndroid9() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return

        val arabic = "بسم الله الرحمن الرحيم"
        val ayah = QuranAyah(
            suraNumber = 2,
            ayahNumber = 1,
            arabic = arabic,
            latin = "Synthetic Latin",
            turkish = "Synthetic Turkish",
            english = "Synthetic English",
            german = "Synthetic German",
        )
        var nativeRequests = 0
        val simulatedNativeToolbar = object : TextContextMenuProvider {
            override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                nativeRequests++
                awaitCancellation()
            }
        }
        rule.setContent {
            CompositionLocalProvider(
                LocalTextContextMenuToolbarProvider provides simulatedNativeToolbar,
            ) {
                AppTheme(flavorName = "kuran_kerim") {
                    AyahItem(
                        state = AyahDisplayState(
                            ayah = ayah,
                            isBookmarked = false,
                            isAudioDownloaded = false,
                            isAudioDownloading = false,
                            audioDownloadProgress = 0f,
                            isCurrentlyPlaying = false,
                        ),
                        displayMode = QuranDisplayMode.ARABIC,
                        translatedText = "",
                        fontSize = 26,
                        onPlayPauseClick = {},
                        onBookmarkClick = {},
                    )
                }
            }
        }
        val text = rule.onNodeWithText(arabic, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        text.performTouchInput { longClick(layouts.single().getBoundingBox(1).center) }
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue("The native toolbar provider must be reached", nativeRequests > 0)
        }
        rule.onNodeWithTag("reader_copy_action").assertDoesNotExist()
    }
}
