package com.streamflow.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflow.ui.donghua.DonghuaScreen
import com.streamflow.ui.home.HomeScreen
import com.streamflow.ui.library.LibraryScreen
import com.streamflow.ui.player.PlayerScreen
import com.streamflow.ui.search.SearchScreen
import com.streamflow.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",    Icons.Default.Home)
    object Search   : Screen("search",   "Search",  Icons.Default.Search)
    object Donghua  : Screen("donghua",  "Donghua", Icons.Default.LiveTv)
    object Library  : Screen("library",  "Library", Icons.Default.VideoLibrary)
    object Settings : Screen("settings", "Settings",Icons.Default.Settings)
    object Player   : Screen("player/{videoUrl}", "Player", Icons.Default.PlayArrow) {
        fun createRoute(url: String) = "player/${URLEncoder.encode(url, "UTF-8")}"
    }
}

private val bottomItems = listOf(
    Screen.Home, Screen.Search, Screen.Donghua, Screen.Library, Screen.Settings
)

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val entry by navController.currentBackStackEntryAsState()
            val currentDest = entry?.destination
            val showBottom = bottomItems.any { it.route == currentDest?.route }
            if (showBottom) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomItems.forEach { screen ->
                        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) })
            }
            composable(Screen.Search.route) {
                SearchScreen(onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) })
            }
            composable(Screen.Donghua.route) {
                DonghuaScreen(onPlayNative = { navController.navigate(Screen.Player.createRoute(it)) })
            }
            composable(Screen.Library.route) {
                LibraryScreen(onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(navArgument("videoUrl") { type = NavType.StringType })
            ) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("videoUrl") ?: ""
                val videoUrl = URLDecoder.decode(encoded, "UTF-8")
                PlayerScreen(
                    videoUrl = videoUrl,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Screen.Player.createRoute(it)) }
                )
            }
        }
    }
}
