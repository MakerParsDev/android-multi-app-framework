package com.parsfilo.contentapp.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.parsfilo.contentapp.R
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.navigation.AppRoute

@Composable
fun AppBottomBarWithFab(
    currentDestination: NavDestination?,
    onNavigateToDestination: (AppRoute) -> Unit,
    unreadNotificationCount: Int,
    unreadMessageCount: Int,
    newOtherAppsCount: Int,
    shouldShowSubscriptionBadge: Boolean,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val view = LocalView.current
    val currentOnNavigateToDestination = rememberUpdatedState(onNavigateToDestination)
    val selectedId =
        remember(currentDestination) {
            bottomDestinations
                .firstOrNull { destination ->
                    currentDestination?.hierarchy?.any {
                        it.route == destination.route.route
                    } == true
                }?.id ?: AppRoute.HomeGraph.route
        }
    val systemNavBottomInsetPx =
        ViewCompat
            .getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?.toFloat() ?: with(density) { 48.dp.toPx() }
    NavigationBar(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val insetTop = size.height - systemNavBottomInsetPx - (stroke / 2f)
                    drawLine(
                        color = colorScheme.onSurface.copy(alpha = 0.82f),
                        start = Offset(0f, insetTop),
                        end = Offset(size.width, insetTop),
                        strokeWidth = stroke,
                    )
                },
        tonalElevation = dimens.elevationLow,
        containerColor = colorScheme.surface,
    ) {
        bottomDestinations.forEach { destination ->
            key(destination.id) {
                val onClick =
                    remember(destination.route) {
                        {
                            currentOnNavigateToDestination.value(destination.route)
                        }
                    }

                AppBottomNavigationItem(
                    destination = destination,
                    selected = destination.id == selectedId,
                    badgeText =
                        destination.badgeText(
                            unreadNotificationCount = unreadNotificationCount,
                            unreadMessageCount = unreadMessageCount,
                            newOtherAppsCount = newOtherAppsCount,
                            shouldShowSubscriptionBadge = shouldShowSubscriptionBadge,
                        ),
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun RowScope.AppBottomNavigationItem(
    destination: BottomDestination,
    selected: Boolean,
    badgeText: String?,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val title = stringResource(destination.titleRes)

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            BadgedBox(
                badge = {
                    if (!badgeText.isNullOrBlank()) {
                        Badge(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError,
                        ) {
                            Text(text = badgeText)
                        }
                    }
                },
            ) {
                Icon(
                    painter = painterResource(destination.iconRes),
                    contentDescription = title,
                )
            }
        },
        label = {
            Text(
                text = title,
                fontSize = 10.sp,
            )
        },
        alwaysShowLabel = true,
        colors =
            NavigationBarItemDefaults.colors(
                selectedIconColor = colorScheme.onSecondaryContainer,
                selectedTextColor = colorScheme.onSecondaryContainer,
                indicatorColor = colorScheme.secondaryContainer,
                unselectedIconColor = colorScheme.onSurfaceVariant,
                unselectedTextColor = colorScheme.onSurfaceVariant,
            ),
    )
}

@Immutable
internal data class BottomDestination(
    val route: AppRoute,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    val id: String = route.route
}

internal val bottomDestinations =
    listOf(
        BottomDestination(
            route = AppRoute.Subscription,
            titleRes = R.string.nav_premium,
            iconRes = R.drawable.ic_star,
        ),
        BottomDestination(
            route = AppRoute.OtherApps,
            titleRes = R.string.nav_apps,
            iconRes = R.drawable.ic_apps,
        ),
        BottomDestination(
            route = AppRoute.HomeGraph,
            titleRes = R.string.nav_home,
            iconRes = R.drawable.ic_home,
        ),
        BottomDestination(
            route = AppRoute.MessagesGraph,
            titleRes = R.string.nav_messages,
            iconRes = R.drawable.ic_email,
        ),
        BottomDestination(
            route = AppRoute.NotificationsGraph,
            titleRes = R.string.nav_alerts,
            iconRes = R.drawable.ic_notifications,
        ),
    )

internal fun BottomDestination.badgeText(
    unreadNotificationCount: Int,
    unreadMessageCount: Int,
    newOtherAppsCount: Int,
    shouldShowSubscriptionBadge: Boolean,
): String? =
    when (route) {
        AppRoute.NotificationsGraph ->
            if (BadgeFeatureFlags.SHOW_NOTIFICATIONS_BADGE) {
                unreadNotificationCount.toBottomBarBadgeText()
            } else {
                null
            }

        AppRoute.OtherApps ->
            if (BadgeFeatureFlags.SHOW_OTHER_APPS_BADGE) {
                newOtherAppsCount.toBottomBarBadgeText()
            } else {
                null
            }

        AppRoute.MessagesGraph ->
            if (BadgeFeatureFlags.SHOW_MESSAGES_BADGE) {
                unreadMessageCount.toBottomBarBadgeText()
            } else {
                null
            }

        AppRoute.Subscription ->
            if (
                BadgeFeatureFlags.SHOW_SUBSCRIPTION_BADGE && shouldShowSubscriptionBadge
            ) {
                "!"
            } else {
                null
            }

        else -> null
    }

internal fun Int.toBottomBarBadgeText(): String? =
    when {
        this <= 0 -> null
        this > 9 -> "9+"
        else -> toString()
    }
