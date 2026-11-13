package com.parsfilo.contentapp.feature.audio.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Media3 MediaSessionService.
 *
 * Audio yükleme ve kontrol AudioPlayerViewModel tarafından yapılır;
 * bu servis yalnızca MediaSession'ı expose eder ve foreground notification sağlar.
 * Player singleton olduğu için ViewModel'ın hazırladığı MediaItem
 * burada zaten yüklü olur.
 */
@AndroidEntryPoint
class AudioService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    @OptIn(UnstableApi::class)
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // A cold media-button start has no UI to prepare a MediaItem. Let Media3
        // acknowledge and stop that foreground-service start instead of timing out.
        val controllerType = controllerInfo.connectionHints.getString(
            MediaSessionService.CONNECTION_HINT_KEY_CONTROLLER_INFO_TYPE,
        )
        if (!shouldExposeSessionForMediaButton(controllerType, player.mediaItemCount)) {
            return null
        }
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * Media buttons can start this service without any Activity or ViewModel alive.
 * No playlist means no playback, so accepting that fallback session would keep
 * a foreground-service start pending until Android kills the process.
 */
internal fun shouldExposeSessionForMediaButton(controllerType: String?, mediaItemCount: Int): Boolean {
    return controllerType != Intent.ACTION_MEDIA_BUTTON || mediaItemCount > 0
}
