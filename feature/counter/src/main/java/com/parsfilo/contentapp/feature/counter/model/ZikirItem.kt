package com.parsfilo.contentapp.feature.counter.model

data class ZikirItem(
    val key: String,
    val arabicText: String,
    val latinText: String,
    val turkishMeaning: String,
    val defaultTarget: Int,
    val virtue: String,
    val virtueSource: String,
)
