package com.streamflow.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel

private val countryOptions = listOf(
    "US" to "United States",
    "GB" to "United Kingdom",
    "JP" to "Japan",
    "KH" to "Cambodia",
    "KR" to "South Korea",
    "IN" to "India",
    "FR" to "France",
    "DE" to "Germany",
    "CA" to "Canada",
    "AU" to "Australia"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val theme     by vm.theme.collectAsState()
    val quality   by vm.quality.collectAsState()
    val autoPlay  by vm.autoPlay.collectAsState()
    val dataSaver by vm.dataSaver.collectAsState()
    val country   by vm.country.collectAsState()
    val favCount  by vm.favoritesCount.collectAsState()
    val histCount by vm.historyCount.collectAsState()
    val update    by vm.update.collectAsState()
    val context   = LocalContext.current

    var showThemeDialog   by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showClearHist     by remember { mutableStateOf(false) }
    var showClearFav      by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(bottom = 32.dp)
        ) {

            // ── Update banner ────────────────────────────────────────────
            AnimatedVisibility(
                visible = update.info != null || update.downloading,
                enter   = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit    = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                if (update.downloading) "Downloading update…" else "Update available — v${update.info?.latestVersion}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (update.downloading) {
                            LinearProgressIndicator(
                                progress = { update.progress / 100f },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                color        = MaterialTheme.colorScheme.primary,
                                trackColor   = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.15f)
                            )
                            Text("${update.progress}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        } else {
                            Button(
                                onClick = { vm.downloadUpdate() },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) { Text("Download & Install") }
                        }
                    }
                }
            }

            // ── Appearance ───────────────────────────────────────────────
            SettingsSection("Appearance")
            SettingsCard {
                SettingsItem(Icons.Default.Palette, "Theme",
                    when (theme) { "AMOLED" -> "AMOLED Black"; "LIGHT" -> "Light"; "SYSTEM" -> "Follow system"; else -> "Dark" }
                ) { showThemeDialog = true }
            }

            // ── Playback ─────────────────────────────────────────────────
            SettingsSection("Playback")
            SettingsCard {
                SettingsItem(Icons.Default.HighQuality, "Video quality",
                    when (quality) { "1080P" -> "1080p"; "720P" -> "720p"; "480P" -> "480p"; "360P" -> "360p"; else -> "Auto" }
                ) { showQualityDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.PlayCircle, "Auto-play", "Play related videos automatically", autoPlay) { vm.setAutoPlay(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.DataSaverOn, "Data saver", "Prefer lower quality to save data", dataSaver) { vm.setDataSaver(it) }
                SettingsDivider()
                SettingsItem(Icons.Default.Language, "Trending country",
                    countryOptions.firstOrNull { it.first == country }?.second ?: country
                ) { showCountryDialog = true }
            }

            // ── Storage ──────────────────────────────────────────────────
            SettingsSection("Storage")
            SettingsCard {
                SettingsItem(Icons.Default.FavoriteBorder, "Clear favorites", "$favCount saved") { if (favCount > 0) showClearFav = true }
                SettingsDivider()
                SettingsItem(Icons.Default.History, "Clear watch history", "$histCount entries") { if (histCount > 0) showClearHist = true }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection("About")
            SettingsCard {
                SettingsItem(Icons.Default.Info, "App version", "v${vm.appVersion}")
                SettingsDivider()
                SettingsItem(Icons.Default.SystemUpdate, "Check for updates",
                    if (update.checking) "Checking…" else if (update.info != null) "Update available!" else "Up to date"
                ) { vm.checkForUpdate() }
                SettingsDivider()
                SettingsItem(Icons.Default.Code, "Source code", "github.com/Tann-Menghong/StreamFlowApp") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tann-Menghong/StreamFlowApp")))
                }
            }
        }
    }

    if (showThemeDialog) {
        val opts = listOf("SYSTEM" to "Follow system", "DARK" to "Dark", "AMOLED" to "AMOLED Black", "LIGHT" to "Light")
        PickerDialog("Theme", opts.map { it.second }, opts.indexOfFirst { it.first == theme }.coerceAtLeast(0),
            { vm.setTheme(opts[it].first); showThemeDialog = false }, { showThemeDialog = false })
    }
    if (showQualityDialog) {
        val opts = listOf("AUTO" to "Auto", "1080P" to "1080p", "720P" to "720p", "480P" to "480p", "360P" to "360p")
        PickerDialog("Video quality", opts.map { it.second }, opts.indexOfFirst { it.first == quality }.coerceAtLeast(0),
            { vm.setQuality(opts[it].first); showQualityDialog = false }, { showQualityDialog = false })
    }
    if (showCountryDialog) {
        PickerDialog(
            "Trending country",
            countryOptions.map { it.second },
            countryOptions.indexOfFirst { it.first == country }.coerceAtLeast(0),
            { vm.setCountry(countryOptions[it].first); showCountryDialog = false },
            { showCountryDialog = false }
        )
    }
    if (showClearHist) {
        ConfirmDialog("Clear history", "Remove all $histCount watch history entries?",
            { vm.clearHistory(); showClearHist = false }, { showClearHist = false })
    }
    if (showClearFav) {
        ConfirmDialog("Clear favorites", "Remove all $favCount favorites?",
            { vm.clearFavorites(); showClearFav = false }, { showClearFav = false })
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation= CardDefaults.cardElevation(0.dp)
    ) { Column(content = content) }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outline.copy(0.3f))
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchItem(icon: ImageVector, title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.85f))
    }
}

@Composable
private fun PickerDialog(title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(i) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == selectedIndex, onClick = { onSelect(i) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text  = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onConfirm) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(16.dp)
    )
}
