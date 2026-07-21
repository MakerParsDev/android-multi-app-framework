package com.parsfilo.contentapp.feature.otherapps.model

data class OtherApp(
    val appName: String,
    val packageName: String,
    val appIconUrl: String,
    val isNew: Boolean = false
)
