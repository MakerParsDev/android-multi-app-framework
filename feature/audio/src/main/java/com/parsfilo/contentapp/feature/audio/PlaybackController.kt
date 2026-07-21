package com.parsfilo.contentapp.feature.audio

interface PlaybackController {
    fun play()

    fun pause()

    fun seekTo(position: Long)
    // Add more as needed
}
