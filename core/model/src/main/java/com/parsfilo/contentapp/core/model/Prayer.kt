package com.parsfilo.contentapp.core.model

/**
 * Namaz Suresi veya Duası modeli
 * namazsurelerivedualarsesli flavor'ında kullanılır
 */
data class Prayer(
    val sureID: Int,
    val sureAdiAR: String,
    val sureAdiEN: String,
    val sureAdiTR: String,
    val sureAdiDE: String = "",
    val ayetSayisi: Int,
    // MP3 dosya adı (uppercase)
    val sureMedya: String,
    val ayetler: List<PrayerVerse>
)

/**
 * Namaz/Dua içindeki ayet modeli
 */
data class PrayerVerse(
    val ayetID: Int,
    // Arapça metin
    val ayetAR: String,
    // Latin okunuş
    val ayetLAT: String,
    // Türkçe meal
    val ayetTR: String,
    // İngilizce çeviri (opsiyonel)
    val ayetEN: String = "",
    // Almanca çeviri (opsiyonel)
    val ayetDE: String = "",
)
