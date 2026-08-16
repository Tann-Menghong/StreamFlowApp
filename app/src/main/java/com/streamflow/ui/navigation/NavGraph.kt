package com.streamflow.ui.navigation

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflow.PlaybackService
import com.streamflow.ui.components.MiniPlayerBar
import com.streamflow.ui.components.MiniPlayerState
import com.streamflow.ui.channel.ChannelScreen
import com.streamflow.ui.donghua.DonghuaScreen
import com.streamflow.ui.feed.FeedScreen
import com.streamflow.ui.home.HomeScreen
import com.streamflow.ui.library.LibraryScreen
import com.streamflow.ui.player.PlayerScreen
import com.streamflow.ui.playlist.PlaylistDetailScreen
import com.streamflow.ui.playlist.RemotePlaylistScreen
import com.streamflow.ui.search.SearchScreen
import com.streamflow.ui.settings.SettingsScreen
import com.streamflow.ui.settings.SettingsCategoryScreen
import com.streamflow.ui.settings.SettingsViewModel
import com.streamflow.ui.shorts.ShortsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import android.content.ComponentName
import com.streamflow.ui.theme.appShape
import kotlinx.coroutines.flow.first

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",    Icons.Rounded.Home)
    object Search   : Screen("search",   "Search",  Icons.Rounded.Search)
    object Donghua  : Screen("donghua",  "Donghua", Icons.Rounded.LiveTv)
    object Drama    : Screen("drama",    "Drama",   Icons.Rounded.Theaters)
    object PdTv     : Screen("pdtv",     "PDTV",    Icons.Rounded.Movie)
    object Mkiss    : Screen("mkiss",    "MKissa",  Icons.Rounded.OndemandVideo)
    object Library  : Screen("library",  "Library", Icons.Rounded.VideoLibrary)
    object Settings : Screen("settings", "Settings",Icons.Rounded.Settings)
    object SettingsCategory : Screen("settings/{category}", "Settings", Icons.Rounded.Settings) {
        fun createRoute(category: String) = "settings/${URLEncoder.encode(category, "UTF-8")}"
    }
    object Player   : Screen("player?videoUrl={videoUrl}", "Player", Icons.Rounded.PlayArrow) {
        fun createRoute(url: String) = "player?videoUrl=${URLEncoder.encode(url, "UTF-8")}"
    }
    object Channel  : Screen("channel?channelUrl={channelUrl}", "Channel", Icons.Rounded.AccountCircle) {
        fun createRoute(url: String) = "channel?channelUrl=${URLEncoder.encode(url, "UTF-8")}"
    }
    object Feed     : Screen("feed", "Feed", Icons.Rounded.Subscriptions)
    object LocalPlaylist : Screen("localplaylist/{playlistId}", "Playlist", Icons.Rounded.PlaylistPlay) {
        fun createRoute(id: Long) = "localplaylist/$id"
    }
    object YtPlaylist : Screen("ytplaylist?url={url}", "YouTube Playlist", Icons.Rounded.PlaylistPlay) {
        fun createRoute(url: String) = "ytplaylist?url=${URLEncoder.encode(url, "UTF-8")}"
    }
    object Shorts : Screen("shortsfeed", "Shorts", Icons.Rounded.SlowMotionVideo)

    /**
     * A user-added website tab. Unlike the built-ins this is a data class, not an
     * object: label, icon and target URL all come from the database row, so each
     * configured tab is its own Screen instance.
     */
    data class Custom(val id: Long, val title: String, val iconVector: ImageVector) :
        Screen("customtab/$id", title, iconVector) {
        companion object {
            const val ROUTE_PATTERN = "customtab/{tabId}"
            fun createRoute(id: Long) = "customtab/$id"
        }
    }
}

private val allBottomRoutes = listOf(
    Screen.Home.route, Screen.Search.route, Screen.Donghua.route, Screen.Drama.route,
    Screen.PdTv.route, Screen.Mkiss.route, Screen.Library.route, Screen.Settings.route
)

// Custom tabs are part of the bottom-bar set too, but their routes are dynamic,
// so they're matched by prefix rather than by membership in the fixed list.
private fun isBottomRoute(route: String?): Boolean =
    route != null && (allBottomRoutes.contains(route) || route.startsWith("customtab/"))

@Composable
fun NavGraph(startUrl: String? = null, startDest: String? = null, intentNonce: Int = 0) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentDest = entry?.destination
    var isDonghuaFullscreen by remember { mutableStateOf(false) }
    val showBottom = isBottomRoute(currentDest?.route) && !isDonghuaFullscreen

    val miniState by MiniPlayerState.data.collectAsState()
    val isOnPlayerScreen = currentDest?.route?.startsWith("player") == true
    val showMini = miniState.url.isNotEmpty() && !isOnPlayerScreen && showBottom

    // Mini player MediaController
    val context = LocalContext.current
    val appPrefs = remember { com.streamflow.data.local.AppPreferences.get(context) }
    val uiLang by appPrefs.language.collectAsState(initial = "EN")
    val showDonghua by appPrefs.showDonghua.collectAsState(initial = true)
    val showDrama by appPrefs.showDrama.collectAsState(initial = true)
    val showPdTv by appPrefs.showPdTv.collectAsState(initial = true)
    val showMkiss by appPrefs.showMkiss.collectAsState(initial = true)
    val showSearchTab by appPrefs.showSearchTab.collectAsState(initial = false)
    val navLabels by appPrefs.navLabels.collectAsState(initial = "SELECTED")
    val reduceMotion by appPrefs.reduceMotion.collectAsState(initial = false)
    val confirmExit by appPrefs.confirmExit.collectAsState(initial = false)
    val isOnline by com.streamflow.data.ConnectivityMonitor.online.collectAsState()

    // Double-back to exit (optional, Settings > Appearance)
    var lastBackAt by remember { mutableStateOf(0L) }
    val activity = context as? android.app.Activity
    androidx.activity.compose.BackHandler(
        enabled = confirmExit && currentDest?.route == Screen.Home.route
    ) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2200) activity?.finish()
        else {
            lastBackAt = now
            android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // User-added website tabs, live from the database so adding one in Settings
    // shows up in the bar immediately without an app restart.
    val customTabs by remember {
        com.streamflow.data.local.AppDatabase.get(context).customTabDao().getAll()
    }.collectAsState(initial = emptyList())

    val bottomItems = remember(showDonghua, showDrama, showPdTv, showMkiss, showSearchTab, customTabs) {
        buildList {
            add(Screen.Home)
            if (showSearchTab) add(Screen.Search)
            if (showDonghua) add(Screen.Donghua)
            if (showDrama) add(Screen.Drama)
            if (showPdTv) add(Screen.PdTv)
            if (showMkiss) add(Screen.Mkiss)
            // Custom tabs sit with the other site tabs, before Library/Settings,
            // which stay anchored at the end where users expect them.
            customTabs.forEach { t ->
                add(Screen.Custom(t.id, t.title, com.streamflow.data.CustomTabs.iconFor(t.iconKey)))
            }
            add(Screen.Library)
            add(Screen.Settings)
        }
    }
    var miniMediaController by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try { miniMediaController = future.get() } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            miniMediaController = null
            // releaseFuture handles the race where the controller finishes
            // building after this dispose — cancel(false) + release() could
            // leak a connected controller in that window
            MediaController.releaseFuture(future)
        }
    }

    // "What's New" dialog: shown once after every app update (not on fresh installs).
    // Saveable: lastSeenVersion is written immediately below, so with plain
    // remember a rotation while the dialog was open dismissed it forever
    var showWhatsNew by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val lastSeen = appPrefs.lastSeenVersion.first()
        val current = com.streamflow.BuildConfig.VERSION_CODE
        if (lastSeen in 1 until current) showWhatsNew = true
        if (lastSeen != current) appPrefs.setLastSeenVersion(current)
    }
    if (showWhatsNew) {
        AlertDialog(
            onDismissRequest = { showWhatsNew = false },
            title = {
                Column {
                    Text("What's new", fontWeight = FontWeight.Bold)
                    Text("Version ${com.streamflow.data.Changelog.VERSION_NAME}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.streamflow.data.Changelog.notes.forEach { note ->
                        Row(Modifier.padding(vertical = 5.dp)) {
                            Text("•  ", color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Text(note, fontSize = 13.sp, lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhatsNew = false }) { Text("Got it") }
            }
        )
    }

    // All three keys: with singleTask, onNewIntent updates these while the app
    // runs — a shortcut after a shared link (or vice versa) must re-navigate.
    // The nonce covers repeats: sharing the SAME url twice left both string
    // keys unchanged, so the effect never re-fired and the second share did
    // nothing. MainActivity bumps the nonce on every routed intent.
    LaunchedEffect(startUrl, startDest, intentNonce) {
        if (startUrl != null) {
            // Channel links (incl. launcher shortcuts) open the channel screen;
            // pure playlist links the playlist screen; watch links the player
            val isChannelUrl = listOf("/channel/", "/@", "/c/", "/user/").any { startUrl.contains(it) } &&
                !startUrl.contains("watch?") && !startUrl.contains("/shorts/")
            if (isChannelUrl) {
                navController.navigate(Screen.Channel.createRoute(startUrl))
            } else if (startUrl.contains("/playlist") && startUrl.contains("list=")) {
                navController.navigate(Screen.YtPlaylist.createRoute(startUrl))
            } else {
                navController.navigate(Screen.Player.createRoute(startUrl))
            }
        } else if (startDest != null) {
            // Launcher shortcut destination
            navController.navigate(startDest) { launchSingleTop = true }
        } else {
            // User-chosen start screen (Home stays the back-stack root)
            val tab = appPrefs.startTab.first()
            if (tab != "home" && allBottomRoutes.contains(tab)) {
                navController.navigate(tab) { launchSingleTop = true }
            }
        }
    }

    Scaffold(
        // Zero insets here: every screen's own TopAppBar already applies the
        // status-bar inset, and the default (systemBars) made this Scaffold's
        // innerPadding add it AGAIN — pushing all content down by a status-bar
        // height and leaving a black band under the clock ("app not fullscreen").
        // The nav bar handles its own navigationBarsPadding.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottom,
                enter = slideInVertically { it } + fadeIn(),
                exit  = slideOutVertically { it } + fadeOut()
            ) {
                Column {
                    // Offline banner, above everything else in the bottom stack.
                    // Slides in only when the connection is actually gone, so it
                    // never occupies space in the normal case. Says what still
                    // works rather than just reporting a failure — downloads and
                    // the library are fully usable offline.
                    AnimatedVisibility(
                        visible = !isOnline,
                        enter = slideInVertically { it } + fadeIn(tween(200)),
                        exit  = slideOutVertically { it } + fadeOut(tween(150))
                    ) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.CloudOff, null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "You're offline — downloads and your library still work",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    // Mini player bar above nav bar
                    AnimatedVisibility(
                        visible = showMini,
                        enter = slideInVertically { it } + fadeIn(tween(200)),
                        exit  = slideOutVertically { it } + fadeOut(tween(150))
                    ) {
                        MiniPlayerBar(
                            data = miniState,
                            mediaController = miniMediaController,
                            onNavigateToPlayer = { url ->
                                navController.navigate(Screen.Player.createRoute(url)) {
                                    launchSingleTop = true
                                }
                            },
                            onDismiss = { MiniPlayerState.clear() }
                        )
                    }
                    AnimatedNavBar(
                        items    = bottomItems,
                        current  = currentDest,
                        currentCustomTabId =
                            if (currentDest?.route == Screen.Custom.ROUTE_PATTERN)
                                entry?.arguments?.getLong("tabId") else null,
                        lang     = uiLang,
                        labelStyle   = navLabels,
                        reduceMotion = reduceMotion,
                        onSelect = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home.route,
            modifier         = if (showBottom) Modifier.padding(innerPadding) else Modifier.fillMaxSize(),
            // Telegram-style: screens slide in from the right with a subtle
            // parallax push, and slide back out when popping
            enterTransition  = {
                if (reduceMotion) fadeIn(tween(120))
                else fadeIn(tween(220)) +
                    slideInHorizontally(tween(260, easing = EaseInOut)) { it / 4 }
            },
            exitTransition   = {
                if (reduceMotion) fadeOut(tween(100))
                else fadeOut(tween(180)) +
                    slideOutHorizontally(tween(260, easing = EaseInOut)) { -it / 8 }
            },
            popEnterTransition  = {
                if (reduceMotion) fadeIn(tween(120))
                else fadeIn(tween(220)) +
                    slideInHorizontally(tween(260, easing = EaseInOut)) { -it / 8 }
            },
            popExitTransition   = {
                if (reduceMotion) fadeOut(tween(100))
                else fadeOut(tween(180)) +
                    slideOutHorizontally(tween(260, easing = EaseInOut)) { it / 4 }
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) },
                    onChannelClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.Channel.createRoute(url))
                    },
                    onPlaylistClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.YtPlaylist.createRoute(url))
                    },
                    onShortsClick = { navController.navigate(Screen.Shorts.route) }
                )
            }
            composable(Screen.Shorts.route) {
                // Shorts has its own player — silence any background playback.
                // Keyed on the controller: it connects asynchronously, so an
                // Unit-keyed effect could run while it is still null.
                LaunchedEffect(miniMediaController) { miniMediaController?.pause() }
                ShortsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenInPlayer = { navController.navigate(Screen.Player.createRoute(it)) },
                    onChannelClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.Channel.createRoute(url))
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) })
            }
            composable(Screen.Donghua.route) {
                DonghuaScreen(onFullscreenChange = { isDonghuaFullscreen = it })
            }
            composable(Screen.Drama.route) {
                com.streamflow.ui.browser.AdblockBrowserScreen(
                    homeUrl = "https://kisskh.co/",
                    prefsName = "kisskh_prefs",
                    defaultTitle = "KissKH",
                    onFullscreenChange = { isDonghuaFullscreen = it }
                )
            }
            // ── User-added website tabs ───────────────────────────────────────
            // Same ad-blocking browser as the built-in site tabs, so a custom tab
            // gets the pop-up blocking, fullscreen handling and shield for free.
            composable(
                Screen.Custom.ROUTE_PATTERN,
                arguments = listOf(navArgument("tabId") { type = NavType.LongType })
            ) { backStackEntry ->
                val tabId = backStackEntry.arguments?.getLong("tabId") ?: 0L
                val tab = customTabs.firstOrNull { it.id == tabId }
                if (tab == null) {
                    // The tab was deleted while it was open. Fall back to Home
                    // rather than rendering an empty browser pointed at nothing.
                    LaunchedEffect(tabId) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                } else {
                    com.streamflow.ui.browser.AdblockBrowserScreen(
                        homeUrl = tab.url,
                        prefsName = com.streamflow.data.CustomTabs.prefsNameFor(tab.id),
                        defaultTitle = tab.title,
                        onFullscreenChange = { isDonghuaFullscreen = it }
                    )
                }
            }
            composable(Screen.PdTv.route) {
                // Native live-TV player (the site's own web player doesn't
                // survive a WebView) — parses the channel list, plays via ExoPlayer
                com.streamflow.ui.pdtv.PdTvScreen(
                    onFullscreenChange = { isDonghuaFullscreen = it }
                )
            }
            composable(Screen.Mkiss.route) {
                com.streamflow.ui.browser.AdblockBrowserScreen(
                    homeUrl = "https://mkissa.to/",
                    prefsName = "mkissa_prefs",
                    defaultTitle = "MKissa",
                    onFullscreenChange = { isDonghuaFullscreen = it }
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) },
                    onChannelClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.Channel.createRoute(url))
                    },
                    onFeedClick = { navController.navigate(Screen.Feed.route) },
                    onPlaylistClick = { id -> navController.navigate(Screen.LocalPlaylist.createRoute(id)) }
                )
            }
            composable(
                route = Screen.LocalPlaylist.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { back ->
                PlaylistDetailScreen(
                    playlistId = back.arguments?.getLong("playlistId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) }
                )
            }
            composable(
                route = Screen.YtPlaylist.route,
                arguments = listOf(navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                })
            ) { back ->
                val url = URLDecoder.decode(back.arguments?.getString("url") ?: "", "UTF-8")
                RemotePlaylistScreen(
                    playlistUrl = url,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) }
                )
            }
            composable(Screen.Feed.route) {
                FeedScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) },
                    onChannelClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.Channel.createRoute(url))
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onCategoryClick = { category ->
                    navController.navigate(Screen.SettingsCategory.createRoute(category))
                })
            }
            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                })
            ) { back ->
                val category = URLDecoder.decode(back.arguments?.getString("category") ?: "", "UTF-8")
                // Share the dashboard's own SettingsViewModel instance (rather than
                // creating a second one) so state stays in sync and "check for
                // update" / AI-state refresh don't fire twice per visit
                val parentEntry = remember(back) { navController.getBackStackEntry(Screen.Settings.route) }
                val sharedVm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(parentEntry)
                SettingsCategoryScreen(
                    category = category,
                    onBack   = { navController.popBackStack() },
                    vm       = sharedVm
                )
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(navArgument("videoUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                })
            ) { back ->
                val videoUrl = URLDecoder.decode(back.arguments?.getString("videoUrl") ?: "", "UTF-8")
                PlayerScreen(
                    videoUrl     = videoUrl,
                    onBack       = { navController.popBackStack() },
                    onVideoClick = { url ->
                        // Replace, don't push: this is Player -> Player navigation
                        // (related video / autoplay / "up next" queue). Pushing a
                        // new entry per video let a long autoplay session grow the
                        // back stack — and its ViewModels — without bound, and
                        // turned the back button into "step through every video
                        // I've watched this session" instead of returning to
                        // wherever playback started.
                        navController.navigate(Screen.Player.createRoute(url)) {
                            popUpTo(Screen.Player.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onChannelClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.Channel.createRoute(url))
                    }
                )
            }
            composable(
                route = Screen.Channel.route,
                arguments = listOf(navArgument("channelUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                })
            ) { back ->
                val channelUrl = URLDecoder.decode(back.arguments?.getString("channelUrl") ?: "", "UTF-8")
                ChannelScreen(
                    channelUrl  = channelUrl,
                    onBack      = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) },
                    onChannelClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.Channel.createRoute(url))
                    },
                    onPlaylistClick = { url ->
                        if (url.isNotEmpty()) navController.navigate(Screen.YtPlaylist.createRoute(url))
                    }
                )
            }
        }
    }
}

@Composable
private fun AnimatedNavBar(
    items: List<Screen>,
    current: androidx.navigation.NavDestination?,
    /**
     * Which custom tab is open, if any.
     *
     * Needed because a custom tab's Screen.route is the CONCRETE path
     * ("customtab/7") while the back-stack destination carries the PATTERN
     * ("customtab/{tabId}"). Comparing those two strings never matches, so
     * without the id the tab would never highlight as selected.
     */
    currentCustomTabId: Long? = null,
    lang: String = "EN",
    labelStyle: String = "SELECTED", // ALWAYS / SELECTED / NEVER
    reduceMotion: Boolean = false,
    onSelect: (Screen) -> Unit
) {
    // MODERN: floating pill nav; AURORA: the pill gets a gradient hairline
    // border (glass look); CLASSIC: original full-width bar
    val designStyle = com.streamflow.ui.theme.LocalDesignStyle.current
    val terminalStyle = designStyle == "TERMINAL"
    val modernStyle = designStyle != "CLASSIC" && !terminalStyle
    // Read outside drawBehind — MaterialTheme is not readable from a draw scope.
    val terminalNavRule = MaterialTheme.colorScheme.outline
    Surface(
        color        = MaterialTheme.colorScheme.surface,
        // No depth in TERMINAL — the design system forbids shadows, so the bar
        // is separated by a top border rule instead of by elevation.
        tonalElevation = if (terminalStyle) 0.dp else 4.dp,
        shadowElevation = if (terminalStyle) 0.dp else 10.dp,
        border = if (designStyle == "AURORA")
            androidx.compose.foundation.BorderStroke(1.dp,
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primary.copy(0.55f),
                    MaterialTheme.colorScheme.tertiary.copy(0.35f))))
        else null,
        shape = if (modernStyle) appShape(26.dp)
                else androidx.compose.ui.graphics.RectangleShape,
        modifier = Modifier
            .navigationBarsPadding()
            .then(
                when {
                    terminalStyle -> Modifier.drawBehind {
                        // A single phosphor rule across the top edge — the split
                        // between two tmux panes, not a floating surface.
                        drawLine(
                            color = terminalNavRule,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 2f
                        )
                    }
                    modernStyle -> Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)
                    else -> Modifier
                }
            )
    ) {
        // With the built-ins plus up to five custom tabs the bar can hold more
        // items than a phone width can share out. Past six, stop dividing the
        // width (which squeezes icons into each other and makes labels unreadable)
        // and scroll fixed-width items instead.
        val crowded = items.size > 6
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (crowded) Modifier.horizontalScroll(rememberScrollState())
                    else Modifier
                )
                .height(if (terminalStyle) 52.dp else 60.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = if (crowded) Arrangement.Start else Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val selected = if (screen is Screen.Custom) {
                    current?.route == Screen.Custom.ROUTE_PATTERN &&
                        currentCustomTabId == screen.id
                } else {
                    current?.hierarchy?.any { it.route == screen.route } == true
                }
                val iconScale by animateFloatAsState(
                    targetValue    = if (selected && !reduceMotion) 1.12f else 1f,
                    animationSpec  = if (reduceMotion) snap()
                        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label          = "nav_scale_${screen.label}"
                )
                Column(
                    modifier = (if (crowded) Modifier.width(76.dp) else Modifier.weight(1f))
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(screen) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // TERMINAL: the selected tab is INVERTED VIDEO — a solid
                    // phosphor block with a black glyph. A 16%-alpha tint is a
                    // GUI convention and reads as washed-out on this palette;
                    // inversion is how a terminal shows selection, and it is a
                    // far stronger focus indicator.
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 32.dp)
                            .clip(appShape(if (terminalStyle) 0.dp else 12.dp))
                            .background(
                                when {
                                    selected && terminalStyle -> MaterialTheme.colorScheme.primary
                                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.label,
                            modifier = Modifier.size(if (terminalStyle) 18.dp else 22.dp)
                                .scale(if (terminalStyle) 1f else iconScale),
                            tint = when {
                                selected && terminalStyle -> MaterialTheme.colorScheme.background
                                selected -> MaterialTheme.colorScheme.primary
                                terminalStyle -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            }
                        )
                    }
                    val labelVisible = when (labelStyle) {
                        "ALWAYS" -> true
                        "NEVER"  -> false
                        else     -> selected
                    }
                    AnimatedVisibility(
                        visible = labelVisible,
                        enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                        exit  = fadeOut(tween(120)) + shrinkVertically(tween(120))
                    ) {
                        val navLabel = com.streamflow.ui.theme.KmStrings.t(screen.label, lang)
                        Text(
                            // Shell tabs are lower-case commands, not Title Case.
                            text  = if (terminalStyle) navLabel.lowercase() else navLabel,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                    else if (terminalStyle) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
