package com.streamflow.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val countryOptions = listOf(
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "AU" to "Australia",
    "JP" to "Japan",
    "KR" to "South Korea",
    "KH" to "Cambodia",
    "TH" to "Thailand",
    "VN" to "Vietnam",
    "ID" to "Indonesia",
    "MY" to "Malaysia",
    "SG" to "Singapore",
    "PH" to "Philippines",
    "IN" to "India",
    "FR" to "France",
    "DE" to "Germany",
    "BR" to "Brazil",
    "MX" to "Mexico",
    "RU" to "Russia",
    "TR" to "Turkey"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val theme                by vm.theme.collectAsState()
    val quality              by vm.quality.collectAsState()
    val autoPlay             by vm.autoPlay.collectAsState()
    val dataSaver            by vm.dataSaver.collectAsState()
    val country              by vm.country.collectAsState()
    val accentColor          by vm.accentColor.collectAsState()
    val defaultSpeed         by vm.defaultSpeed.collectAsState()
    val homeLayout           by vm.homeLayout.collectAsState()
    val showContinueWatching by vm.showContinueWatching.collectAsState()
    val showHeroCard         by vm.showHeroCard.collectAsState()
    val gridColumns          by vm.gridColumns.collectAsState()
    val skipSeconds          by vm.skipSeconds.collectAsState()
    val favCount             by vm.favoritesCount.collectAsState()
    val histCount            by vm.historyCount.collectAsState()
    val blockedCount         by vm.blockedCount.collectAsState()
    val volumeBoost          by vm.volumeBoost.collectAsState()
    val notifyNewVideos      by vm.notifyNewVideos.collectAsState()
    val language             by vm.language.collectAsState()
    val fontScale            by vm.fontScale.collectAsState()
    val showDonghua          by vm.showDonghua.collectAsState()
    val startTab             by vm.startTab.collectAsState()
    val incognito            by vm.incognito.collectAsState()
    val qualityCellular      by vm.qualityCellular.collectAsState()
    val historyRetention     by vm.historyRetention.collectAsState()
    val cornerStyle          by vm.cornerStyle.collectAsState()
    val navLabels            by vm.navLabels.collectAsState()
    val reduceMotion         by vm.reduceMotion.collectAsState()
    val hapticsEnabled       by vm.hapticsEnabled.collectAsState()
    val playerGestures       by vm.playerGestures.collectAsState()
    val confirmExit          by vm.confirmExit.collectAsState()

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.exportBackup(uri) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importBackup(uri) }
    val importSubsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importSubscriptions(uri) }
    val update               by vm.update.collectAsState()
    val context              = LocalContext.current

    var showThemeDialog    by remember { mutableStateOf(false) }
    var showQualityDialog  by remember { mutableStateOf(false) }
    var showCountryDialog  by remember { mutableStateOf(false) }
    var showAccentDialog   by remember { mutableStateOf(false) }
    var showSpeedDialog    by remember { mutableStateOf(false) }
    var showColumnsDialog  by remember { mutableStateOf(false) }
    var showSkipDialog     by remember { mutableStateOf(false) }
    var showClearHist      by remember { mutableStateOf(false) }
    var showClearFav       by remember { mutableStateOf(false) }
    var showClearBlocked   by remember { mutableStateOf(false) }
    var showBoostDialog    by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontDialog     by remember { mutableStateOf(false) }
    var showStartTabDialog by remember { mutableStateOf(false) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }
    var showCellularDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showCornerDialog   by remember { mutableStateOf(false) }
    var showNavLabelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 32.dp)
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
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Text(
                                if (update.downloading) "Downloading update…"
                                else "Update available — v${update.info?.latestVersion}",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (update.downloading) {
                            LinearProgressIndicator(
                                progress = { update.progress / 100f },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                color      = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.15f)
                            )
                            Text("${update.progress}%", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        } else {
                            Button(
                                onClick  = { vm.downloadUpdate() },
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
                SettingsDivider()
                SettingsItem(Icons.Default.ColorLens, "Accent color",
                    if (accentColor == "DYNAMIC") "Dynamic (Material You)"
                    else accentColor.lowercase().replaceFirstChar { it.uppercase() }
                ) { showAccentDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.Translate, "Language / ភាសា",
                    if (language == "KM") "ភាសាខ្មែរ" else "English"
                ) { showLanguageDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.FormatSize, "Font size",
                    when (fontScale) { "SMALL" -> "Small"; "LARGE" -> "Large"; else -> "Default" }
                ) { showFontDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.RoundedCorner, "Thumbnail corners",
                    when (cornerStyle) { "SQUARE" -> "Square"; "ROUND" -> "Extra round"; else -> "Rounded" }
                ) { showCornerDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.Label, "Bottom bar labels",
                    when (navLabels) { "ALWAYS" -> "Always show"; "NEVER" -> "Icons only"; else -> "Selected tab only" }
                ) { showNavLabelDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.Animation, "Reduce motion",
                    "Calmer, faster screen transitions", reduceMotion, vm::setReduceMotion)
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.Vibration, "Haptic feedback",
                    "Vibrate on long-press actions", hapticsEnabled, vm::setHapticsEnabled)
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.ExitToApp, "Confirm before exit",
                    "Press back twice on Home to close the app", confirmExit, vm::setConfirmExit)
            }

            // ── Notifications ────────────────────────────────────────────
            SettingsSection("Notifications")
            SettingsCard {
                SettingsSwitchItem(Icons.Default.NotificationsActive, "New video alerts",
                    "Notify when subscribed channels upload (checks every ~6h)", notifyNewVideos
                ) { vm.setNotifyNewVideos(it) }
            }

            // ── Home customization ────────────────────────────────────────
            SettingsSection("Home")
            SettingsCard {
                SettingsSwitchItem(Icons.Default.GridView, "Grid layout",
                    "Show videos in a grid instead of list", homeLayout == "GRID"
                ) { vm.setHomeLayout(if (it) "GRID" else "LIST") }
                SettingsDivider()
                SettingsItem(Icons.Default.ViewModule, "Grid columns",
                    "$gridColumns columns"
                ) { showColumnsDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.History, "Continue Watching row",
                    "Show partially watched videos at the top", showContinueWatching
                ) { vm.setShowContinueWatching(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.Stars, "Hero featured card",
                    "Show first trending video as a large banner", showHeroCard
                ) { vm.setShowHeroCard(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.LiveTv, "Show Donghua tab",
                    "Hide it from the bottom bar if you don't use it", showDonghua
                ) { vm.setShowDonghua(it) }
                SettingsDivider()
                SettingsItem(Icons.Default.Start, "Start screen",
                    when (startTab) { "donghua" -> "Donghua"; "library" -> "Library"; else -> "Home" }
                ) { showStartTabDialog = true }
            }

            // ── Playback ─────────────────────────────────────────────────
            SettingsSection("Playback")
            SettingsCard {
                SettingsItem(Icons.Default.HighQuality, "Video quality",
                    when (quality) { "1080P" -> "1080p"; "720P" -> "720p"; "480P" -> "480p"; "360P" -> "360p"; else -> "Auto" }
                ) { showQualityDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.SignalCellularAlt, "Quality on mobile data",
                    when (qualityCellular) { "720P" -> "720p"; "480P" -> "480p"; "360P" -> "360p"; "AUTO" -> "Auto"; else -> "Same as Wi-Fi" }
                ) { showCellularDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.Speed, "Default speed",
                    when (defaultSpeed) { "0.5" -> "0.5×"; "0.75" -> "0.75×"; "1.25" -> "1.25×"; "1.5" -> "1.5×"; "2.0" -> "2×"; else -> "1×" }
                ) { showSpeedDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.FastForward, "Double-tap skip",
                    "${skipSeconds}s per tap"
                ) { showSkipDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.Swipe, "Player swipe gestures",
                    "Swipe edges for brightness and volume", playerGestures, vm::setPlayerGestures)
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.PlayCircle, "Auto-play",
                    "Play related videos automatically", autoPlay
                ) { vm.setAutoPlay(it) }
                SettingsDivider()
                SettingsItem(Icons.Default.VolumeUp, "Volume boost",
                    when (volumeBoost) { "300" -> "Low (+30%)"; "600" -> "High (+60%)"; "1000" -> "Max (+100%)"; else -> "Off" }
                ) { showBoostDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.DataSaverOn, "Data saver",
                    "Prefer lower quality to save mobile data", dataSaver
                ) { vm.setDataSaver(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Default.VisibilityOff, "Incognito mode",
                    "Watch without saving to history", incognito
                ) { vm.setIncognito(it) }
                SettingsDivider()
                SettingsItem(Icons.Default.Language, "Trending country",
                    countryOptions.firstOrNull { it.first == country }?.second ?: country
                ) { showCountryDialog = true }
            }

            // ── Storage ──────────────────────────────────────────────────
            SettingsSection("Storage")
            SettingsCard {
                SettingsItem(Icons.Default.FavoriteBorder, "Clear favorites",
                    "$favCount saved"
                ) { if (favCount > 0) showClearFav = true }
                SettingsDivider()
                SettingsItem(Icons.Default.History, "Clear watch history",
                    "$histCount entries"
                ) { if (histCount > 0) showClearHist = true }
                SettingsDivider()
                SettingsItem(Icons.Default.VisibilityOff, "Hidden videos & channels",
                    if (blockedCount == 0) "Nothing hidden" else "$blockedCount hidden"
                ) { if (blockedCount > 0) showClearBlocked = true }
                SettingsDivider()
                SettingsItem(Icons.Default.AutoDelete, "Auto-clear history",
                    when (historyRetention) { "30" -> "After 30 days"; "90" -> "After 90 days"; else -> "Never" }
                ) { showRetentionDialog = true }
            }

            // ── Backup ───────────────────────────────────────────────────
            SettingsSection("Backup")
            SettingsCard {
                SettingsItem(Icons.Default.Upload, "Export backup",
                    "Subscriptions, favorites, playlists → JSON file"
                ) { exportLauncher.launch("streamflow-backup.json") }
                SettingsDivider()
                SettingsItem(Icons.Default.Download, "Import backup",
                    "Restore from a StreamFlow backup file"
                ) { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                SettingsDivider()
                SettingsItem(Icons.Default.Subscriptions, "Import YouTube subscriptions",
                    "From a Google Takeout CSV or NewPipe export"
                ) { importSubsLauncher.launch(arrayOf("text/csv", "text/comma-separated-values",
                    "application/json", "text/*", "application/octet-stream")) }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection("About")
            SettingsCard {
                SettingsItem(Icons.Default.Info, "App version", "v${vm.appVersion}")
                SettingsDivider()
                SettingsItem(Icons.Default.NewReleases, "What's new",
                    "See what changed in v${com.streamflow.data.Changelog.VERSION_NAME}"
                ) { showWhatsNewDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Default.SystemUpdate, "Check for updates",
                    if (update.checking) "Checking…" else if (update.info != null) "Update available!" else "Up to date"
                ) { vm.checkForUpdate() }
                SettingsDivider()
                SettingsItem(Icons.Default.Code, "Source code",
                    "github.com/Tann-Menghong/StreamFlowApp"
                ) {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Tann-Menghong/StreamFlowApp")))
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────
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
            countryOptions.map { "${it.second} (${it.first})" },
            countryOptions.indexOfFirst { it.first == country }.coerceAtLeast(0),
            { vm.setCountry(countryOptions[it].first); showCountryDialog = false },
            { showCountryDialog = false }
        )
    }
    if (showAccentDialog) {
        AccentPickerDialog(accentColor, { vm.setAccentColor(it); showAccentDialog = false }, { showAccentDialog = false })
    }
    if (showSpeedDialog) {
        val speedOpts = listOf("0.5" to "0.5×", "0.75" to "0.75×", "1.0" to "1×", "1.25" to "1.25×", "1.5" to "1.5×", "2.0" to "2×")
        PickerDialog("Default speed", speedOpts.map { it.second }, speedOpts.indexOfFirst { it.first == defaultSpeed }.coerceAtLeast(0),
            { vm.setDefaultSpeed(speedOpts[it].first); showSpeedDialog = false }, { showSpeedDialog = false })
    }
    if (showColumnsDialog) {
        val colOpts = listOf("2" to "2 columns", "3" to "3 columns")
        PickerDialog("Grid columns", colOpts.map { it.second }, colOpts.indexOfFirst { it.first == gridColumns }.coerceAtLeast(0),
            { vm.setGridColumns(colOpts[it].first); showColumnsDialog = false }, { showColumnsDialog = false })
    }
    if (showSkipDialog) {
        val skipOpts = listOf("5" to "5 seconds", "10" to "10 seconds", "15" to "15 seconds", "30" to "30 seconds")
        PickerDialog("Double-tap skip", skipOpts.map { it.second }, skipOpts.indexOfFirst { it.first == skipSeconds }.coerceAtLeast(0),
            { vm.setSkipSeconds(skipOpts[it].first); showSkipDialog = false }, { showSkipDialog = false })
    }
    if (showClearHist) {
        ConfirmDialog("Clear history", "Remove all $histCount watch history entries?",
            { vm.clearHistory(); showClearHist = false }, { showClearHist = false })
    }
    if (showClearFav) {
        ConfirmDialog("Clear favorites", "Remove all $favCount favorites?",
            { vm.clearFavorites(); showClearFav = false }, { showClearFav = false })
    }
    if (showClearBlocked) {
        ConfirmDialog("Unhide all", "Show the $blockedCount hidden videos/channels in your feed again?",
            { vm.clearBlocked(); showClearBlocked = false }, { showClearBlocked = false })
    }
    if (showBoostDialog) {
        val boostOpts = listOf("0" to "Off", "300" to "Low (+30%)", "600" to "High (+60%)", "1000" to "Max (+100%)")
        PickerDialog("Volume boost", boostOpts.map { it.second },
            boostOpts.indexOfFirst { it.first == volumeBoost }.coerceAtLeast(0),
            { vm.setVolumeBoost(boostOpts[it].first); showBoostDialog = false }, { showBoostDialog = false })
    }
    if (showLanguageDialog) {
        val langOpts = listOf("EN" to "English", "KM" to "ភាសាខ្មែរ (Khmer)")
        PickerDialog("Language / ភាសា", langOpts.map { it.second },
            langOpts.indexOfFirst { it.first == language }.coerceAtLeast(0),
            { vm.setLanguage(langOpts[it].first); showLanguageDialog = false }, { showLanguageDialog = false })
    }
    if (showFontDialog) {
        val fontOpts = listOf("SMALL" to "Small", "DEFAULT" to "Default", "LARGE" to "Large")
        PickerDialog("Font size", fontOpts.map { it.second },
            fontOpts.indexOfFirst { it.first == fontScale }.coerceAtLeast(0),
            { vm.setFontScale(fontOpts[it].first); showFontDialog = false }, { showFontDialog = false })
    }
    if (showStartTabDialog) {
        val tabOpts = listOf("home" to "Home", "donghua" to "Donghua", "library" to "Library")
        PickerDialog("Start screen", tabOpts.map { it.second },
            tabOpts.indexOfFirst { it.first == startTab }.coerceAtLeast(0),
            { vm.setStartTab(tabOpts[it].first); showStartTabDialog = false }, { showStartTabDialog = false })
    }
    if (showCellularDialog) {
        val cellOpts = listOf("SAME" to "Same as Wi-Fi", "AUTO" to "Auto", "720P" to "720p", "480P" to "480p", "360P" to "360p")
        PickerDialog("Quality on mobile data", cellOpts.map { it.second },
            cellOpts.indexOfFirst { it.first == qualityCellular }.coerceAtLeast(0),
            { vm.setQualityCellular(cellOpts[it].first); showCellularDialog = false }, { showCellularDialog = false })
    }
    if (showRetentionDialog) {
        val retOpts = listOf("0" to "Never", "30" to "After 30 days", "90" to "After 90 days")
        PickerDialog("Auto-clear history", retOpts.map { it.second },
            retOpts.indexOfFirst { it.first == historyRetention }.coerceAtLeast(0),
            { vm.setHistoryRetention(retOpts[it].first); showRetentionDialog = false }, { showRetentionDialog = false })
    }
    if (showCornerDialog) {
        val cornerOpts = listOf("SQUARE" to "Square", "ROUNDED" to "Rounded", "ROUND" to "Extra round")
        PickerDialog("Thumbnail corners", cornerOpts.map { it.second },
            cornerOpts.indexOfFirst { it.first == cornerStyle }.coerceAtLeast(0),
            { vm.setCornerStyle(cornerOpts[it].first); showCornerDialog = false }, { showCornerDialog = false })
    }
    if (showNavLabelDialog) {
        val labelOpts = listOf("ALWAYS" to "Always show", "SELECTED" to "Selected tab only", "NEVER" to "Icons only")
        PickerDialog("Bottom bar labels", labelOpts.map { it.second },
            labelOpts.indexOfFirst { it.first == navLabels }.coerceAtLeast(0),
            { vm.setNavLabels(labelOpts[it].first); showNavLabelDialog = false }, { showNavLabelDialog = false })
    }
    if (showWhatsNewDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsNewDialog = false },
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
                TextButton(onClick = { showWhatsNewDialog = false }) { Text("Got it") }
            }
        )
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) { Column(content = content) }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color    = MaterialTheme.colorScheme.outline.copy(0.3f)
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
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
            modifier = Modifier.size(18.dp))
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.85f))
    }
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(i) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = i == selectedIndex, onClick = { onSelect(i) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onDismiss) { Text("Cancel") } },
        shape          = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title  = { Text(title, fontWeight = FontWeight.Bold) },
        text   = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onConfirm) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape  = RoundedCornerShape(16.dp)
    )
}

private val accentCircleColors = listOf(
    "RED"    to Color(0xFFFF3B3B),
    "BLUE"   to Color(0xFF448AFF),
    "GREEN"  to Color(0xFF00C853),
    "PURPLE" to Color(0xFFA855F7),
    "ORANGE" to Color(0xFFFF7722),
    "PINK"   to Color(0xFFF472B6),
    "TEAL"   to Color(0xFF2DD4BF),
    "YELLOW" to Color(0xFFFACC15),
    "INDIGO" to Color(0xFF818CF8),
    "CYAN"   to Color(0xFF22D3EE),
)

@Composable
private fun AccentPickerDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent color", fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                // Material You option — only on Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected == "DYNAMIC") MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                            )
                            .clickable { onSelect("DYNAMIC") }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.sweepGradient(
                                        listOf(Color(0xFF6750A4), Color(0xFF006875), Color(0xFF3F6844), Color(0xFF6750A4))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected == "DYNAMIC") Icon(Icons.Default.Check, null,
                                tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Dynamic (Material You)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected == "DYNAMIC") MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onBackground)
                            Text("Uses your wallpaper colors",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // Custom color: pick any hue with the slider
                var customHue by remember {
                    mutableFloatStateOf(
                        if (selected.startsWith("CUSTOM:")) {
                            val c = selected.removePrefix("CUSTOM:").toLongOrNull(16)
                            if (c != null) {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV((c or 0xFF000000L).toInt(), hsv)
                                hsv[0]
                            } else 200f
                        } else 200f
                    )
                }
                val customColor = Color.hsv(customHue, 0.82f, 0.95f)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(customColor)
                            .then(if (selected.startsWith("CUSTOM:"))
                                Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                            .clickable {
                                onSelect("CUSTOM:%06X".format(
                                    android.graphics.Color.HSVToColor(floatArrayOf(customHue, 0.82f, 0.95f)) and 0xFFFFFF))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected.startsWith("CUSTOM:")) Icon(Icons.Default.Check, null,
                            tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Custom color — tap circle to apply",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = customHue,
                            onValueChange = { customHue = it },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = customColor,
                                activeTrackColor = customColor,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
                // Color grid
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.padding(top = 4.dp)
                ) {
                    itemsIndexed(accentCircleColors) { _, (key, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (key == selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { onSelect(key) }
                            ) {
                                if (key == selected) Icon(Icons.Default.Check, null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(key.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 9.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onDismiss) { Text("Cancel") } },
        shape          = RoundedCornerShape(16.dp)
    )
}
