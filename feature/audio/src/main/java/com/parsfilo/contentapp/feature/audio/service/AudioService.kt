package com.parsfilo.contentapp.feature.audio.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.net.URI

private const val RESUME_PREFS = "audio_playback_resumption"
private const val URI_KEY = "last_played_local_uri"
private const val POSITION_KEY = "last_played_position_ms"
private const val SAVE_INTERVAL_MS = 10_000L

/**
 * The UI supplies media through its MediaController. If the UI and service are
 * gone, a media button may resume a previously played local/asset item without
 * needing the Activity or a remote download to initialize first.
 */
@AndroidEntryPoint
class AudioService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var playbackListener: Player.Listener? = null
    private val resumeStore by lazy { LocalAudioResumeStore(this) }
    private val handler = Handler(Looper.getMainLooper())

    private val saveProgress = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                resumeStore.savePlayedItem(player.currentMediaItem, player.currentPosition)
                handler.postDelayed(this, SAVE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        val observer = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(saveProgress)
                if (isPlaying) {
                    resumeStore.savePlayedItem(player.currentMediaItem, player.currentPosition)
                    handler.postDelayed(saveProgress, SAVE_INTERVAL_MS)
                } else {
                    resumeStore.updatePositionIfSaved(player.currentMediaItem, player.currentPosition)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                resumeStore.updatePositionIfSaved(player.currentMediaItem, player.currentPosition)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) resumeStore.clear()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) resumeStore.clear()
            }
        }
        playbackListener = observer
        player.addListener(observer)
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(
                object : MediaSession.Callback {
                    @OptIn(UnstableApi::class)
                    override fun onPlaybackResumption(
                        mediaSession: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        isForPlayback: Boolean,
                    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                        val bookmark = resumeStore.load()
                            ?: return Futures.immediateFailedFuture(
                                IllegalStateException("No locally playable audio to resume"),
                            )
                        return Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(
                                listOf(MediaItem.fromUri(bookmark.uri)),
                                0,
                                bookmark.positionMs,
                            ),
                        )
                    }
                },
            )
            .build()
    }

    @OptIn(UnstableApi::class)
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val controllerType = controllerInfo.connectionHints.getString(
            MediaSessionService.CONNECTION_HINT_KEY_CONTROLLER_INFO_TYPE,
        )
        // Empty cold foreground starts are not accepted without playable media.
        if (!shouldExposeSessionForMediaButton(
                controllerType,
                player.mediaItemCount,
                resumeStore.load() != null,
            )
        ) {
            return null
        }
        return mediaSession
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveProgress)
        if (::player.isInitialized) {
            // Releasing ExoPlayer can emit an empty-media event; detach first
            // so a normal service shutdown keeps the last-played bookmark.
            playbackListener?.let { player.removeListener(it) }
            playbackListener = null
            // A stopped service may report position 0 even when the last
            // actual pause was at a later position. Keep that checkpoint.
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

internal fun shouldExposeSessionForMediaButton(
    controllerType: String?,
    mediaItemCount: Int,
    hasResumableMedia: Boolean = false,
): Boolean = controllerType != Intent.ACTION_MEDIA_BUTTON || mediaItemCount > 0 || hasResumableMedia

/**
 * Only previously downloaded private audio or bundled assets can cold resume.
 * Network streams and missing files must not start an unplayable FGS.
 */
internal fun isResumableAudioUri(uri: String?, filesDir: File): Boolean {
    if (uri.isNullOrBlank()) return false
    if (uri.startsWith("asset:///")) {
        val path = uri.removePrefix("asset:///")
        if (path.isBlank()) return false
        return path.split('/').all { segment ->
            segment.matches(Regex("[A-Za-z0-9._-]+")) && segment != "." && segment != ".."
        }
    }
    val file = try {
        File(URI(uri)).canonicalFile
    } catch (_: Exception) {
        return false
    }
    val privateCache = File(filesDir, "audio_cache").canonicalFile
    return file.isFile && file.parentFile == privateCache
}

private data class AudioResumeBookmark(val uri: Uri, val positionMs: Long)

private class LocalAudioResumeStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(RESUME_PREFS, Context.MODE_PRIVATE)

    fun load(): AudioResumeBookmark? {
        val text = prefs.getString(URI_KEY, null) ?: return null
        if (!isResumableAudioUri(text, context.filesDir) || !assetExists(text)) {
            clear()
            return null
        }
        return AudioResumeBookmark(Uri.parse(text), prefs.getLong(POSITION_KEY, 0L).coerceAtLeast(0L))
    }

    fun savePlayedItem(item: MediaItem?, positionMs: Long) {
        val text = item?.localConfiguration?.uri?.toString()
        if (!isResumableAudioUri(text, context.filesDir)) {
            clear()
            return
        }
        prefs.edit()
            .putString(URI_KEY, text)
            .putLong(POSITION_KEY, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun updatePositionIfSaved(item: MediaItem?, positionMs: Long) {
        val currentUri = item?.localConfiguration?.uri?.toString()
        if (currentUri != null && currentUri == prefs.getString(URI_KEY, null)) {
            prefs.edit().putLong(POSITION_KEY, positionMs.coerceAtLeast(0L)).apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun assetExists(text: String): Boolean {
        if (!text.startsWith("asset:///")) return true
        return try {
            context.assets.open(text.removePrefix("asset:///")).close()
            true
        } catch (_: IOException) {
            false
        }
    }
}
