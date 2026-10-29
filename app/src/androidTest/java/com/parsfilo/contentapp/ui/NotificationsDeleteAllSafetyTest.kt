package com.parsfilo.contentapp.ui

import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.core.database.model.NotificationEntity
import com.parsfilo.contentapp.core.database.model.asExternalModel
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.notifications.R
import com.parsfilo.contentapp.feature.notifications.ui.NotificationsScreen
import com.parsfilo.contentapp.feature.notifications.ui.NotificationsUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated UI and in-memory Room acceptance only. Never launch against a user profile
 * or call the real notifications route, system notification manager, or production DB.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsDeleteAllSafetyTest {
    @get:Rule val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private var deleteCalls = 0
    private var markReadCalls = 0
    private var state by mutableStateOf<NotificationsUiState>(NotificationsUiState.Loading)

    private val deleteLabel get() = context.getString(R.string.notifications_action_delete_all)
    private val title get() = context.getString(R.string.notifications_delete_all_confirm_title)
    private val cancelLabel get() = context.getString(R.string.notifications_delete_all_cancel)

    @Before fun seedDisposableDatabase() {
        // Even in a synthetic test, keep destructive exercises off physical user devices.
        assumeTrue(
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("sdk", ignoreCase = true) ||
                Build.PRODUCT.contains("sdk", ignoreCase = true)
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            database.notificationDao().insertNotification(notification("test-one", 100L, false))
            database.notificationDao().insertNotification(notification("test-two", 200L, true))
        }
        deleteCalls = 0
        markReadCalls = 0
        state = NotificationsUiState.Success(currentItems())
    }

    @After fun closeDisposableDatabase() {
        if (::database.isInitialized) database.close()
    }

    private fun notification(key: String, time: Long, read: Boolean) =
        NotificationEntity(
            notificationId = key,
            title = "Disposable $key",
            body = "Synthetic test record",
            imageUrl = null,
            type = null,
            isRead = read,
            timestamp = time,
            dataPayloadJson = null,
        )

    private fun currentItems() = runBlocking {
        database.notificationDao().getNotifications().first().map { it.asExternalModel() }
    }

    private fun assertDatabaseIntact() {
        val items = currentItems()
        assertEquals(2, items.size)
        assertEquals(1, items.count { !it.isRead })
        assertEquals(0, deleteCalls)
        assertEquals(0, markReadCalls)
    }

    private fun showNotifications() {
        rule.setContent {
            AppTheme(flavorName = "namazsurelerivedualarsesli") {
                NotificationsScreen(
                    uiState = state,
                    onNotificationOpen = { _, _ -> },
                    onNotificationMarkUnread = {},
                    onNotificationDelete = { _, _ -> },
                    onMarkAllRead = { markReadCalls++ },
                    onDeleteAll = {
                        deleteCalls++
                        runBlocking { database.notificationDao().deleteAllNotifications() }
                        state = NotificationsUiState.Success(emptyList())
                    },
                )
            }
        }
    }

    private fun openConfirmation() {
        rule.onNodeWithContentDescription(deleteLabel).performClick()
        rule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test fun cancelNeverDeletesAndLeavesUnreadCount() {
        showNotifications()
        openConfirmation()
        assertDatabaseIntact()
        rule.onNodeWithText(cancelLabel).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertDatabaseIntact()
        rule.onNodeWithText("Disposable test-one").assertIsDisplayed()
    }

    @Test fun systemBackDismissesWithoutDeleting() {
        showNotifications()
        openConfirmation()
        Espresso.pressBack()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertDatabaseIntact()
    }

    @Test fun outsideDialogTapDismissesWithoutDeleting() {
        showNotifications()
        openConfirmation()
        // Send a synchronous real touchscreen event outside the dialog window.
        // The emulator-only @Before guard keeps this away from physical devices.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val uptime = SystemClock.uptimeMillis()
        val outsideX = 20f
        val outsideY = context.resources.displayMetrics.heightPixels / 2f

        fun inject(action: Int, at: Long) {
            val event = MotionEvent.obtain(uptime, at, action, outsideX, outsideY, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        inject(MotionEvent.ACTION_DOWN, uptime)
        inject(MotionEvent.ACTION_UP, uptime + 80L)
        rule.waitForIdle()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertDatabaseIntact()
    }

    @Test fun explicitConfirmDeletesDisposableRowsExactlyOnce() {
        showNotifications()
        openConfirmation()
        assertDatabaseIntact()
        rule.onNodeWithText(deleteLabel).performClick()
        rule.waitForIdle()
        assertEquals(1, deleteCalls)
        assertEquals(0, currentItems().size)
        assertEquals(0, markReadCalls)
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithContentDescription(deleteLabel).assertDoesNotExist()
    }

    @Test fun emptyNotificationsHideBulkActions() {
        runBlocking { database.notificationDao().deleteAllNotifications() }
        state = NotificationsUiState.Success(emptyList())
        showNotifications()
        rule.onNodeWithContentDescription(deleteLabel).assertDoesNotExist()
        rule.onNodeWithContentDescription(
            context.getString(R.string.notifications_action_mark_all_read)
        ).assertDoesNotExist()
        assertEquals(0, deleteCalls)
    }
}
