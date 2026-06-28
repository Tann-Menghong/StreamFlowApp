package com.streamflow.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.streamflow.app.ui.channel.ChannelDetailScreen
import com.streamflow.app.ui.home.HomeScreen
import com.streamflow.app.ui.library.LibraryScreen
import com.streamflow.app.ui.playlist.PlaylistDetailScreen
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
    const val CHANNEL = "channel/{channelUrl}"
    const val PLAYLIST = "playlist/{playlistUrl}"
}

fun NavHostController.navigateToVideo(url: String) {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
    navigate("video/$encoded")
}

fun NavHostController.navigateToChannel(url: String) {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
    navigate("channel/$encoded")
}

fun NavHostController.navigateToPlaylist(url: String) {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
    navigate("playlist/$encoded")
}

@Composable
fun StreamFlowNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onCheckForUpdates: () -> Unit = {},
    isInPictureInPictureMode: Boolean = false
) {
    NavHost(navController = navController, startDestination = Destinations.HOME, modifier = modifier) {
        composable(Destinations.HOME) {
            HomeScreen(
                onVideoClick = { video -> navController.navigateToVideo(video.url) },
                onChannelClick = { url -> navController.navigateToChannel(url) }
            )
        }
        composable(Destinations.SEARCH) {
            SearchScreen(
                onVideoClick = { video -> navController.navigateToVideo(video.url) },
                onChannelClick = { url -> navController.navigateToChannel(url) }
            )
        }
        composable(Destinations.LIBRARY) {
            LibraryScreen(
                onVideoClick = { video -> navController.navigateToVideo(video.url) },
                onChannelClick = { url -> navController.navigateToChannel(url) },
                onCheckForUpdates = onCheckForUpdates
            )
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
                onVideoClick = { video -> navController.navigateToVideo(video.url) },
                onChannelClick = { url -> navController.navigateToChannel(url) },
                isInPictureInPictureMode = isInPictureInPictureMode
            )
        }
        composable(
            route = Destinations.CHANNEL,
            arguments = listOf(navArgument("channelUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("channelUrl").orEmpty()
            val channelUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
            ChannelDetailScreen(
                channelUrl = channelUrl,
                onBack = { navController.popBackStack() },
                onVideoClick = { video -> navController.navigateToVideo(video.url) },
                onPlaylistClick = { url -> navController.navigateToPlaylist(url) }
            )
        }
        composable(
            route = Destinations.PLAYLIST,
            arguments = listOf(navArgument("playlistUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("playlistUrl").orEmpty()
            val playlistUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
            PlaylistDetailScreen(
                playlistUrl = playlistUrl,
                onBack = { navController.popBackStack() },
                onVideoClick = { video -> navController.navigateToVideo(video.url) },
                onChannelClick = { url -> navController.navigateToChannel(url) }
            )
        }
    }
}
