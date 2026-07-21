package com.parsfilo.contentapp.core.model

data class Message(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val subject: String = "",
    val message: String = "",
    val category: String = "",
    val priority: String = "NORMAL",
    val status: String = "PENDING",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val hasReply: Boolean = false,
    val replyCount: Int = 0,
    val lastReplyAt: Long? = null,
    val replies: List<MessageReply> = emptyList()
)

data class MessageReply(
    val id: String = "",
    val messageId: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val reply: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
