package com.parsfilo.contentapp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.MainActivity
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.logAudioPause
import com.parsfilo.contentapp.core.firebase.logAudioPlay
import com.parsfilo.contentapp.core.firebase.logAudioStop
import com.parsfilo.contentapp.core.firebase.logScreenView
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.InterstitialTriggerKind
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialIntroSpec
import com.parsfilo.contentapp.feature.ads.ui.BannerAd
import com.parsfilo.contentapp.feature.ads.ui.NativeAdViewModel
import com.parsfilo.contentapp.feature.ads.ui.NativeFeedAdSlot
import com.parsfilo.contentapp.feature.audio.ui.AudioPlayerViewModel
import com.parsfilo.contentapp.feature.audio.ui.InlineAudioPlayer
import com.parsfilo.contentapp.feature.auth.ui.AuthRoute
import com.parsfilo.contentapp.feature.billing.ui.SubscriptionRoute
import com.parsfilo.contentapp.feature.content.ui.ContentRoute
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesContentVariant
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesDetailRoute
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesListRoute
import com.parsfilo.contentapp.feature.content.ui.prayer.PrayerDetailRoute
import com.parsfilo.contentapp.feature.content.ui.prayer.PrayerListRoute
import com.parsfilo.contentapp.feature.counter.ui.CounterRoute
import com.parsfilo.contentapp.feature.messages.ui.MessageDetailRoute
import com.parsfilo.contentapp.feature.messages.ui.MessagesRoute
import com.parsfilo.contentapp.feature.notifications.ui.NotificationDetailRoute
import com.parsfilo.contentapp.feature.notifications.ui.NotificationsRoute
import com.parsfilo.contentapp.feature.otherapps.ui.OtherAppsRoute
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import com.parsfilo.contentapp.feature.prayertimes.ui.PrayerLocationPickerRoute
import com.parsfilo.contentapp.feature.prayertimes.ui.PrayerTimesRoute
import com.parsfilo.contentapp.feature.qibla.QiblaRoute
import com.parsfilo.contentapp.feature.quran.ui.bookmarks.QuranBookmarksRoute
import com.parsfilo.contentapp.feature.quran.ui.reciter.QuranReciterSettingsRoute
import com.parsfilo.contentapp.feature.quran.ui.suradetail.QuranSuraDetailRoute
import com.parsfilo.contentapp.feature.quran.ui.surelist.QuranSuraListRoute
import com.parsfilo.contentapp.feature.settings.ui.SettingsRoute
import com.parsfilo.contentapp.monetization.AppAdUnitIds
import com.parsfilo.contentapp.navigation.AppRoute
import com.parsfilo.contentapp.product.AppProductDefinition
import com.parsfilo.contentapp.product.ContentFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    isUserSignedIn: Boolean,
    audioPlayerViewModel: AudioPlayerViewModel,
    appAnalytics: AppAnalytics,
    onPrivacyOptionsUpdated: () -> Unit = {},
    updateDebugSummary: String? = null,
    onUpdateDebugFetchNow: () -> Unit = {},
    onUpdateDebugSimulateSoft: () -> Unit = {},
    onUpdateDebugSimulateHard: () -> Unit = {},
    onUpdateDebugClearSimulation: () -> Unit = {},
    onUpdateDebugResetSoftPrompt: () -> Unit = {},
    nativeAdViewModel: NativeAdViewModel = hiltViewModel(),
) {
    val audioState by audioPlayerViewModel.playerState.collectAsStateWithLifecycle()
    val nativeAd by nativeAdViewModel.nativeAdState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hostActivity = context as? MainActivity
    val productDefinition = remember { AppProductDefinition.current }
    val useTestAds = remember { BuildConfig.USE_TEST_ADS }
    val isPrayerTimesFlavor = remember(productDefinition) { productDefinition.isPrayerTimesFlavor }
    val isQuranFlavor = remember(productDefinition) { productDefinition.contentFamily == ContentFamily.QURAN }
    val isEsmaFlavor = remember(productDefinition) { productDefinition.contentFamily == ContentFamily.ESMA }
    val showVerseCount = remember(productDefinition) { !productDefinition.hasCapability("hide_verse_count") }
    val homeStartDestination =
        remember(productDefinition) {
            resolveHomeStartDestination(productDefinition)
        }
    val coroutineScope = rememberCoroutineScope()
    var rewardedInterstitialIntroRequest by remember {
        mutableStateOf<RewardedInterstitialIntroRequest?>(null)
    }
    val adUnitIds =
        remember(context, useTestAds) {
            AppAdUnitIds.resolve(context, useTestAds)
        }

    fun clearRewardedInterstitialIntroRequest() {
        rewardedInterstitialIntroRequest = null
    }

    fun requestInterstitialAd(
        placement: AdPlacement = AdPlacement.INTERSTITIAL_NAV_BREAK,
        route: String? = null,
        triggerKind: InterstitialTriggerKind = InterstitialTriggerKind.NAV_BREAK,
        onAdDismissed: () -> Unit = {},
    ) {
        val activity = hostActivity ?: return
        Timber.d(
            "AppNavigation interstitial requested placement=%s route=%s trigger=%s",
            placement.analyticsValue,
            route,
            triggerKind.analyticsValue,
        )
        coroutineScope.launch {
            activity.adOrchestrator.showInterstitialIfEligible(
                activity = activity,
                placement = placement,
                route = route,
                triggerKind = triggerKind,
                onAdDismissed = onAdDismissed,
            )
        }
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow
            // Keep route template names (avoid ids in analytics to reduce cardinality).
            .map { entry -> entry.destination.route ?: "destination_${entry.destination.id}" }
            .distinctUntilChanged()
            .collect { route ->
                Timber.d("AppNavigation route changed route=%s", route)
                appAnalytics.logScreenView(screenName = route, screenClass = "AppNavHost")
                hostActivity?.adOrchestrator?.updateSessionContext(
                    activeRoute = route,
                    contentType = routeToContentType(route),
                )
            }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.HomeGraph.route,
        modifier = modifier,
    ) {
        // Home Graph
        navigation(
            route = AppRoute.HomeGraph.route,
            startDestination = homeStartDestination,
        ) {
            composable(AppRoute.PrayerTimesHome.route) {
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_HOME)
                }
                PrayerTimesRoute(
                    appName = stringResource(com.parsfilo.contentapp.R.string.app_name),
                    variant = PrayerAppVariant.NAMAZ_VAKITLERI,
                    bannerAdContent = {
                        BannerAd(
                            adUnitId = adUnitIds.banner,
                            placement = AdPlacement.BANNER_HOME,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    nativeAdContent = {
                        NativeFeedAdSlot(
                            nativeAd = nativeAd,
                            nativePlacement = AdPlacement.NATIVE_FEED_HOME,
                            bannerAdUnitId = adUnitIds.banner,
                            bannerPlacement = AdPlacement.BANNER_HOME,
                            route = AppRoute.PrayerTimesHome.route,
                        )
                    },
                    onOpenQibla = {
                        navController.navigate(AppRoute.Qibla.route)
                    },
                    onOpenLocationPicker = {
                        navController.navigate(AppRoute.PrayerLocationPicker.route)
                    },
                    onOpenRewards = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                )
            }
            composable(AppRoute.PrayerLocationPicker.route) {
                PrayerLocationPickerRoute(
                    appName = stringResource(com.parsfilo.contentapp.R.string.app_name),
                    onBackClick = { navController.popBackStack() },
                ) {
                    BannerAd(
                        adUnitId = adUnitIds.banner,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            composable(AppRoute.Qibla.route) {
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_HOME)
                }
                QiblaRoute(
                    appName = stringResource(com.parsfilo.contentapp.R.string.app_name),
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onRewardsClick = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                    bannerAdContent = {
                        BannerAd(
                            adUnitId = adUnitIds.banner,
                            placement = AdPlacement.BANNER_QIBLA,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    nativeAdContent = {
                        NativeFeedAdSlot(
                            nativeAd = nativeAd,
                            nativePlacement = AdPlacement.NATIVE_FEED_HOME,
                            bannerAdUnitId = adUnitIds.banner,
                            bannerPlacement = AdPlacement.BANNER_QIBLA,
                            route = AppRoute.Qibla.route,
                        )
                    },
                )
            }
            composable(AppRoute.ZikirCounter.route) {
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_ZIKIR)
                }
                CounterRoute(
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onRewardsClick = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                    onShowInterstitial = {
                        requestInterstitialAd(
                            placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                            route = AppRoute.ZikirCounter.route,
                            triggerKind = InterstitialTriggerKind.NAV_BREAK,
                        )
                    },
                    onShowRewardedHistoryAd = { onUnlocked ->
                        hostActivity?.let { activity ->
                            val placement = AdPlacement.REWARDED_INTERSTITIAL_HISTORY_UNLOCK
                            val route = AppRoute.ZikirCounter.route
                            val adUnitId =
                                AppAdUnitIds.resolvePlacement(
                                    activity,
                                    placement,
                                    useTestAds,
                                )
                            val introSpec =
                                activity.adOrchestrator.buildRewardedInterstitialIntro(
                                    placement,
                                )
                            activity.adOrchestrator.onRewardedInterstitialIntroShown(
                                placement = placement,
                                route = route,
                                adUnitId = adUnitId,
                            )
                            rewardedInterstitialIntroRequest =
                                RewardedInterstitialIntroRequest(
                                    placement = placement,
                                    route = route,
                                    adUnitId = adUnitId,
                                    spec = introSpec,
                                    onRewardEarned = onUnlocked,
                                )
                        } ?: onUnlocked()
                    },
                    bannerAdContent = {
                        BannerAd(
                            adUnitId = adUnitIds.banner,
                            placement = AdPlacement.BANNER_ZIKIR,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    nativeAdContent = {
                        NativeFeedAdSlot(
                            nativeAd = nativeAd,
                            nativePlacement = AdPlacement.NATIVE_FEED_ZIKIR,
                            bannerAdUnitId = adUnitIds.banner,
                            bannerPlacement = AdPlacement.BANNER_ZIKIR,
                            route = AppRoute.ZikirCounter.route,
                        )
                    },
                )
            }
            if (isQuranFlavor) {
                composable(AppRoute.QuranSuraList.route) {
                    LaunchedEffect(Unit) {
                        nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_HOME)
                    }
                    QuranSuraListRoute(
                        onSuraClick = { suraNumber ->
                            if (hostActivity == null) {
                                navController.navigate(
                                    AppRoute.QuranSuraDetail.createRoute(suraNumber),
                                )
                            } else {
                                requestInterstitialAd(
                                    placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                    route = AppRoute.QuranSuraList.route,
                                    triggerKind = InterstitialTriggerKind.NAV_BREAK,
                                    onAdDismissed = {
                                        navController.navigate(
                                            AppRoute.QuranSuraDetail.createRoute(suraNumber),
                                        )
                                    },
                                )
                            }
                        },
                        onBookmarksClick = {
                            navController.navigate(
                                AppRoute.QuranBookmarks.route,
                            )
                        },
                        onSettingsClick = {
                            navController.navigate(AppRoute.Settings.route)
                        },
                        onRewardsClick = {
                            navController.navigate(AppRoute.Rewards.route)
                        },
                        bannerAdContent = {
                            BannerAd(
                                adUnitId = adUnitIds.banner,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        nativeAdContent = {
                            NativeFeedAdSlot(
                                nativeAd = nativeAd,
                                nativePlacement = AdPlacement.NATIVE_FEED_HOME,
                                bannerAdUnitId = adUnitIds.banner,
                                bannerPlacement = AdPlacement.BANNER_DEFAULT,
                                route = AppRoute.QuranSuraList.route,
                            )
                        },
                    )
                }
                composable(
                    route = AppRoute.QuranSuraDetail.route,
                    arguments = AppRoute.QuranSuraDetail.arguments,
                ) {
                    LaunchedEffect(Unit) {
                        nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_CONTENT)
                    }
                    val seenAyahsInSession = remember { mutableSetOf<Int>() }
                    QuranSuraDetailRoute(
                        onBack = { navController.popBackStack() },
                        onBookmarksClick = {
                            navController.navigate(
                                AppRoute.QuranBookmarks.route,
                            )
                        },
                        onPlayAudioUrl = { url ->
                            audioPlayerViewModel.setOverrideAudioFileName(null)
                            audioPlayerViewModel.playFromUrl(url)
                        },
                        onPauseAudio = {
                            audioPlayerViewModel.pause()
                        },
                        onAyahVisibleExternal = { ayah ->
                            if (seenAyahsInSession.add(ayah.ayahNumber)) {
                                hostActivity?.adOrchestrator?.updateSessionContext(
                                    contentType = "quran",
                                    verseReadIncrement = 1,
                                )
                            }
                        },
                        bannerAdContent = {
                            BannerAd(
                                adUnitId = adUnitIds.banner,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        nativeAdContent = {
                            NativeFeedAdSlot(
                                nativeAd = nativeAd,
                                nativePlacement = AdPlacement.NATIVE_FEED_CONTENT,
                                bannerAdUnitId = adUnitIds.banner,
                                bannerPlacement = AdPlacement.BANNER_CONTENT_DETAIL,
                                route = AppRoute.QuranSuraDetail.route.substringBefore("/{"),
                            )
                        },
                    )
                }
                composable(AppRoute.QuranBookmarks.route) {
                    LaunchedEffect(Unit) {
                        nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_CONTENT)
                    }
                    QuranBookmarksRoute(
                        onAyahClick = { sura, _ ->
                            navController.navigate(AppRoute.QuranSuraDetail.createRoute(sura))
                        },
                        onBack = { navController.popBackStack() },
                        bannerAdContent = {
                            BannerAd(
                                adUnitId = adUnitIds.banner,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        nativeAdContent = {
                            NativeFeedAdSlot(
                                nativeAd = nativeAd,
                                nativePlacement = AdPlacement.NATIVE_FEED_CONTENT,
                                bannerAdUnitId = adUnitIds.banner,
                                bannerPlacement = AdPlacement.BANNER_CONTENT_DETAIL,
                                route = AppRoute.QuranBookmarks.route,
                            )
                        },
                    )
                }
                composable(AppRoute.QuranReciterSettings.route) {
                    QuranReciterSettingsRoute(onBack = { navController.popBackStack() })
                }
            }
            composable(AppRoute.Content.route) {
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_CONTENT)
                }
                LaunchedEffect(Unit) {
                    audioPlayerViewModel.setOverrideAudioFileName(null)
                }
                ContentRoute(
                    appName = stringResource(com.parsfilo.contentapp.R.string.app_name),
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onRewardsClick = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                    onModeChanged = { oldMode, newMode ->
                        if (isDisplayModeSwitch(oldMode, newMode)) {
                            requestInterstitialAd(
                                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                route = "mode_switch_content",
                                triggerKind = InterstitialTriggerKind.MODE_SWITCH,
                            )
                        }
                    },
                    audioPlayerContent = {
                        InlineAudioPlayer(
                            state = audioState,
                            onPlayPause = {
                                if (audioState.isPlaying) {
                                    appAnalytics.logAudioPause(
                                        positionMs = audioState.currentPosition,
                                        durationMs = audioState.duration,
                                    )
                                    requestInterstitialAd(
                                        placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                        route = "audio_pause_content",
                                        triggerKind = InterstitialTriggerKind.AUDIO_PAUSE,
                                    )
                                } else {
                                    hostActivity?.adOrchestrator?.updateSessionContext(
                                        audioPlayed = true,
                                    )
                                    appAnalytics.logAudioPlay(
                                        positionMs = audioState.currentPosition,
                                        durationMs = audioState.duration,
                                    )
                                }
                                audioPlayerViewModel.togglePlayPause()
                            },
                            onStop = {
                                appAnalytics.logAudioStop(
                                    positionMs = audioState.currentPosition,
                                    durationMs = audioState.duration,
                                )
                                if (audioState.currentPosition > 0L || audioState.isPlaying) {
                                    requestInterstitialAd(
                                        placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                        route = "audio_stop_content",
                                        triggerKind = InterstitialTriggerKind.AUDIO_STOP,
                                    )
                                }
                                audioPlayerViewModel.stop()
                            },
                            onSeek = audioPlayerViewModel::seekTo,
                            onRetry = audioPlayerViewModel::retryAssetLoad,
                        )
                    },
                    nativeAdContent = {
                        NativeFeedAdSlot(
                            nativeAd = nativeAd,
                            nativePlacement = AdPlacement.NATIVE_FEED_CONTENT,
                            bannerAdUnitId = adUnitIds.banner,
                            bannerPlacement = AdPlacement.BANNER_CONTENT_DETAIL,
                            route = AppRoute.Content.route,
                        )
                    },
                    bannerAdUnitId = adUnitIds.banner,
                )
            }
            composable(AppRoute.PrayerList.route) {
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_HOME)
                }
                LaunchedEffect(Unit) {
                    audioPlayerViewModel.setOverrideAudioFileName(null)
                }
                PrayerListRoute(
                    onPrayerClick = { prayerId ->
                        if (hostActivity == null) {
                            navController.navigate(AppRoute.PrayerDetail.createRoute(prayerId))
                        } else {
                            requestInterstitialAd(
                                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                route = AppRoute.PrayerList.route,
                                triggerKind = InterstitialTriggerKind.NAV_BREAK,
                                onAdDismissed = {
                                    navController.navigate(
                                        AppRoute.PrayerDetail.createRoute(prayerId),
                                    )
                                },
                            )
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onRewardsClick = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                    showVerseCount = showVerseCount,
                    bannerAdUnitId = adUnitIds.banner,
                ) {
                    NativeFeedAdSlot(
                        nativeAd = nativeAd,
                        nativePlacement = AdPlacement.NATIVE_FEED_CONTENT,
                        bannerAdUnitId = adUnitIds.banner,
                        bannerPlacement = AdPlacement.BANNER_CONTENT_LIST,
                        route = AppRoute.PrayerList.route,
                    )
                }
            }
            composable(
                route = AppRoute.PrayerDetail.route,
                arguments = AppRoute.PrayerDetail.arguments,
            ) { backStackEntry ->
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_CONTENT)
                }
                PrayerDetailRoute(
                    onBackClick = { navController.popBackStack() },
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onRewardsClick = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                    onAudioFileChanged = { mediaKey ->
                        audioPlayerViewModel.setOverrideAudioFileName(mediaKey)
                    },
                    onModeChanged = { oldMode, newMode ->
                        if (isDisplayModeSwitch(oldMode, newMode)) {
                            requestInterstitialAd(
                                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                route = "mode_switch_prayer_detail",
                                triggerKind = InterstitialTriggerKind.MODE_SWITCH,
                            )
                        }
                    },
                    audioPlayerContent = {
                        InlineAudioPlayer(
                            state = audioState,
                            onPlayPause = {
                                if (audioState.isPlaying) {
                                    appAnalytics.logAudioPause(
                                        positionMs = audioState.currentPosition,
                                        durationMs = audioState.duration,
                                    )
                                    requestInterstitialAd(
                                        placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                        route = "audio_pause_prayer_detail",
                                        triggerKind = InterstitialTriggerKind.AUDIO_PAUSE,
                                    )
                                } else {
                                    hostActivity?.adOrchestrator?.updateSessionContext(
                                        audioPlayed = true,
                                    )
                                    appAnalytics.logAudioPlay(
                                        positionMs = audioState.currentPosition,
                                        durationMs = audioState.duration,
                                    )
                                }
                                audioPlayerViewModel.togglePlayPause()
                            },
                            onStop = {
                                appAnalytics.logAudioStop(
                                    positionMs = audioState.currentPosition,
                                    durationMs = audioState.duration,
                                )
                                if (audioState.currentPosition > 0L || audioState.isPlaying) {
                                    requestInterstitialAd(
                                        placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                        route = "audio_stop_prayer_detail",
                                        triggerKind = InterstitialTriggerKind.AUDIO_STOP,
                                    )
                                }
                                audioPlayerViewModel.stop()
                            },
                            onSeek = audioPlayerViewModel::seekTo,
                            onRetry = audioPlayerViewModel::retryAssetLoad,
                        )
                    },
                    nativeAdContent = {
                        NativeFeedAdSlot(
                            nativeAd = nativeAd,
                            nativePlacement = AdPlacement.NATIVE_FEED_CONTENT,
                            bannerAdUnitId = adUnitIds.banner,
                            bannerPlacement = AdPlacement.BANNER_CONTENT_DETAIL,
                            route = AppRoute.PrayerDetail.route.substringBefore("/{"),
                        )
                    },
                    isDebug = useTestAds,
                )
            }
            composable(AppRoute.MiraclesList.route) {
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_HOME)
                }
                if (isEsmaFlavor) {
                    LaunchedEffect(Unit) {
                        // Esma listesi için flavor default sesine dön.
                        audioPlayerViewModel.setOverrideAudioFileName(null)
                    }
                }
                MiraclesListRoute(
                    onPrayerClick = { prayerIndex ->
                        if (hostActivity == null) {
                            navController.navigate(AppRoute.MiraclesDetail.createRoute(prayerIndex))
                        } else {
                            requestInterstitialAd(
                                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                                route = AppRoute.MiraclesList.route,
                                triggerKind = InterstitialTriggerKind.NAV_BREAK,
                                onAdDismissed = {
                                    navController.navigate(
                                        AppRoute.MiraclesDetail.createRoute(prayerIndex),
                                    )
                                },
                            )
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onRewardsClick = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                    nativeAdContent = {
                        NativeFeedAdSlot(
                            nativeAd = nativeAd,
                            nativePlacement = AdPlacement.NATIVE_FEED_CONTENT,
                            bannerAdUnitId = adUnitIds.banner,
                            bannerPlacement = AdPlacement.BANNER_CONTENT_LIST,
                            route = AppRoute.MiraclesList.route,
                        )
                    },
                    audioPlayerContent =
                        if (isEsmaFlavor) {
                            {
                                InlineAudioPlayer(
                                    state = audioState,
                                    onPlayPause = audioPlayerViewModel::togglePlayPause,
                                    onStop = audioPlayerViewModel::stop,
                                    onSeek = audioPlayerViewModel::seekTo,
                                    onRetry = audioPlayerViewModel::retryAssetLoad,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            null
                        },
                    onPlayAllAudioClick =
                        if (isEsmaFlavor) {
                            {
                                hostActivity?.adOrchestrator?.updateSessionContext(
                                    audioPlayed = true,
                                )
                                audioPlayerViewModel.setOverrideAudioFileName(null)
                                audioPlayerViewModel.play()
                            }
                        } else {
                            null
                        },
                    bannerAdUnitId = adUnitIds.banner,
                    variant =
                        if (isEsmaFlavor) {
                            MiraclesContentVariant.ESMAUL_HUSNA
                        } else {
                            MiraclesContentVariant.MUCIZEDUALAR
                        },
                    headerTitle = stringResource(com.parsfilo.contentapp.R.string.app_name),
                )
            }
            composable(
                route = AppRoute.MiraclesDetail.route,
                arguments = AppRoute.MiraclesDetail.arguments,
            ) { backStackEntry ->
                LaunchedEffect(Unit) {
                    nativeAdViewModel.setPlacement(AdPlacement.NATIVE_FEED_CONTENT)
                }
                MiraclesDetailRoute(
                    onBackClick = { navController.popBackStack() },
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onRewardsClick = {
                        navController.navigate(AppRoute.Rewards.route)
                    },
                    variant =
                        if (isEsmaFlavor) {
                            MiraclesContentVariant.ESMAUL_HUSNA
                        } else {
                            MiraclesContentVariant.MUCIZEDUALAR
                        },
                ) {
                    NativeFeedAdSlot(
                        nativeAd = nativeAd,
                        nativePlacement = AdPlacement.NATIVE_FEED_CONTENT,
                        bannerAdUnitId = adUnitIds.banner,
                        bannerPlacement = AdPlacement.BANNER_CONTENT_DETAIL,
                        route = AppRoute.MiraclesDetail.route.substringBefore("/{"),
                    )
                }
            }
            composable(AppRoute.Settings.route) {
                SettingsRoute(
                    onBackClick = { navController.popBackStack() },
                    onPrivacyOptionsUpdated = onPrivacyOptionsUpdated,
                    updateDebugSummary = updateDebugSummary,
                    onUpdateDebugFetchNow = onUpdateDebugFetchNow,
                    onUpdateDebugSimulateSoft = onUpdateDebugSimulateSoft,
                    onUpdateDebugSimulateHard = onUpdateDebugSimulateHard,
                    onUpdateDebugClearSimulation = onUpdateDebugClearSimulation,
                    onUpdateDebugResetSoftPrompt = onUpdateDebugResetSoftPrompt,
                )
            }
            composable(AppRoute.Rewards.route) {
                RewardsRoute(
                    onBackClick = { navController.popBackStack() },
                )
            }
        }

        // Subscription Tab
        composable(AppRoute.Subscription.route) {
            SubscriptionRoute(onBackClick = { navController.popBackStack() })
        }

        // Other Apps Tab
        composable(AppRoute.OtherApps.route) {
            OtherAppsRoute()
        }

        // Messages Graph
        navigation(
            route = AppRoute.MessagesGraph.route,
            startDestination = if (isUserSignedIn) AppRoute.MessageList.route else AppRoute.Auth.route,
        ) {
            composable(AppRoute.MessageList.route) {
                MessagesRoute()
            }
            composable(AppRoute.Auth.route) {
                AuthRoute(
                    onSignInSuccess = {
                        navController.navigate(AppRoute.MessageList.route) {
                            popUpTo(AppRoute.Auth.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = AppRoute.MessageDetail.route,
                arguments = AppRoute.MessageDetail.arguments,
            ) { backStackEntry ->
                val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
                MessageDetailRoute(
                    messageId = messageId,
                    onBackClick = { navController.popBackStack() },
                )
            }
        }

        // Notifications Graph
        navigation(
            route = AppRoute.NotificationsGraph.route,
            startDestination = AppRoute.NotificationList.route,
        ) {
            composable(AppRoute.NotificationList.route) {
                NotificationsRoute()
            }
            composable(
                route = AppRoute.NotificationDetail.route,
                arguments = AppRoute.NotificationDetail.arguments,
            ) { backStackEntry ->
                val notificationId = backStackEntry.arguments?.getLong("notificationId") ?: 0L
                NotificationDetailRoute(
                    notificationId = notificationId,
                    onBackClick = { navController.popBackStack() },
                )
            }
        }
    }

    rewardedInterstitialIntroRequest?.let { request ->
        RewardedInterstitialIntroDialog(
            spec = request.spec,
            onConfirm = {
                val activity = hostActivity ?: return@RewardedInterstitialIntroDialog
                val launchToken =
                    activity.adOrchestrator.confirmRewardedInterstitialIntro(
                        placement = request.placement,
                        route = request.route,
                        adUnitId = request.adUnitId,
                    )
                clearRewardedInterstitialIntroRequest()
                coroutineScope.launch {
                    activity.adOrchestrator.showRewardedInterstitialIfEligible(
                        activity = activity,
                        launchToken = launchToken,
                        placement = request.placement,
                        route = request.route,
                        onUserEarnedReward = request.onRewardEarned,
                    )
                }
            },
            onDismiss = {
                hostActivity?.adOrchestrator?.onRewardedInterstitialIntroSkipped(
                    placement = request.placement,
                    route = request.route,
                    adUnitId = request.adUnitId,
                )
                clearRewardedInterstitialIntroRequest()
            },
        )
    }
}

private fun resolveHomeStartDestination(productDefinition: AppProductDefinition): String =
    when {
        productDefinition.isPrayerTimesFlavor -> AppRoute.PrayerTimesHome.route
        productDefinition.contentFamily == ContentFamily.QIBLA -> AppRoute.Qibla.route
        productDefinition.contentFamily == ContentFamily.PRAYER_LIBRARY -> AppRoute.PrayerList.route
        productDefinition.contentFamily == ContentFamily.MIRACLES ||
            productDefinition.contentFamily == ContentFamily.ESMA -> AppRoute.MiraclesList.route
        productDefinition.contentFamily == ContentFamily.ZIKIR_COUNTER -> AppRoute.ZikirCounter.route
        productDefinition.contentFamily == ContentFamily.QURAN -> AppRoute.QuranSuraList.route
        else -> AppRoute.Content.route
    }

private fun routeToContentType(route: String): String =
    when {
        route.startsWith(AppRoute.QuranSuraDetail.route.substringBefore("/{")) ||
            route.startsWith(AppRoute.QuranSuraList.route) ||
            route.startsWith(AppRoute.QuranBookmarks.route) -> "quran"
        route.startsWith(AppRoute.PrayerTimesHome.route) ||
            route.startsWith(AppRoute.PrayerList.route) ||
            route.startsWith(AppRoute.PrayerDetail.route.substringBefore("/{")) -> "prayer"
        route.startsWith(AppRoute.ZikirCounter.route) -> "zikir"
        route.startsWith(AppRoute.MiraclesList.route) ||
            route.startsWith(AppRoute.MiraclesDetail.route.substringBefore("/{")) -> "miracle"
        route.startsWith(AppRoute.Content.route) -> "content"
        route.startsWith(AppRoute.Qibla.route) -> "qibla"
        else -> "other"
    }

private fun isDisplayModeSwitch(
    oldMode: DisplayMode,
    newMode: DisplayMode,
): Boolean = oldMode != newMode

private data class RewardedInterstitialIntroRequest(
    val placement: AdPlacement,
    val route: String,
    val adUnitId: String,
    val spec: RewardedInterstitialIntroSpec,
    val onRewardEarned: () -> Unit,
)
