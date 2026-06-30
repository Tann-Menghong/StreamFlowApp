package com.streamflow.ui.navigation

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.streamflow.ui.donghua.DonghuaScreen
import com.streamflow.ui.home.HomeScreen
import com.streamflow.ui.library.LibraryScreen
import com.streamflow.ui.player.PlayerScreen
import com.streamflow.ui.search.SearchScreen
import com.streamflow.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import android.content.ComponentName

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",    Icons.Default.Home)
    object Search   : Screen("search",   "Search",  Icons.Default.Search)
    object Donghua  : Screen("donghua",  "Donghua", Icons.Default.LiveTv)
    object Library  : Screen("library",  "Library", Icons.Default.VideoLibrary)
    object Settings : Screen("settings", "Settings",Icons.Default.Settings)
    object Player   : Screen("player?videoUrl={videoUrl}", "Player", Icons.Default.PlayArrow) {
        fun createRoute(url: String) = "player?videoUrl=${URLEncoder.encode(url, "UTF-8")}"
    }
}

private val bottomItems = listOf(
    Screen.Home, Screen.Donghua, Screen.Library, Screen.Settings
)

@Composable
fun NavGraph(startUrl: String? = null) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentDest = entry?.destination
    var isDonghuaFullscreen by remember { mutableStateOf(false) }
    val showBottom = bottomItems.any { it.route == currentDest?.route } && !isDonghuaFullscreen

    val miniState by MiniPlayerState.data.collectAsState()
    val isOnPlayerScreen = currentDest?.route?.startsWith("player") == true
    val showMini = miniState.url.isNotEmpty() && !isOnPlayerScreen && showBottom

    // Mini player MediaController
    val context = LocalContext.current
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

    LaunchedEffect(startUrl) {
        if (startUrl != null) {
            navController.navigate(Screen.Player.createRoute(startUrl))
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
            enterTransition  = {
                fadeIn(tween(270, easing = EaseInOut)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(270, easing = EaseInOut))
            },
            exitTransition   = {
                fadeOut(tween(200, easing = EaseInOut))
            },
            popEnterTransition  = {
                fadeIn(tween(270)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(270))
            },
            popExitTransition   = {
                fadeOut(tween(200)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(200))
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) })
            }
            composable(Screen.Search.route) {
                SearchScreen(onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) })
            }
            composable(Screen.Donghua.route) {
                DonghuaScreen(onFullscreenChange = { isDonghuaFullscreen = it })
            }
            composable(Screen.Library.route) {
                LibraryScreen(onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) })
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
                    videoUrl    = videoUrl,
                    onBack      = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) }
                )
            }
        }
    }
}

@Composable
private fun AnimatedNavBar(
    items: List<Screen>,
    current: androidx.navigation.NavDestination?,
    onSelect: (Screen) -> Unit
) {
    Surface(
        color        = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val selected = current?.hierarchy?.any { it.route == screen.route } == true
                val iconScale by animateFloatAsState(
                    targetValue    = if (selected) 1.12f else 1f,
                    animationSpec  = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                        exit  = fadeOut(tween(120)) + shrinkVertically(tween(120))
                    ) {
                        Text(
                            text  = screen.label,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
