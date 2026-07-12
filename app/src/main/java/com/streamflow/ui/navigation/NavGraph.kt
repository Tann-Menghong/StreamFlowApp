package com.streamflow.ui.navigation

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.streamflow.ui.shorts.ShortsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import android.content.ComponentName
import kotlinx.coroutines.flow.first

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",    Icons.Rounded.Home)
    object Search   : Screen("search",   "Search",  Icons.Rounded.Search)
    object Donghua  : Screen("donghua",  "Donghua", Icons.Rounded.LiveTv)
    object Library  : Screen("library",  "Library", Icons.Rounded.VideoLibrary)
    object Settings : Screen("settings", "Settings",Icons.Rounded.Settings)
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
}

private val allBottomRoutes = listOf(
    Screen.Home.route, Screen.Search.route, Screen.Donghua.route,
    Screen.Library.route, Screen.Settings.route
)

@Composable
fun NavGraph(startUrl: String? = null, startDest: String? = null) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentDest = entry?.destination
    var isDonghuaFullscreen by remember { mutableStateOf(false) }
    val showBottom = allBottomRoutes.any { it == currentDest?.route } && !isDonghuaFullscreen

    val miniState by MiniPlayerState.data.collectAsState()
    val isOnPlayerScreen = currentDest?.route?.startsWith("player") == true
    val showMini = miniState.url.isNotEmpty() && !isOnPlayerScreen && showBottom

    // Mini player MediaController
    val context = LocalContext.current
    val appPrefs = remember { com.streamflow.data.local.AppPreferences.get(context) }
    val uiLang by appPrefs.language.collectAsState(initial = "EN")
    val showDonghua by appPrefs.showDonghua.collectAsState(initial = true)
    val showSearchTab by appPrefs.showSearchTab.collectAsState(initial = false)
    val navLabels by appPrefs.navLabels.collectAsState(initial = "SELECTED")
    val reduceMotion by appPrefs.reduceMotion.collectAsState(initial = false)
    val confirmExit by appPrefs.confirmExit.collectAsState(initial = false)

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
    val bottomItems = remember(showDonghua, showSearchTab) {
        buildList {
            add(Screen.Home)
            if (showSearchTab) add(Screen.Search)
            if (showDonghua) add(Screen.Donghua)
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
            future.cancel(false)
            miniMediaController?.release()
            miniMediaController = null
        }
    }

    // "What's New" dialog: shown once after every app update (not on fresh installs)
    var showWhatsNew by remember { mutableStateOf(false) }
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

    LaunchedEffect(startUrl) {
        if (startUrl != null) {
            // Pure playlist links open the playlist screen; watch links open the player
            if (startUrl.contains("/playlist") && startUrl.contains("list=")) {
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
        bottomBar = {
            AnimatedVisibility(
                visible = showBottom,
                enter = slideInVertically { it } + fadeIn(),
                exit  = slideOutVertically { it } + fadeOut()
            ) {
                Column {
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
            composable(Screen.Settings.route) { SettingsScreen() }
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
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) },
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
    lang: String = "EN",
    labelStyle: String = "SELECTED", // ALWAYS / SELECTED / NEVER
    reduceMotion: Boolean = false,
    onSelect: (Screen) -> Unit
) {
    // Floating pill nav: inset rounded bar matching the floating mini player
    Surface(
        color        = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val selected = current?.hierarchy?.any { it.route == screen.route } == true
                val iconScale by animateFloatAsState(
                    targetValue    = if (selected && !reduceMotion) 1.12f else 1f,
                    animationSpec  = if (reduceMotion) snap()
                        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label          = "nav_scale_${screen.label}"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(screen) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else          androidx.compose.ui.graphics.Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.label,
                            modifier = Modifier.size(22.dp).scale(iconScale),
                            tint = if (selected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
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
                        Text(
                            text  = com.streamflow.ui.theme.KmStrings.t(screen.label, lang),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
