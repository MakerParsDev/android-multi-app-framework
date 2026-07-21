package com.parsfilo.contentapp.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.parsfilo.contentapp.core.model.Notification

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["notificationId"], unique = true)]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
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

fun NotificationEntity.asExternalModel() = Notification(
    id = id,
    notificationId = notificationId,
    title = title,
    body = body,
    imageUrl = imageUrl,
    type = type,
    isRead = isRead,
    timestamp = timestamp,
    dataPayloadJson = dataPayloadJson
)
