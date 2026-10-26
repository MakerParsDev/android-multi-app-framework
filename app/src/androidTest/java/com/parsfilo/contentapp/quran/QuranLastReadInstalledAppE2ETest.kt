package com.parsfilo.contentapp.quran

import android.os.Build
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.MainActivity
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.core.database.model.quran.QuranAyahEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranBookmarkEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranLastReadEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranSuraEntity
import com.parsfilo.contentapp.feature.quran.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full MainActivity -> actual Compose navigation -> Room persistence in a disposable emulator.
 * Never run against an existing user installation or a physical phone with user data.
 */
@RunWith(AndroidJUnit4::class)
class QuranLastReadInstalledAppE2ETest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun recentAndBookmarkRoutesPersistThroughBackAndColdStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.parsfilo.kuran_kerim", context.packageName)
        assertTrue("Disable network, push and ads for synthetic QA", BuildConfig.CI_SMOKE)
        assertTrue(
            "Never seed a physical phone: emulator-only synthetic E2E",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
                Build.PRODUCT.contains("sdk", ignoreCase = true),
        )

        // The external ADB runner must reject installed packages before installation.
        // Verify this is a newly installed emulator app, not a reused data-bearing app.
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        val installAgeMs = System.currentTimeMillis() - installedAt
        assertTrue(
            "Refuse stale installation: original app may contain other user data",
            installAgeMs in 0..(15 * 60 * 1_000L),
        )
        val databasePath = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        assertTrue(
            "Refuse pre-existing Room database, even if Quran tables happen to be empty",
            !databasePath.exists() &&
                !java.io.File(databasePath.path + "-wal").exists() &&
                !java.io.File(databasePath.path + "-shm").exists(),
        )

        val database = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .build()
        runBlocking {
            val dao = database.quranDao()
            // Fail closed: no pre-existing reading progress or suras may be overwritten.
            assertEquals("Refuse nonempty Quran database", 0, dao.getSuraCount())
            assertNull("Refuse pre-existing reading progress", dao.observeLastRead().first())
            dao.insertSuras(
                (1..114).map { number ->
                    QuranSuraEntity(
                        number = number,
                        nameArabic = "QA Sura $number",
                        nameLatin = "QA Sura $number",
                        nameTurkish = "QA Sura $number",
                        nameEnglish = "QA Sura $number",
                        revelationType = "MEDINAN",
                        ayahCount = if (number == 2) 10 else 1,
                    )
                },
            )
            dao.insertAyahs(
                (1..10).map { number ->
                    QuranAyahEntity(
                        suraNumber = 2,
                        ayahNumber = number,
                        arabic = "QA Ayah $number",
                        latin = "QA Ayah $number",
                        turkish = "QA Ayah $number",
                        english = "QA Ayah $number",
                        german = "QA Ayah $number",
                    )
                },
            )
            dao.saveLastRead(QuranLastReadEntity(suraNumber = 2, ayahNumber = 5))
            dao.insertBookmark(QuranBookmarkEntity(suraNumber = 2, ayahNumber = 8))
        }
        database.close()
        Log.i("QURAN_482_E2E", "fixture_seeded")

        val recentLabel = context.getString(R.string.quran_last_read_format, 2, 5)
        val bookmarkLabel = context.getString(R.string.quran_last_read_format, 2, 8)

        Log.i("QURAN_482_E2E", "before_main_activity_launch")
        ActivityScenario.launch(MainActivity::class.java).use {
            Log.i("QURAN_482_E2E", "activity_launched")
            waitFor(recentLabel)
            Log.i("QURAN_482_E2E", "recent_card_found")
            rule.onNodeWithText(recentLabel).performClick()
            Log.i("QURAN_482_E2E", "recent_card_clicked")
            waitFor("QA Ayah 5")
            Log.i("QURAN_482_E2E", "target_5_found")
            rule.onNodeWithText("QA Ayah 5").assertIsDisplayed()
            Espresso.pressBack()
            Log.i("QURAN_482_E2E", "back_from_5")
            waitFor(recentLabel)
            assertEquals(5, readMarker(context))
            Log.i("QURAN_482_E2E", "marker_5_retained")

            rule.onNodeWithContentDescription(context.getString(R.string.quran_more_options)).performClick()
            rule.onNodeWithText(context.getString(R.string.quran_open_bookmarks)).performClick()
            waitFor(bookmarkLabel)
            Log.i("QURAN_482_E2E", "bookmark_card_found")
            rule.onNodeWithText(bookmarkLabel).performClick()
            waitFor("QA Ayah 8")
            Log.i("QURAN_482_E2E", "target_8_found")
            rule.onNodeWithText("QA Ayah 8").assertIsDisplayed()
            Espresso.pressBack()
            assertEquals(8, readMarkerEventually(context, 8))
            Log.i("QURAN_482_E2E", "marker_8_saved")
        }
        Log.i("QURAN_482_E2E", "first_activity_closed")

        // An entirely new activity should retain the same persisted verse.
        ActivityScenario.launch(MainActivity::class.java).use {
            Log.i("QURAN_482_E2E", "cold_restart_launched")
            val resumeLabel = context.getString(R.string.quran_last_read_format, 2, 8)
            waitFor(resumeLabel)
            rule.onNodeWithText(resumeLabel).assertIsDisplayed()
            assertEquals(8, readMarker(context))
            Log.i("QURAN_482_E2E", "cold_restart_retained_8")
        }
    }

    private fun readMarker(context: android.content.Context): Int {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
        return try {
            runBlocking { db.quranDao().observeLastRead().first()!!.ayahNumber }
        } finally {
            db.close()
        }
    }

    private fun readMarkerEventually(context: android.content.Context, value: Int): Int {
        rule.waitUntil(timeoutMillis = 10_000) { readMarker(context) == value }
        return readMarker(context)
    }

    private fun waitFor(text: String) {
        rule.waitUntil(timeoutMillis = 45_000) {
            runCatching { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }
}
