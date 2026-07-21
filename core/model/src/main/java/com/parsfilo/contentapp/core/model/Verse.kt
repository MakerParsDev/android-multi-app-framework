package com.parsfilo.contentapp.core.model

data class Verse(
    val id: Int,
    val arabic: String,
    val latin: String,
    val turkish: String,
    // Optional translations for app-language dependent "Meal/Translation" mode.
    val english: String = "",
    val german: String = ""
)
