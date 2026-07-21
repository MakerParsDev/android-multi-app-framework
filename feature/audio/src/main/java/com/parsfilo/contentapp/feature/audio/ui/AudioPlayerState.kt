package com.parsfilo.contentapp.feature.audio.ui

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val isLoaded: Boolean = false,
    val assetLoading: Boolean = false,
    val assetReady: Boolean = false,
    val assetError: String? = null
)
