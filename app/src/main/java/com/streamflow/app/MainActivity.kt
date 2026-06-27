package com.streamflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.ui.navigation.Destinations
import com.streamflow.app.ui.navigation.StreamFlowNavGraph
import com.streamflow.app.ui.theme.StreamFlowTheme
import com.streamflow.app.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamFlowTheme {
                Surface {
                    StreamFlowApp()
                }
            }
        }
    }
}

private data class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Destinations.HOME, R.string.tab_home, Icons.Default.Home),
    BottomTab(Destinations.SEARCH, R.string.tab_search, Icons.Default.Search),
    BottomTab(Destinations.LIBRARY, R.string.tab_library, Icons.Default.LibraryBooks)
)

@Composable
private fun StreamFlowApp() {
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
    val showBottomBar = currentRoute != Destinations.VIDEO_DETAIL

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
    ) { padding ->
        StreamFlowNavGraph(
            navController = navController,
            modifier = Modifier.padding(padding),
            onCheckForUpdates = { updateViewModel.checkForUpdate(announceUpToDate = true) }
        )
    }
}
