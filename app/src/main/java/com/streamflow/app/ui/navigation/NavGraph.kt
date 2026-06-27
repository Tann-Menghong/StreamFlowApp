package com.streamflow.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.streamflow.app.ui.home.HomeScreen
import com.streamflow.app.ui.library.LibraryScreen
import com.streamflow.app.ui.search.SearchScreen
import com.streamflow.app.ui.video.VideoDetailScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Destinations {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val VIDEO_DETAIL = "video/{videoUrl}"
}

fun NavHostController.navigateToVideo(url: String) {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
    navigate("video/$encoded")
}

@Composable
fun StreamFlowNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Destinations.HOME, modifier = modifier) {
        composable(Destinations.HOME) {
            HomeScreen(onVideoClick = { video -> navController.navigateToVideo(video.url) })
        }
        composable(Destinations.SEARCH) {
            SearchScreen(onVideoClick = { video -> navController.navigateToVideo(video.url) })
        }
        composable(Destinations.LIBRARY) {
            LibraryScreen(onVideoClick = { video -> navController.navigateToVideo(video.url) })
        }
        composable(
            route = Destinations.VIDEO_DETAIL,
            arguments = listOf(navArgument("videoUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("videoUrl").orEmpty()
            val videoUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
            VideoDetailScreen(
                videoUrl = videoUrl,
                onBack = { navController.popBackStack() },
                onVideoClick = { video -> navController.navigateToVideo(video.url) }
            )
        }
    }
}
