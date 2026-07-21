package com.parsfilo.contentapp.core.model

data class Notification(
    val id: Long = 0,
    val notificationId: String,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val type: String?,
    val isRead: Boolean,
    val timestamp: Long,
    val dataPayloadJson: String?
)
