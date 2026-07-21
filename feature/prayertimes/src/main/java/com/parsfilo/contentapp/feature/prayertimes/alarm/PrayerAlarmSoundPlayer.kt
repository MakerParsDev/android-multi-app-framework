package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerAlarmSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null

    fun play(uriString: String?) {
        playInternal(
            uriString = uriString,
            preview = false,
        )
    }

    fun playPreview(uriString: String?) {
        playInternal(
            uriString = uriString,
            preview = true,
        )
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        mediaPlayer?.release()
        mediaPlayer = null
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun playInternal(
        uriString: String?,
        preview: Boolean,
    ) {
        stop()

        val parsedUri = uriString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { it.toUri() }.getOrNull() }

        val candidateUris = buildList {
            parsedUri?.let(::add)
            if (preview) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let(::add)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
            }
        }.distinct()

        if (candidateUris.isEmpty()) return

        val ringtoneAttributes = AudioAttributes.Builder()
            .setUsage(
                if (preview) {
                    AudioAttributes.USAGE_NOTIFICATION_EVENT
                } else {
                    AudioAttributes.USAGE_ALARM
                },
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        candidateUris.forEach { uri ->
            val playedByRingtone = runCatching {
                val candidate = RingtoneManager.getRingtone(context, uri) ?: return@runCatching false
                runCatching { candidate.audioAttributes = ringtoneAttributes }
                candidate.play()
                if (candidate.isPlaying) {
                    ringtone = candidate
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (playedByRingtone) {
                Timber.d("PrayerAlarmSoundPlayer: played by ringtone uri=%s preview=%s", uri, preview)
                return
            }

            val playedByMediaPlayer = runCatching {
                val attributes = AudioAttributes.Builder()
                    .setUsage(
                        if (preview) {
                            AudioAttributes.USAGE_MEDIA
                        } else {
                            AudioAttributes.USAGE_ALARM
                        },
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val player = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, uri)
                    isLooping = false
                    prepare()
                    start()
                    setOnCompletionListener {
                        it.release()
                        if (mediaPlayer === it) {
                            mediaPlayer = null
                        }
                    }
                }
                mediaPlayer = player
                true
            }.getOrDefault(false)

            if (playedByMediaPlayer) {
                Timber.d("PrayerAlarmSoundPlayer: played by mediaPlayer uri=%s preview=%s", uri, preview)
                return
            }
        }

        Timber.w("PrayerAlarmSoundPlayer: falling back to tone preview=%s", preview)
        playToneFallback(preview)
    }

    private fun playToneFallback(preview: Boolean) {
        val streamType = if (preview) {
            AudioManager.STREAM_MUSIC
        } else {
            AudioManager.STREAM_ALARM
        }
        val generator = runCatching { ToneGenerator(streamType, 100) }.getOrNull() ?: return
        toneGenerator = generator
        runCatching { generator.startTone(ToneGenerator.TONE_PROP_BEEP, 700) }
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (toneGenerator === generator) {
                    toneGenerator?.release()
                    toneGenerator = null
                }
            },
            900L,
        )
    }
}
