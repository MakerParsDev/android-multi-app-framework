package com.parsfilo.contentapp.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class AppRoute(
    val route: String,
    val arguments: List<NamedNavArgument> = emptyList(),
) {
    // Top-level tabs
    data object HomeGraph : AppRoute("home_graph")

    data object Subscription : AppRoute("subscription")

    data object OtherApps : AppRoute("other_apps")

    data object MessagesGraph : AppRoute("messages_graph")

    data object NotificationsGraph : AppRoute("notifications_graph")

    // Home Graph Destinations
    data object Content : AppRoute("content")

    data object PrayerTimesHome : AppRoute("prayer_times_home")

    data object PrayerLocationPicker : AppRoute("prayer_location_picker")

    data object Qibla : AppRoute("qibla")

    data object PrayerList : AppRoute("prayer_list")

    data object PrayerDetail : AppRoute(
        route = "prayer_detail/{prayerId}",
        arguments = listOf(navArgument("prayerId") { type = NavType.IntType }),
    ) {
        fun createRoute(prayerId: Int) = "prayer_detail/$prayerId"
    }

    data object MiraclesList : AppRoute("miracles_list")

    data object ZikirCounter : AppRoute("zikir_counter")

    data object QuranSuraList : AppRoute("quran_sura_list")

    data object QuranBookmarks : AppRoute("quran_bookmarks")

    data object QuranSuraDetail : AppRoute(
        route = "quran_sura_detail/{suraNumber}",
        arguments = listOf(navArgument("suraNumber") { type = NavType.IntType }),
    ) {
        fun createRoute(suraNumber: Int) = "quran_sura_detail/$suraNumber"
    }

    data object QuranReciterSettings : AppRoute("quran_reciter_settings")

    data object MiraclesDetail : AppRoute(
        route = "miracles_detail/{prayerIndex}",
        arguments = listOf(navArgument("prayerIndex") { type = NavType.IntType }),
    ) {
        fun createRoute(prayerIndex: Int) = "miracles_detail/$prayerIndex"
    }

    data object Settings : AppRoute("settings")

    data object Rewards : AppRoute("rewards")

    // Messages Graph Destinations
    data object MessageList : AppRoute("message_list")

    data object MessageDetail : AppRoute(
        route = "message_detail/{messageId}",
        arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
    ) {
        fun createRoute(messageId: String) = "message_detail/$messageId"
    }

    data object Auth : AppRoute("auth")

    // Notifications Graph Destinations
    data object NotificationList : AppRoute("notification_list")

    data object NotificationDetail : AppRoute(
        route = "notification_detail/{notificationId}",
        arguments = listOf(navArgument("notificationId") { type = NavType.LongType }),
    ) {
        fun createRoute(notificationId: Long) = "notification_detail/$notificationId"
    }
}
