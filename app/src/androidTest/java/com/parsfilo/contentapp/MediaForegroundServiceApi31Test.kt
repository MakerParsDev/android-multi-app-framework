package com.parsfilo.contentapp

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.feature.audio.service.AudioService
import com.parsfilo.contentapp.feature.audio.service.ResumableAudioMediaButtonReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Disposable Android 12+ CI smoke, not a signed Play/OEM reproduction of the v61 ANRs.
 * A media command initiated while the Activity is visible must keep foreground playback
 * and its native media notification when the user returns HOME.
 */
@RunWith(AndroidJUnit4::class)
class MediaForegroundServiceApi31Test {
    @Test
    fun foregroundUserPlaybackContinuesWithNotificationAfterHome() {
        assumeTrue("Android 12+ emulator smoke", Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "ci-audio-fgs-20s.wav")
        writeSilentWav(fixture, seconds = 20)

        val activity = ActivityScenario.launch(MainActivity::class.java)
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, AudioService::class.java)),
        ).buildAsync()

        try {
            val controller = future.get(20, TimeUnit.SECONDS)
            val item = MediaItem.Builder()
                .setUri(Uri.fromFile(fixture))
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle("CI media playback test").build(),
                ).build()
            onMain(instrumentation) {
                controller.setMediaItem(item)
                controller.prepare()
                controller.play()
            }
            assertEventually("Playable media should start while the app is foreground") {
                onMain(instrumentation) { controller.isPlaying }
            }
            val initialPosition = onMain(instrumentation) { controller.currentPosition }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            assertEventually("Playback should continue and advance after pressing HOME") {
                onMain(instrumentation) {
                    controller.isPlaying && controller.currentPosition > initialPosition + 500
                }
            }
            val notifications =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertEventually("Foreground service should provide a transport notification") {
                notifications.activeNotifications.any { status ->
                    status.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 &&
                        status.notification.category == Notification.CATEGORY_TRANSPORT
                }
            }
            onMain(instrumentation) { controller.pause() }
            assertEventually("Pause must halt the playback without a crash") {
                onMain(instrumentation) { !controller.isPlaying }
            }
            onMain(instrumentation) { controller.clearMediaItems() }
        } finally {
            onMain(instrumentation) { MediaController.releaseFuture(future) }
            activity.close()
            fixture.delete()
        }
    }

    /**
     * A quick HOME transition may race with Media3's foreground-service promotion.
     * Exercise this independently from the test that waits for playback first.
     */
    @Test
    fun immediateHomeAfterPlayKeepsForegroundNotification() {
        assumeTrue("Android 12+ emulator smoke", Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "ci-audio-quick-home-20s.wav")
        writeSilentWav(fixture, seconds = 20)

        val activity = ActivityScenario.launch(MainActivity::class.java)
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, AudioService::class.java)),
        ).buildAsync()

        try {
            val controller = future.get(20, TimeUnit.SECONDS)
            val item = MediaItem.Builder()
                .setUri(Uri.fromFile(fixture))
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle("CI quick HOME playback test").build(),
                ).build()
            onMain(instrumentation) {
                controller.setMediaItem(item)
                controller.prepare()
                controller.play()
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()

            assertEventually("Playback must survive immediate HOME after Play") {
                onMain(instrumentation) { controller.isPlaying && controller.currentPosition > 500L }
            }
            val notifications =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertEventually("Immediate HOME playback must show a foreground media notification") {
                notifications.activeNotifications.any { status ->
                    status.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 &&
                        status.notification.category == Notification.CATEGORY_TRANSPORT
                }
            }
            onMain(instrumentation) {
                controller.pause()
                controller.clearMediaItems()
            }
        } finally {
            onMain(instrumentation) { MediaController.releaseFuture(future) }
            activity.close()
            fixture.delete()
        }
    }

    /**
     * Check the actual Media3 receiver override with Android SharedPreferences
     * and a private, locally playable fixture. No service is started here:
     * this isolates the pre-FGS decision from Media3's playback timing.
     */
    @Test
    fun coldMediaButtonOnlyStartsForAnExistingLocalBookmark() {
        assumeTrue("Android 12+ emulator smoke", Build.VERSION.SDK_INT >= 31)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val currentReceiver = ResumableAudioMediaButtonReceiver::class.java.name
        val registered = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(context.packageName),
            0,
        )
        assertTrue(
            "Amen should register the guarded receiver",
            registered.any { it.activityInfo?.name == currentReceiver },
        )
        assertFalse(
            "The unconditional Media3 receiver must not remain registered",
            registered.any {
                it.activityInfo?.name == "androidx.media3.session.MediaButtonReceiver"
            },
        )

        val prefs = context.getSharedPreferences("audio_playback_resumption", Context.MODE_PRIVATE)
        val originalUri = prefs.getString("last_played_local_uri", null)
        val originalPosition = prefs.getLong("last_played_position_ms", 0L)
        val fixture = File(context.filesDir, "audio_cache/ci-receiver-resume.wav")
        val receiver = object : ResumableAudioMediaButtonReceiver() {
            fun canStart(): Boolean = shouldStartForegroundService(
                context, Intent(Intent.ACTION_MEDIA_BUTTON),
            )
        }
        try {
            val audioDirectory = requireNotNull(fixture.parentFile)
            assertTrue("Create disposable audio directory", audioDirectory.isDirectory || audioDirectory.mkdirs())
            prefs.edit().clear().commit()
            assertFalse("An empty cold media button cannot start an FGS", receiver.canStart())

            writeSilentWav(fixture, seconds = 20)
            prefs.edit().putString("last_played_local_uri", Uri.fromFile(fixture).toString())
                .putLong("last_played_position_ms", 1_000L).commit()
            assertTrue("A real local resume bookmark must still work", receiver.canStart())

            fixture.delete()
            assertFalse("A missing private file cannot restart an FGS", receiver.canStart())
        } finally {
            fixture.delete()
            val edit = prefs.edit().clear()
            if (originalUri != null) {
                edit.putString("last_played_local_uri", originalUri)
                    .putLong("last_played_position_ms", originalPosition)
            }
            edit.commit()
        }
    }

    // Media3 MediaController methods (including releaseFuture) belong to its application looper.
    // Preserve the original exception rather than hiding it behind a cleanup-thread failure.
    private fun <T> onMain(instrumentation: Instrumentation, action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return requireNotNull(result).getOrThrow()
    }

    private fun assertEventually(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(250)
        }
        assertTrue(message, condition())
    }

    /** 20 seconds of deterministic silent WAV; no network request or real device user data. */
    private fun writeSilentWav(file: File, seconds: Int) {
        val sampleRate = 16_000
        val pcmSize = sampleRate * seconds * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmSize + 36)
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmSize)
        FileOutputStream(file).use { output ->
            output.write(header.array())
            val chunk = ByteArray(4_096)
            var remaining = pcmSize
            while (remaining > 0) {
                val count = minOf(remaining, chunk.size)
                output.write(chunk, 0, count)
                remaining -= count
            }
        }
    }
}
