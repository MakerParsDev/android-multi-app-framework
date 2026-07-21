package com.parsfilo.contentapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parsfilo.contentapp.core.database.model.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    // Unread first, then newest. This matches the badge semantics (unread count) and improves UX.
    @Query("SELECT * FROM notifications ORDER BY isRead ASC, timestamp DESC")
    fun getNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE notificationId = :notificationId LIMIT 1")
    suspend fun getByNotificationId(notificationId: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    fun getById(id: Long): Flow<NotificationEntity?>

    @Query("SELECT * FROM notifications WHERE isRead = 0")
    fun getUnreadNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET isRead = 0 WHERE id = :id")
    suspend fun markAsUnread(id: Long)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()
}
