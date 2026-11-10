package com.parsfilo.contentapp

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
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
 * Disposable Android 12 CI smoke, not a signed Play/OEM reproduction of the v61 ANRs.
 * A media command initiated while the Activity is visible must keep foreground playback
 * and its native media notification when the user returns HOME.
 */
@RunWith(AndroidJUnit4::class)
class MediaForegroundServiceApi31Test {
    @Test
    fun foregroundUserPlaybackContinuesWithNotificationAfterHome() {
        assumeTrue("Dedicated API 31 emulator only", Build.VERSION.SDK_INT == 31)
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
