package com.parsfilo.contentapp.core.model

data class QuranSura(
    val number: Int,
    val nameArabic: String,
    val nameLatin: String,
    val nameTurkish: String,
    val nameEnglish: String,
    val revelationType: RevelationType,
    val ayahCount: Int,
    val rukus: Int = 0,
)

enum class RevelationType {
    MECCAN,
    MEDINAN,
}

data class QuranAyah(
    val suraNumber: Int,
    val ayahNumber: Int,
    val arabic: String,
    val latin: String,
    val turkish: String,
    val english: String,
    val german: String,
)

data class QuranAudioState(
    val reciterId: String,
    val suraNumber: Int,
    val ayahNumber: Int,
    val isPlaying: Boolean,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val downloadProgress: Float = 0f,
)

data class QuranBookmark(
    val suraNumber: Int,
    val ayahNumber: Int,
    val savedAt: Long = System.currentTimeMillis(),
    val note: String = "",
)

enum class QuranDisplayMode {
    ARABIC,
    LATIN,
    TRANSLATION,
}
