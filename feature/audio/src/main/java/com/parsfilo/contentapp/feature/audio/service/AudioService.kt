package com.parsfilo.contentapp.feature.audio.service

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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
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
