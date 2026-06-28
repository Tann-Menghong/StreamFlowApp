package com.streamflow.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val theme by vm.theme.collectAsState()
    val quality by vm.quality.collectAsState()
    val autoPlay by vm.autoPlay.collectAsState()
    val dataSaver by vm.dataSaver.collectAsState()
    val favCount by vm.favoritesCount.collectAsState()
    val histCount by vm.historyCount.collectAsState()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showClearHistDialog by remember { mutableStateOf(false) }
    var showClearFavDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            // ── Appearance ──────────────────────────────────────────────
            SectionHeader("Appearance")
            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = when (theme) {
                    "AMOLED" -> "AMOLED Black"
                    "LIGHT"  -> "Light"
                    else     -> "Dark"
                },
                onClick = { showThemeDialog = true }
            )

            // ── Playback ─────────────────────────────────────────────────
            SectionHeader("Playback")
            SettingsItem(
                icon = Icons.Default.HighQuality,
                title = "Video quality",
                subtitle = when (quality) {
                    "1080P" -> "1080p"
                    "720P"  -> "720p"
                    "480P"  -> "480p"
                    "360P"  -> "360p"
                    else    -> "Auto (best available)"
                },
                onClick = { showQualityDialog = true }
            )
            SettingsSwitchItem(
                icon = Icons.Default.PlayCircle,
                title = "Auto-play related videos",
                checked = autoPlay,
                onCheckedChange = { vm.setAutoPlay(it) }
            )
            SettingsSwitchItem(
                icon = Icons.Default.DataSaverOn,
                title = "Data saver",
                subtitle = "Prefer lower quality to reduce data use",
                checked = dataSaver,
                onCheckedChange = { vm.setDataSaver(it) }
            )

            // ── Storage ──────────────────────────────────────────────────
            SectionHeader("Storage")
            SettingsItem(
                icon = Icons.Default.Favorite,
                title = "Clear favorites",
                subtitle = "$favCount saved",
                onClick = { if (favCount > 0) showClearFavDialog = true }
            )
            SettingsItem(
                icon = Icons.Default.History,
                title = "Clear watch history",
                subtitle = "$histCount entries",
                onClick = { if (histCount > 0) showClearHistDialog = true }
            )

            // ── About ────────────────────────────────────────────────────
            SectionHeader("About")
            SettingsItem(
                icon = Icons.Default.Info,
                title = "App version",
                subtitle = "v${vm.appVersion}"
            )
            SettingsItem(
                icon = Icons.Default.SystemUpdate,
                title = "Check for updates",
                subtitle = "Open GitHub releases",
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(vm.githubReleasesUrl))
                    )
                }
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "github.com/Tann-Menghong/StreamFlowApp",
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tann-Menghong/StreamFlowApp"))
                    )
                }
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────
    if (showThemeDialog) {
        val options = listOf("DARK" to "Dark", "AMOLED" to "AMOLED Black", "LIGHT" to "Light")
        SimplePickerDialog(
            title = "Theme",
            options = options.map { it.second },
            selectedIndex = options.indexOfFirst { it.first == theme }.coerceAtLeast(0),
            onSelect = { vm.setTheme(options[it].first); showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showQualityDialog) {
        val options = listOf("AUTO" to "Auto", "1080P" to "1080p", "720P" to "720p", "480P" to "480p", "360P" to "360p")
        SimplePickerDialog(
            title = "Video quality",
            options = options.map { it.second },
            selectedIndex = options.indexOfFirst { it.first == quality }.coerceAtLeast(0),
            onSelect = { vm.setQuality(options[it].first); showQualityDialog = false },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showClearHistDialog) {
        ConfirmDialog(
            title = "Clear history",
            message = "Remove all $histCount watch history entries?",
            onConfirm = { vm.clearHistory(); showClearHistDialog = false },
            onDismiss = { showClearHistDialog = false }
        )
    }

    if (showClearFavDialog) {
        ConfirmDialog(
            title = "Clear favorites",
            message = "Remove all $favCount favorites?",
            onConfirm = { vm.clearFavorites(); showClearFavDialog = false },
            onDismiss = { showClearFavDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SimplePickerDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
