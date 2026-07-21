package com.parsfilo.contentapp.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.parsfilo.contentapp.R
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.designsystem.theme.app_transparent
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.logTabSelected
import com.parsfilo.contentapp.feature.audio.ui.AudioPlayerViewModel
import com.parsfilo.contentapp.navigation.AppRoute
import com.parsfilo.contentapp.navigation.NotificationOpenRequest
import com.parsfilo.contentapp.product.AppProductDefinition
import com.parsfilo.contentapp.ui.update.HardUpdateRequiredOverlay
import com.parsfilo.contentapp.ui.update.SoftUpdateDialog
import com.parsfilo.contentapp.update.UpdateGateViewModel
import com.parsfilo.contentapp.update.UpdatePolicy
import com.parsfilo.contentapp.update.openPlayStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun ContentApp(
    openNotificationsEvents: Flow<NotificationOpenRequest> = emptyFlow(),
    appAnalytics: AppAnalytics,
    onPrivacyOptionsUpdated: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
    audioPlayerViewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    val productDefinition = AppProductDefinition.current
    val dimens = LocalDimens.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isMediumOrExpanded =
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle(
        initialValue = 0,
    )
    val unreadMessageCount by viewModel.unreadMessageCount.collectAsStateWithLifecycle(
        initialValue = 0,
    )
    val newOtherAppsCount by viewModel.newOtherAppsCount.collectAsStateWithLifecycle(
        initialValue = 0,
    )
    val shouldShowSubscriptionBadge by viewModel.shouldShowSubscriptionBadge
        .collectAsStateWithLifecycle(
            initialValue = true,
        )
    val isUserSignedIn by viewModel.isUserSignedIn.collectAsStateWithLifecycle()
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsStateWithLifecycle()
    val updateGateViewModel: UpdateGateViewModel = hiltViewModel()
    val updatePolicy by updateGateViewModel.activePolicy.collectAsStateWithLifecycle()
    val updateDebugSnapshot by updateGateViewModel.debugSnapshot.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = true

    val topLevelRoutes =
        listOf(
            AppRoute.Subscription,
            AppRoute.OtherApps,
            AppRoute.HomeGraph,
            AppRoute.MessagesGraph,
            AppRoute.NotificationsGraph,
        )

    fun navigateToRoute(route: AppRoute) {
        if (route == AppRoute.HomeGraph) {
            navController.navigate(AppRoute.HomeGraph.route) {
                popUpTo(AppRoute.HomeGraph.route) {
                    inclusive = true
                }
                launchSingleTop = true
                restoreState = false
            }
        } else {
            navController.navigate(route.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val selectedTopLevelRoute =
        topLevelRoutes.firstOrNull { destination ->
            currentDestination?.hierarchy?.any { it.route == destination.route } == true
        } ?: AppRoute.HomeGraph

    fun navBadgeText(route: AppRoute): String? =
        when (route) {
            AppRoute.NotificationsGraph ->
                if (BadgeFeatureFlags.SHOW_NOTIFICATIONS_BADGE) {
                    unreadNotificationCount.toBadgeText()
                } else {
                    null
                }
            AppRoute.OtherApps ->
                if (BadgeFeatureFlags.SHOW_OTHER_APPS_BADGE) {
                    newOtherAppsCount.toBadgeText()
                } else {
                    null
                }
            AppRoute.MessagesGraph ->
                if (BadgeFeatureFlags.SHOW_MESSAGES_BADGE) {
                    unreadMessageCount.toBadgeText()
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

    LaunchedEffect(selectedTopLevelRoute) {
        viewModel.onTopLevelRouteVisited(selectedTopLevelRoute)
        appAnalytics.logTabSelected(selectedTopLevelRoute.route)
    }

    LaunchedEffect(openNotificationsEvents) {
        openNotificationsEvents.collect { request ->
            when (request.target) {
                NotificationOpenRequest.Target.LIST -> {
                    navigateToRoute(AppRoute.NotificationsGraph)
                }

                NotificationOpenRequest.Target.DETAIL -> {
                    val rowId = request.notificationRowId
                    if (rowId != null && rowId > 0L) {
                        navController.navigate(AppRoute.NotificationDetail.createRoute(rowId)) {
                            launchSingleTop = true
                        }
                    } else {
                        navigateToRoute(AppRoute.NotificationsGraph)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        updateGateViewModel.checkForUpdate()
    }

    DisposableEffect(lifecycleOwner, audioPlayerViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    val isConfigChange = (context as? Activity)?.isChangingConfigurations == true
                    if (!isConfigChange) {
                        audioPlayerViewModel.stopForAppBackground()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppTheme(
        darkTheme = darkModeEnabled,
        flavorName = productDefinition.themeTokenKey,
    ) {
        Box(
            modifier =
                Modifier
                    .testTag("app_root")
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            if (isMediumOrExpanded && showBottomBar) {
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        topLevelRoutes.forEach { route ->
                            item(
                                selected = route == selectedTopLevelRoute,
                                onClick = { navigateToRoute(route) },
                                icon = {
                                    val iconId =
                                        when (route) {
                                            AppRoute.Subscription -> R.drawable.ic_star
                                            AppRoute.OtherApps -> R.drawable.ic_apps
                                            AppRoute.HomeGraph -> R.drawable.ic_home
                                            AppRoute.MessagesGraph -> R.drawable.ic_email
                                            AppRoute.NotificationsGraph -> R.drawable.ic_notifications
                                            else -> R.drawable.ic_home
                                        }
                                    Icon(
                                        painter = painterResource(id = iconId),
                                        contentDescription =
                                            when (route) {
                                                AppRoute.Subscription ->
                                                    stringResource(
                                                        R.string.nav_premium,
                                                    )
                                                AppRoute.OtherApps ->
                                                    stringResource(
                                                        R.string.nav_apps,
                                                    )
                                                AppRoute.HomeGraph ->
                                                    stringResource(
                                                        R.string.nav_home,
                                                    )
                                                AppRoute.MessagesGraph ->
                                                    stringResource(
                                                        R.string.nav_messages,
                                                    )
                                                AppRoute.NotificationsGraph ->
                                                    stringResource(
                                                        R.string.nav_alerts,
                                                    )
                                                else -> stringResource(R.string.nav_home)
                                            },
                                    )
                                },
                                label = {
                                    Text(
                                        text =
                                            when (route) {
                                                AppRoute.Subscription ->
                                                    stringResource(
                                                        R.string.nav_premium,
                                                    )
                                                AppRoute.OtherApps ->
                                                    stringResource(
                                                        R.string.nav_apps,
                                                    )
                                                AppRoute.HomeGraph ->
                                                    stringResource(
                                                        R.string.nav_home,
                                                    )
                                                AppRoute.MessagesGraph ->
                                                    stringResource(
                                                        R.string.nav_messages,
                                                    )
                                                AppRoute.NotificationsGraph ->
                                                    stringResource(
                                                        R.string.nav_alerts,
                                                    )
                                                else -> stringResource(R.string.nav_home)
                                            },
                                    )
                                },
                                badge =
                                    navBadgeText(route)?.let { badgeText ->
                                        { Text(text = badgeText) }
                                    },
                            )
                        }
                    },
                ) {
                    Scaffold(
                        containerColor = app_transparent,
                        contentWindowInsets =
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                            ),
                    ) { innerPadding ->
                        AppNavHost(
                            navController = navController,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .consumeWindowInsets(innerPadding),
                            isUserSignedIn = isUserSignedIn,
                            audioPlayerViewModel = audioPlayerViewModel,
                            appAnalytics = appAnalytics,
                            onPrivacyOptionsUpdated = onPrivacyOptionsUpdated,
                            updateDebugSummary = updateDebugSnapshot?.toSummaryText(),
                            onUpdateDebugFetchNow = updateGateViewModel::fetchNowForDebug,
                            onUpdateDebugSimulateSoft = updateGateViewModel::simulateSoftPrompt,
                            onUpdateDebugSimulateHard = updateGateViewModel::simulateHardBlock,
                            onUpdateDebugClearSimulation = updateGateViewModel::clearSimulation,
                            onUpdateDebugResetSoftPrompt = updateGateViewModel::resetSoftPromptForSession,
                        )
                    }
                }
            } else {
                Scaffold(
                    containerColor = app_transparent,
                    contentWindowInsets =
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    bottomBar = {
                        if (showBottomBar) {
                            AppBottomBarWithFab(
                                currentDestination = currentDestination,
                                onNavigateToDestination = ::navigateToRoute,
                                unreadNotificationCount = unreadNotificationCount,
                                unreadMessageCount = unreadMessageCount,
                                newOtherAppsCount = newOtherAppsCount,
                                shouldShowSubscriptionBadge = shouldShowSubscriptionBadge,
                            )
                        }
                    },
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding),
                        isUserSignedIn = isUserSignedIn,
                        audioPlayerViewModel = audioPlayerViewModel,
                        appAnalytics = appAnalytics,
                        onPrivacyOptionsUpdated = onPrivacyOptionsUpdated,
                        updateDebugSummary = updateDebugSnapshot?.toSummaryText(),
                        onUpdateDebugFetchNow = updateGateViewModel::fetchNowForDebug,
                        onUpdateDebugSimulateSoft = updateGateViewModel::simulateSoftPrompt,
                        onUpdateDebugSimulateHard = updateGateViewModel::simulateHardBlock,
                        onUpdateDebugClearSimulation = updateGateViewModel::clearSimulation,
                        onUpdateDebugResetSoftPrompt = updateGateViewModel::resetSoftPromptForSession,
                    )
                }
            }

            when (val policy = updatePolicy) {
                is UpdatePolicy.Hard ->
                    HardUpdateRequiredOverlay(
                        policy = policy,
                        onUpdateClick = { openPlayStore(context) },
                        modifier = Modifier.align(Alignment.Center),
                    )

                is UpdatePolicy.Soft ->
                    SoftUpdateDialog(
                        policy = policy,
                        onUpdateClick = { openPlayStore(context) },
                        onLaterClick = updateGateViewModel::dismissSoftPromptForSession,
                    )

                UpdatePolicy.None -> Unit
            }
        }
    }
}

private fun Int.toBadgeText(): String? =
    when {
        this <= 0 -> null
        this > 9 -> "9+"
        else -> toString()
    }
