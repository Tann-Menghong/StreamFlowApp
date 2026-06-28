package com.streamflow.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.player.PlayerUiState
import com.streamflow.app.ui.navigation.Destinations
import com.streamflow.app.ui.navigation.StreamFlowNavGraph
import com.streamflow.app.ui.navigation.navigateToVideo
import com.streamflow.app.ui.theme.StreamFlowTheme
import com.streamflow.app.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private var canEnterPip = false
    private var inPictureInPictureMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamFlowTheme {
                Surface {
                    StreamFlowApp(
                        isInPictureInPictureMode = inPictureInPictureMode,
                        onPipEligibilityChanged = { canEnterPip = it }
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (canEnterPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPictureMode = isInPictureInPictureMode
    }
}

private data class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Destinations.HOME, R.string.tab_home, Icons.Default.Home),
    BottomTab(Destinations.SEARCH, R.string.tab_search, Icons.Default.Search),
    BottomTab(Destinations.LIBRARY, R.string.tab_library, Icons.Default.LibraryBooks)
)

@Composable
private fun StreamFlowApp(
    isInPictureInPictureMode: Boolean = false,
    onPipEligibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val currentVersionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val updateViewModel: UpdateViewModel = viewModel(
        factory = viewModelFactory {
            initializer { UpdateViewModel(ServiceLocator.updateManager, currentVersionName) }
        }
    )
    val updateState by updateViewModel.state.collectAsState()

    LaunchedEffect(Unit) { updateViewModel.checkForUpdate() }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != Destinations.VIDEO_DETAIL && !isInPictureInPictureMode

    val playerState by ServiceLocator.playerController.state.collectAsState()
    LaunchedEffect(currentRoute, playerState.isPlaying) {
        onPipEligibilityChanged(currentRoute == Destinations.VIDEO_DETAIL && playerState.isPlaying)
    }

    val availableUpdate = updateState.available
    if (availableUpdate != null) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.update_available_message,
                        availableUpdate.versionName,
                        currentVersionName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { updateViewModel.startUpdate(context) }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = updateViewModel::dismiss) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }

    if (updateState.upToDate) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismissUpToDate,
            title = { Text(stringResource(R.string.check_for_updates)) },
            text = { Text(stringResource(R.string.up_to_date)) },
            confirmButton = {
                TextButton(onClick = updateViewModel::dismissUpToDate) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }

    val updateError = updateState.error
    if (updateError != null) {
        AlertDialog(
            onDismissRequest = updateViewModel::dismissError,
            title = { Text(stringResource(R.string.check_for_updates)) },
            text = { Text(updateError) },
            confirmButton = {
                TextButton(onClick = updateViewModel::dismissError) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }

    if (updateState.downloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.check_for_updates)) },
            text = { Text(stringResource(R.string.update_downloading)) },
            confirmButton = {}
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    if (playerState.videoPageUrl.isNotBlank()) {
                        MiniPlayer(
                            playerState = playerState,
                            onResume = { navController.navigateToVideo(playerState.videoPageUrl) },
                            onTogglePlayPause = { ServiceLocator.playerController.togglePlayPause() },
                            onClose = { ServiceLocator.playerController.stop() }
                        )
                    }
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        StreamFlowNavGraph(
            navController = navController,
            modifier = Modifier.padding(padding),
            onCheckForUpdates = { updateViewModel.checkForUpdate(announceUpToDate = true) },
            isInPictureInPictureMode = isInPictureInPictureMode
        )
    }
}

@Composable
private fun MiniPlayer(
    playerState: PlayerUiState,
    onResume: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit
) {
    val progress = if (playerState.durationMs > 0) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onResume)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = playerState.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                )
                Text(
                    text = playerState.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playerState.isPlaying) R.string.pause else R.string.play
                    )
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_player))
            }
        }
    }
}
