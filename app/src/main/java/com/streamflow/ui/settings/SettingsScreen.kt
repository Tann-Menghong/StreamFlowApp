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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamflow.data.ai.AiEngine

// Section header Y-positions inside the settings scroll column, recorded by
// SettingsSection and used by the quick-jump chips (single-instance screen)
private val settingsSectionOffsets = mutableStateMapOf<String, Int>()

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
    val notifyFreq           by vm.notifyFreq.collectAsState()
    val notifyMax            by vm.notifyMax.collectAsState()
    val notifyAppUpdates     by vm.notifyAppUpdates.collectAsState()
    val quietHours           by vm.quietHours.collectAsState()
    val cornerStyle          by vm.cornerStyle.collectAsState()
    val navLabels            by vm.navLabels.collectAsState()
    val reduceMotion         by vm.reduceMotion.collectAsState()
    val hapticsEnabled       by vm.hapticsEnabled.collectAsState()
    val playerGestures       by vm.playerGestures.collectAsState()
    val autoPip              by vm.autoPip.collectAsState()
    val designStyle          by vm.designStyle.collectAsState()
    val eqPreset             by vm.eqPreset.collectAsState()
    var showEqDialog         by remember { mutableStateOf(false) }
    val batterySaver         by vm.batterySaver.collectAsState()
    val autoDlWatchLater     by vm.autoDlWatchLater.collectAsState()
    val confirmExit          by vm.confirmExit.collectAsState()
    val showSearchTab        by vm.showSearchTab.collectAsState()
    val fontFamily           by vm.fontFamily.collectAsState()
    val libraryTab           by vm.libraryTab.collectAsState()
    val aiState              by vm.aiState.collectAsState()

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
    var showNotifFreqDialog by remember { mutableStateOf(false) }
    var showNotifMaxDialog  by remember { mutableStateOf(false) }
    var showQuietDialog     by remember { mutableStateOf(false) }
    var showFontFamilyDialog by remember { mutableStateOf(false) }
    var showLibTabDialog     by remember { mutableStateOf(false) }
    var showDeleteAiDialog   by remember { mutableStateOf(false) }

    // Telegram-style big title that collapses into the bar as you scroll
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title  = { Text("Settings", fontWeight = FontWeight.ExtraBold) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val sectionScroll = rememberScrollState()
        val jumpScope = rememberCoroutineScope()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(sectionScroll)
                .padding(padding)
                .padding(bottom = 32.dp)
        ) {

            // ── App hero card: gradient brand banner with version ─────────
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(46.dp).background(
                            androidx.compose.ui.graphics.Color.White.copy(0.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(28.dp))
                    }
                    Column {
                        Text("StreamFlow", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                            color = androidx.compose.ui.graphics.Color.White)
                        Text("Version ${com.streamflow.BuildConfig.VERSION_NAME}",
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.White.copy(0.85f))
                    }
                }
            }

            // ── Dashboard: colorful section tiles, tap to jump ────────────
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    listOf(
                        Triple("Appearance", Icons.Rounded.Palette, Color(0xFFAF52DE)),
                        Triple("Playback", Icons.Rounded.PlayCircle, Color(0xFF4C8DFF))),
                    listOf(
                        Triple("Notifications", Icons.Rounded.Notifications, Color(0xFFFF9500)),
                        Triple("Home", Icons.Rounded.Home, Color(0xFF34C759))),
                    listOf(
                        Triple("AI", Icons.Rounded.AutoAwesome, Color(0xFFEC407A)),
                        Triple("Storage", Icons.Rounded.Storage, Color(0xFF26A69A))),
                    listOf(
                        Triple("Backup", Icons.Rounded.Backup, Color(0xFF5C6BC0)),
                        Triple("About", Icons.Rounded.Info, Color(0xFFFF7043)))
                ).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTiles.forEach { (name, tileIcon, tileColor) ->
                            Surface(
                                onClick = {
                                    settingsSectionOffsets[name]?.let { y ->
                                        jumpScope.launch { sectionScroll.animateScrollTo(y) }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = tileColor.copy(0.13f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier.size(30.dp).background(tileColor, RoundedCornerShape(9.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(tileIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

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
                            Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary,
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
                SettingsSwitchItem(Icons.Rounded.AutoAwesome, "Modern design",
                    "Card feed, floating bars, colorful icons — off for the classic look",
                    designStyle == "MODERN"
                ) { vm.setDesignStyle(if (it) "MODERN" else "CLASSIC") }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Palette, "Theme",
                    when (theme) { "AMOLED" -> "AMOLED Black"; "LIGHT" -> "Light"; "SYSTEM" -> "Follow system"; else -> "Dark" }
                ) { showThemeDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.ColorLens, "Accent color",
                    if (accentColor == "DYNAMIC") "Dynamic (Material You)"
                    else accentColor.lowercase().replaceFirstChar { it.uppercase() }
                ) { showAccentDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Translate, "Language / ភាសា",
                    if (language == "KM") "ភាសាខ្មែរ" else "English"
                ) { showLanguageDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.FormatSize, "Font size",
                    when (fontScale) { "SMALL" -> "Small"; "LARGE" -> "Large"; else -> "Default" }
                ) { showFontDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.TextFields, "Font style",
                    when (fontFamily) { "SERIF" -> "Serif"; "MONO" -> "Monospace"; else -> "Default" }
                ) { showFontFamilyDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.RoundedCorner, "Thumbnail corners",
                    when (cornerStyle) { "SQUARE" -> "Square"; "ROUND" -> "Extra round"; else -> "Rounded" }
                ) { showCornerDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Label, "Bottom bar labels",
                    when (navLabels) { "ALWAYS" -> "Always show"; "NEVER" -> "Icons only"; else -> "Selected tab only" }
                ) { showNavLabelDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.Animation, "Reduce motion",
                    "Calmer, faster screen transitions", reduceMotion, vm::setReduceMotion)
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.Vibration, "Haptic feedback",
                    "Vibrate on long-press actions", hapticsEnabled, vm::setHapticsEnabled)
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.ExitToApp, "Confirm before exit",
                    "Press back twice on Home to close the app", confirmExit, vm::setConfirmExit)
            }

            // ── Notifications ────────────────────────────────────────────
            SettingsSection("Notifications")
            SettingsCard {
                SettingsSwitchItem(Icons.Rounded.NotificationsActive, "New video alerts",
                    "Notify when subscribed channels upload", notifyNewVideos
                ) { vm.setNotifyNewVideos(it) }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Schedule, "Check frequency",
                    when (notifyFreq) {
                        "1" -> "Every hour"; "3" -> "Every 3 hours"; "12" -> "Every 12 hours"
                        "24" -> "Once a day"; else -> "Every 6 hours"
                    }
                ) { showNotifFreqDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.FilterList, "Alerts per check",
                    if (notifyMax == "0") "Unlimited" else "Up to $notifyMax"
                ) { showNotifMaxDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Bedtime, "Quiet hours",
                    if (quietHours == "OFF") "Off"
                    else quietHours.split("-").let { p ->
                        "%02d:00 – %02d:00".format(p.getOrNull(0)?.toIntOrNull() ?: 22,
                                                    p.getOrNull(1)?.toIntOrNull() ?: 7)
                    }
                ) { showQuietDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.SystemUpdate, "App update alerts",
                    "Notify when a new StreamFlow version is released", notifyAppUpdates
                ) { vm.setNotifyAppUpdates(it) }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Tune, "Sound & vibration",
                    "Per-channel options in Android settings"
                ) {
                    try {
                        val i = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(i)
                    } catch (_: Exception) {}
                }
            }

            // ── Home customization ────────────────────────────────────────
            SettingsFooter("New-video alerts follow the schedule above and stay silent during quiet hours.")

            SettingsSection("Home")
            SettingsCard {
                SettingsSwitchItem(Icons.Rounded.GridView, "Grid layout",
                    "Show videos in a grid instead of list", homeLayout == "GRID"
                ) { vm.setHomeLayout(if (it) "GRID" else "LIST") }
                SettingsDivider()
                SettingsItem(Icons.Rounded.ViewModule, "Grid columns",
                    "$gridColumns columns"
                ) { showColumnsDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.History, "Continue Watching row",
                    "Show partially watched videos at the top", showContinueWatching
                ) { vm.setShowContinueWatching(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.Stars, "Hero featured card",
                    "Show first trending video as a large banner", showHeroCard
                ) { vm.setShowHeroCard(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.LiveTv, "Show Donghua tab",
                    "Hide it from the bottom bar if you don't use it", showDonghua
                ) { vm.setShowDonghua(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.Search, "Search tab",
                    "Add a dedicated Search tab to the bottom bar", showSearchTab
                ) { vm.setShowSearchTab(it) }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Start, "Start screen",
                    when (startTab) { "donghua" -> "Donghua"; "library" -> "Library"; else -> "Home" }
                ) { showStartTabDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.VideoLibrary, "Default Library tab",
                    listOf("Favorites", "History", "Watch Later", "Channels", "Playlists", "Downloads")
                        .getOrElse(libraryTab.toIntOrNull() ?: 0) { "Favorites" }
                ) { showLibTabDialog = true }
            }

            // ── Playback ─────────────────────────────────────────────────
            SettingsSection("Playback")
            SettingsCard {
                SettingsItem(Icons.Rounded.HighQuality, "Video quality",
                    when (quality) { "1080P" -> "1080p"; "720P" -> "720p"; "480P" -> "480p"; "360P" -> "360p"; else -> "Auto" }
                ) { showQualityDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.SignalCellularAlt, "Quality on mobile data",
                    when (qualityCellular) { "720P" -> "720p"; "480P" -> "480p"; "360P" -> "360p"; "AUTO" -> "Auto"; else -> "Same as Wi-Fi" }
                ) { showCellularDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Speed, "Default speed",
                    when (defaultSpeed) { "0.5" -> "0.5×"; "0.75" -> "0.75×"; "1.25" -> "1.25×"; "1.5" -> "1.5×"; "2.0" -> "2×"; else -> "1×" }
                ) { showSpeedDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.FastForward, "Double-tap skip",
                    "${skipSeconds}s per tap"
                ) { showSkipDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.Swipe, "Player swipe gestures",
                    "Swipe edges for brightness and volume", playerGestures, vm::setPlayerGestures)
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.PictureInPicture, "Pop-up video on exit",
                    "Off: leaving the app keeps playing in the notification only", autoPip, vm::setAutoPip)
                SettingsDivider()
                SettingsItem(Icons.Rounded.BatteryChargingFull, "Background play protection",
                    "Stop the phone from killing playback (recommended on Vivo/iQOO/Xiaomi)"
                ) {
                    try {
                        val pm = context.getSystemService(android.content.Context.POWER_SERVICE)
                            as android.os.PowerManager
                        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                            !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            val i = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:${context.packageName}"))
                            context.startActivity(i)
                        } else {
                            android.widget.Toast.makeText(context,
                                "Already protected — playback won't be killed",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        // Some OEM builds block the dialog; open the app's battery settings instead
                        try {
                            val i = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${context.packageName}"))
                            context.startActivity(i)
                        } catch (_: Exception) {}
                    }
                }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.PlayCircle, "Auto-play",
                    "Play related videos automatically", autoPlay
                ) { vm.setAutoPlay(it) }
                SettingsDivider()
                SettingsItem(Icons.Rounded.VolumeUp, "Volume boost",
                    when (volumeBoost) { "300" -> "Low (+30%)"; "600" -> "High (+60%)"; "1000" -> "Max (+100%)"; else -> "Off" }
                ) { showBoostDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.GraphicEq, "Equalizer",
                    if (eqPreset == "OFF") "Off" else eqPreset
                ) { showEqDialog = true }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.DataSaverOn, "Data saver",
                    "Prefer lower quality to save mobile data", dataSaver
                ) { vm.setDataSaver(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.BatterySaver, "Battery saver",
                    "Cap quality at 480p, no ambient glow, no prefetching", batterySaver
                ) { vm.setBatterySaver(it) }
                SettingsDivider()
                SettingsSwitchItem(Icons.Rounded.VisibilityOff, "Incognito mode",
                    "Watch without saving to history", incognito
                ) { vm.setIncognito(it) }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Language, "Trending country",
                    countryOptions.firstOrNull { it.first == country }?.second ?: country
                ) { showCountryDialog = true }
            }

            // ── AI (on-device, free, offline) ────────────────────────────
            SettingsFooter("Playback keeps running in the notification when you leave the app. If your phone kills it, turn on Background play protection.")

            SettingsSection("AI")
            SettingsCard {
                if (!AiEngine.isSupported()) {
                    SettingsItem(Icons.Rounded.AutoAwesome, "On-device AI",
                        "Needs Android 7.0 or newer")
                } else when (val s = aiState) {
                    is AiEngine.DownloadState.Downloading -> {
                        SettingsItem(Icons.Rounded.AutoAwesome, "Downloading AI model…",
                            "${(s.progress * 100).toInt()}% of ${AiEngine.MODEL_SIZE_LABEL}")
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        )
                    }
                    is AiEngine.DownloadState.Ready -> {
                        SettingsItem(Icons.Rounded.AutoAwesome, "On-device AI ready",
                            "${AiEngine.MODEL_LABEL} — tap ✦ in the player for summaries & Q&A")
                        SettingsDivider()
                        SettingsItem(Icons.Rounded.DeleteOutline, "Remove AI model",
                            "Frees up ${AiEngine.MODEL_SIZE_LABEL} of storage"
                        ) { showDeleteAiDialog = true }
                    }
                    is AiEngine.DownloadState.Failed -> {
                        SettingsItem(Icons.Rounded.AutoAwesome, "Download failed — tap to resume",
                            s.message) { vm.downloadAiModel() }
                    }
                    else -> {
                        SettingsItem(Icons.Rounded.AutoAwesome, "Download AI model",
                            "One-time download (${AiEngine.MODEL_SIZE_LABEL}) — video summaries and Q&A, free and fully offline"
                        ) { vm.downloadAiModel() }
                    }
                }
            }

            // ── Storage ──────────────────────────────────────────────────
            SettingsFooter("The AI runs fully on your device — nothing you ask ever leaves your phone.")

            SettingsSection("Storage")
            SettingsCard {
                SettingsSwitchItem(Icons.Rounded.CloudDownload, "Auto-download Watch Later",
                    "On Wi-Fi, saves Watch Later videos for offline (3 per check)", autoDlWatchLater
                ) { vm.setAutoDlWatchLater(it) }
                SettingsDivider()
                SettingsItem(Icons.Rounded.FavoriteBorder, "Clear favorites",
                    "$favCount saved"
                ) { if (favCount > 0) showClearFav = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.History, "Clear watch history",
                    "$histCount entries"
                ) { if (histCount > 0) showClearHist = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.VisibilityOff, "Hidden videos & channels",
                    if (blockedCount == 0) "Nothing hidden" else "$blockedCount hidden"
                ) { if (blockedCount > 0) showClearBlocked = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.AutoDelete, "Auto-clear history",
                    when (historyRetention) { "30" -> "After 30 days"; "90" -> "After 90 days"; else -> "Never" }
                ) { showRetentionDialog = true }
            }

            // ── Backup ───────────────────────────────────────────────────
            SettingsSection("Backup")
            SettingsCard {
                SettingsItem(Icons.Rounded.Upload, "Export backup",
                    "Subscriptions, favorites, playlists → JSON file"
                ) { exportLauncher.launch("streamflow-backup.json") }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Download, "Import backup",
                    "Restore from a StreamFlow backup file"
                ) { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Subscriptions, "Import YouTube subscriptions",
                    "From a Google Takeout CSV or NewPipe export"
                ) { importSubsLauncher.launch(arrayOf("text/csv", "text/comma-separated-values",
                    "application/json", "text/*", "application/octet-stream")) }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection("About")
            SettingsCard {
                SettingsItem(Icons.Rounded.Info, "App version", "v${vm.appVersion}")
                SettingsDivider()
                SettingsItem(Icons.Rounded.NewReleases, "What's new",
                    "See what changed in v${com.streamflow.data.Changelog.VERSION_NAME}"
                ) { showWhatsNewDialog = true }
                SettingsDivider()
                SettingsItem(Icons.Rounded.SystemUpdate, "Check for updates",
                    if (update.checking) "Checking…" else if (update.info != null) "Update available!" else "Up to date"
                ) { vm.checkForUpdate() }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Code, "Source code",
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
    if (showEqDialog) {
        // Standard Android preset names; the playback service matches by name
        // and silently ignores presets a device doesn't have
        val eqOpts = listOf("OFF" to "Off", "Normal" to "Normal", "Classical" to "Classical",
            "Dance" to "Dance", "Flat" to "Flat", "Folk" to "Folk",
            "Heavy Metal" to "Heavy metal", "Hip Hop" to "Hip hop",
            "Jazz" to "Jazz", "Pop" to "Pop", "Rock" to "Rock")
        PickerDialog("Equalizer", eqOpts.map { it.second },
            eqOpts.indexOfFirst { it.first == eqPreset }.coerceAtLeast(0),
            { vm.setEqPreset(eqOpts[it].first); showEqDialog = false }, { showEqDialog = false })
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
    if (showDeleteAiDialog) {
        ConfirmDialog("Remove AI model", "Delete the downloaded AI model (${AiEngine.MODEL_SIZE_LABEL})? You can download it again anytime.",
            { vm.deleteAiModel(); showDeleteAiDialog = false }, { showDeleteAiDialog = false })
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
    if (showNotifFreqDialog) {
        val freqOpts = listOf("1" to "Every hour", "3" to "Every 3 hours", "6" to "Every 6 hours",
            "12" to "Every 12 hours", "24" to "Once a day")
        PickerDialog("Check frequency", freqOpts.map { it.second },
            freqOpts.indexOfFirst { it.first == notifyFreq }.coerceAtLeast(0),
            { vm.setNotifyFreq(freqOpts[it].first); showNotifFreqDialog = false }, { showNotifFreqDialog = false })
    }
    if (showNotifMaxDialog) {
        val maxOpts = listOf("1" to "1 alert", "3" to "Up to 3", "5" to "Up to 5", "0" to "Unlimited")
        PickerDialog("Alerts per check", maxOpts.map { it.second },
            maxOpts.indexOfFirst { it.first == notifyMax }.coerceAtLeast(0),
            { vm.setNotifyMax(maxOpts[it].first); showNotifMaxDialog = false }, { showNotifMaxDialog = false })
    }
    if (showQuietDialog) {
        val quietOpts = listOf("OFF" to "Off", "21-6" to "21:00 – 06:00", "22-7" to "22:00 – 07:00",
            "23-8" to "23:00 – 08:00", "0-7" to "00:00 – 07:00")
        PickerDialog("Quiet hours", quietOpts.map { it.second },
            quietOpts.indexOfFirst { it.first == quietHours }.coerceAtLeast(0),
            { vm.setQuietHours(quietOpts[it].first); showQuietDialog = false }, { showQuietDialog = false })
    }
    if (showFontFamilyDialog) {
        val famOpts = listOf("DEFAULT" to "Default", "SERIF" to "Serif", "MONO" to "Monospace")
        PickerDialog("Font style", famOpts.map { it.second },
            famOpts.indexOfFirst { it.first == fontFamily }.coerceAtLeast(0),
            { vm.setFontFamily(famOpts[it].first); showFontFamilyDialog = false }, { showFontFamilyDialog = false })
    }
    if (showLibTabDialog) {
        val tabNames = listOf("Favorites", "History", "Watch Later", "Channels", "Playlists", "Downloads")
        PickerDialog("Default Library tab", tabNames,
            (libraryTab.toIntOrNull() ?: 0).coerceIn(0, 5),
            { vm.setLibraryTab(it.toString()); showLibTabDialog = false }, { showLibTabDialog = false })
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
        text     = title,
        style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color    = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(start = 20.dp, top = 22.dp, bottom = 8.dp)
            // Record where this section sits so the dashboard tiles can jump here
            .onGloballyPositioned { settingsSectionOffsets[title] = it.positionInParent().y.toInt() }
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(18.dp),
        // Slight tonal lift so cards read as clear groups on the background
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.32f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) { Column(content = content) }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color    = MaterialTheme.colorScheme.outline.copy(0.25f)
    )
}

// Telegram-style icon badges: each row gets its own solid color square with a
// white icon, picked deterministically so a row keeps its color forever
private val badgePalette = listOf(
    Color(0xFF4C8DFF), Color(0xFF34C759), Color(0xFFFF9500), Color(0xFFAF52DE),
    Color(0xFFFF3B5C), Color(0xFF00BCD4), Color(0xFF7E57C2), Color(0xFFFF7043),
    Color(0xFF26A69A), Color(0xFFEC407A), Color(0xFF5C6BC0), Color(0xFF8BC34A)
)

@Composable
private fun SettingsIconBadge(icon: ImageVector, title: String) {
    if (com.streamflow.ui.theme.LocalDesignStyle.current == "MODERN") {
        val color = badgePalette[Math.abs(title.hashCode()) % badgePalette.size]
        Box(
            Modifier.size(32.dp).background(color, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
    } else {
        // CLASSIC: plain tinted icon, no badge
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

// Small gray explanation under a settings card, Telegram-style
@Composable
private fun SettingsFooter(text: String) {
    Text(
        text, fontSize = 12.sp, lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f),
        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 6.dp)
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
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon, title)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null,
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
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon, title)
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
                            if (selected == "DYNAMIC") Icon(Icons.Rounded.Check, null,
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
                // Custom color: full hue/saturation/brightness control
                val initialHsv = remember {
                    val hsv = floatArrayOf(200f, 0.82f, 0.95f)
                    if (selected.startsWith("CUSTOM:")) {
                        selected.removePrefix("CUSTOM:").toLongOrNull(16)?.let { c ->
                            android.graphics.Color.colorToHSV((c or 0xFF000000L).toInt(), hsv)
                        }
                    }
                    hsv
                }
                var customHue by remember { mutableFloatStateOf(initialHsv[0]) }
                var customSat by remember { mutableFloatStateOf(initialHsv[1]) }
                var customVal by remember { mutableFloatStateOf(initialHsv[2]) }
                val customColor = Color.hsv(customHue, customSat, customVal)
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
                                    android.graphics.Color.HSVToColor(
                                        floatArrayOf(customHue, customSat, customVal)) and 0xFFFFFF))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected.startsWith("CUSTOM:")) Icon(Icons.Rounded.Check, null,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Depth", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(44.dp))
                            Slider(
                                value = customSat,
                                onValueChange = { customSat = it },
                                valueRange = 0.15f..1f,
                                modifier = Modifier.height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = customColor,
                                    activeTrackColor = customColor,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bright", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(44.dp))
                            Slider(
                                value = customVal,
                                onValueChange = { customVal = it },
                                valueRange = 0.35f..1f,
                                modifier = Modifier.height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = customColor,
                                    activeTrackColor = customColor,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
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
                                if (key == selected) Icon(Icons.Rounded.Check, null,
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
