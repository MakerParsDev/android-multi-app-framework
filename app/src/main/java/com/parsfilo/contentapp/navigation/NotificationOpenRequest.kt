package com.parsfilo.contentapp.navigation

data class NotificationOpenRequest(
    val target: Target,
    val notificationRowId: Long? = null,
) {
    enum class Target {
        LIST,
        DETAIL,
    }
}
