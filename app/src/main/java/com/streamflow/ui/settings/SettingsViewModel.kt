package com.streamflow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.BuildConfig
import com.streamflow.StreamFlowApp
import com.streamflow.data.UpdateInfo
import com.streamflow.data.UpdateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UpdateState(
    val checking: Boolean = false,
    val info: UpdateInfo? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val done: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs   = (app as StreamFlowApp).prefs
    private val db      = (app as StreamFlowApp).database
    private val updater = UpdateManager(app)

    // ── Existing prefs ────────────────────────────────────────────
    val theme        = prefs.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK")
    val quality      = prefs.quality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val autoPlay     = prefs.autoPlay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val dataSaver    = prefs.dataSaver.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val country      = prefs.country.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "US")
    val accentColor  = prefs.accentColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "RED")
    val defaultSpeed = prefs.defaultSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1.0")
    val homeLayout   = prefs.homeLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "LIST")

    // ── New home display prefs ─────────────────────────────────────
    val showContinueWatching = prefs.showContinueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showHeroCard         = prefs.showHeroCard.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gridColumns          = prefs.gridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "2")

    // ── New player prefs ──────────────────────────────────────────
    val skipSeconds = prefs.skipSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "10")
    val volumeBoost = prefs.volumeBoost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")
    val notifyNewVideos = prefs.notifyNewVideos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val language = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "EN")
    val fontScale = prefs.fontScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DEFAULT")
    val showDonghua = prefs.showDonghua.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showDrama = prefs.showDrama.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showPdTv = prefs.showPdTv.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showMkiss = prefs.showMkiss.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val startTab = prefs.startTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "home")
    val incognito = prefs.incognito.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val qualityCellular = prefs.qualityCellular.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SAME")
    val historyRetention = prefs.historyRetention.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")
    // Notifications
    val notifyFreq       = prefs.notifyFreq.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "6")
    val notifyMax        = prefs.notifyMax.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "5")
    val notifyAppUpdates = prefs.notifyAppUpdates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val quietHours       = prefs.quietHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OFF")
    // UI customization
    val cornerStyle    = prefs.cornerStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ROUNDED")
    val navLabels      = prefs.navLabels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SELECTED")
    val reduceMotion   = prefs.reduceMotion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val hapticsEnabled = prefs.hapticsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val playerGestures = prefs.playerGestures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoPip        = prefs.autoPip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val designStyle    = prefs.designStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MODERN")
    val eqPreset       = prefs.eqPreset.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OFF")
    val batterySaver   = prefs.batterySaver.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoDlWatchLater = prefs.autoDlWatchLater.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val confirmExit    = prefs.confirmExit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showSearchTab  = prefs.showSearchTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val fontFamily     = prefs.fontFamily.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DEFAULT")
    val libraryTab     = prefs.libraryTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")
    // Pro features (v4.2.0)
    val showDislikes   = prefs.showDislikes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val deArrow        = prefs.deArrow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val sponsorCategories = prefs.sponsorCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
        com.streamflow.data.local.AppPreferences.DEFAULT_SPONSOR_CATS)
    val autoBackup     = prefs.autoBackup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val eqBands        = prefs.eqBands.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── On-device AI ──────────────────────────────────────────────
    val aiState = com.streamflow.data.ai.AiEngine.downloadState

    init {
        com.streamflow.data.ai.AiEngine.refreshState(app)
    }

    fun downloadAiModel() {
        // App scope, not viewModelScope: the 550 MB download must keep going
        // when the user leaves the Settings screen
        (getApplication<Application>() as StreamFlowApp).appScope.launch {
            com.streamflow.data.ai.AiEngine.downloadModel(getApplication())
        }
    }

    fun deleteAiModel() {
        if (!com.streamflow.data.ai.AiEngine.deleteModel(getApplication())) {
            android.widget.Toast.makeText(getApplication(),
                "AI is answering right now — try again in a moment",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ── DB counts ─────────────────────────────────────────────────
    val favoritesCount = db.favoriteDao().count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val historyCount   = db.historyDao().count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blockedCount   = db.blockedDao().count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val appVersion: String     = BuildConfig.VERSION_NAME
    val githubReleasesUrl: String = BuildConfig.GITHUB_RELEASES_URL

    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update

    init { checkForUpdate() }

    fun checkForUpdate() {
        viewModelScope.launch {
            _update.value = UpdateState(checking = true)
            try {
                val info = updater.checkForUpdate(appVersion)
                _update.value = UpdateState(info = info)
            } catch (e: Exception) {
                // Sentinel the About row checks so a failed check (offline,
                // rate-limited) doesn't read as "Up to date"
                _update.value = UpdateState(error = "check_failed")
            }
        }
    }

    fun downloadUpdate() {
        val url = _update.value.info?.downloadUrl ?: return
        viewModelScope.launch {
            // progress = 0: a retry after a failure used to start the bar at the
            // old stuck percentage instead of from the beginning
            _update.value = _update.value.copy(downloading = true, error = null, progress = 0)
            try {
                updater.downloadAndInstall(url) { p -> _update.value = _update.value.copy(progress = p) }
                _update.value = _update.value.copy(downloading = false, done = true)
            } catch (e: Exception) {
                _update.value = _update.value.copy(downloading = false, error = e.message)
                // The banner just flips back to the button on failure — say why
                android.widget.Toast.makeText(getApplication(),
                    "Update download failed — check your connection and try again",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setTheme(v: String)          = viewModelScope.launch { prefs.setTheme(v) }
    fun setQuality(v: String)        = viewModelScope.launch { prefs.setQuality(v) }
    fun setAutoPlay(v: Boolean)      = viewModelScope.launch { prefs.setAutoPlay(v) }
    fun setDataSaver(v: Boolean)     = viewModelScope.launch { prefs.setDataSaver(v) }
    fun setCountry(v: String)        = viewModelScope.launch { prefs.setCountry(v) }
    fun setAccentColor(v: String)    = viewModelScope.launch { prefs.setAccentColor(v) }
    fun setDefaultSpeed(v: String)   = viewModelScope.launch { prefs.setDefaultSpeed(v) }
    fun setHomeLayout(v: String)     = viewModelScope.launch { prefs.setHomeLayout(v) }
    fun setShowContinueWatching(v: Boolean) = viewModelScope.launch { prefs.setShowContinueWatching(v) }
    fun setShowHeroCard(v: Boolean)  = viewModelScope.launch { prefs.setShowHeroCard(v) }
    fun setGridColumns(v: String)    = viewModelScope.launch { prefs.setGridColumns(v) }
    fun setSkipSeconds(v: String)    = viewModelScope.launch { prefs.setSkipSeconds(v) }
    fun clearHistory()   = viewModelScope.launch { db.historyDao().clearAll() }
    fun clearFavorites() = viewModelScope.launch { db.favoriteDao().clearAll() }
    fun clearBlocked()   = viewModelScope.launch { db.blockedDao().clearAll() }
    fun setVolumeBoost(v: String)        = viewModelScope.launch { prefs.setVolumeBoost(v) }
    fun setNotifyNewVideos(v: Boolean)   = viewModelScope.launch { prefs.setNotifyNewVideos(v) }
    // Notifications
    fun setNotifyMax(v: String)          = viewModelScope.launch { prefs.setNotifyMax(v) }
    fun setNotifyAppUpdates(v: Boolean)  = viewModelScope.launch { prefs.setNotifyAppUpdates(v) }
    fun setQuietHours(v: String)         = viewModelScope.launch { prefs.setQuietHours(v) }
    fun setNotifyFreq(v: String) = viewModelScope.launch {
        prefs.setNotifyFreq(v)
        // Reschedule the periodic check immediately with the new interval
        try {
            androidx.work.WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
                com.streamflow.data.NewVideosWorker.WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                androidx.work.PeriodicWorkRequestBuilder<com.streamflow.data.NewVideosWorker>(
                    v.toLongOrNull() ?: 6L, java.util.concurrent.TimeUnit.HOURS
                ).setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
                ).build()
            )
        } catch (_: Exception) {}
    }
    // UI customization
    fun setCornerStyle(v: String)     = viewModelScope.launch { prefs.setCornerStyle(v) }
    fun setNavLabels(v: String)       = viewModelScope.launch { prefs.setNavLabels(v) }
    fun setReduceMotion(v: Boolean)   = viewModelScope.launch { prefs.setReduceMotion(v) }
    fun setHapticsEnabled(v: Boolean) = viewModelScope.launch { prefs.setHapticsEnabled(v) }
    fun setPlayerGestures(v: Boolean) = viewModelScope.launch { prefs.setPlayerGestures(v) }
    fun setAutoPip(v: Boolean)        = viewModelScope.launch { prefs.setAutoPip(v) }
    fun setDesignStyle(v: String)     = viewModelScope.launch { prefs.setDesignStyle(v) }
    fun setEqPreset(v: String)        = viewModelScope.launch { prefs.setEqPreset(v) }
    fun setBatterySaver(v: Boolean)   = viewModelScope.launch { prefs.setBatterySaver(v) }
    fun setAutoDlWatchLater(v: Boolean) = viewModelScope.launch { prefs.setAutoDlWatchLater(v) }
    fun setConfirmExit(v: Boolean)    = viewModelScope.launch { prefs.setConfirmExit(v) }
    fun setShowSearchTab(v: Boolean)  = viewModelScope.launch { prefs.setShowSearchTab(v) }
    fun setFontFamily(v: String)      = viewModelScope.launch { prefs.setFontFamily(v) }
    fun setLibraryTab(v: String)      = viewModelScope.launch { prefs.setLibraryTab(v) }
    fun setLanguage(v: String)           = viewModelScope.launch { prefs.setLanguage(v) }
    fun setFontScale(v: String)          = viewModelScope.launch { prefs.setFontScale(v) }
    fun setShowDonghua(v: Boolean)       = viewModelScope.launch { prefs.setShowDonghua(v) }
    fun setShowDrama(v: Boolean)         = viewModelScope.launch { prefs.setShowDrama(v) }
    fun setShowPdTv(v: Boolean)          = viewModelScope.launch { prefs.setShowPdTv(v) }
    fun setShowMkiss(v: Boolean)         = viewModelScope.launch { prefs.setShowMkiss(v) }
    fun setShowDislikes(v: Boolean)      = viewModelScope.launch { prefs.setShowDislikes(v) }
    fun setDeArrow(v: Boolean)           = viewModelScope.launch { prefs.setDeArrow(v) }
    fun setSponsorCategories(v: Set<String>) = viewModelScope.launch { prefs.setSponsorCategories(v) }
    fun setEqBands(v: List<Int>)         = viewModelScope.launch { prefs.setEqBands(v) }
    fun setAutoBackup(v: Boolean) = viewModelScope.launch {
        prefs.setAutoBackup(v)
        val wm = androidx.work.WorkManager.getInstance(getApplication())
        if (v) {
            wm.enqueueUniquePeriodicWork(
                com.streamflow.data.AutoBackupWorker.WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                androidx.work.PeriodicWorkRequestBuilder<com.streamflow.data.AutoBackupWorker>(
                    7, java.util.concurrent.TimeUnit.DAYS).build()
            )
        } else {
            wm.cancelUniqueWork(com.streamflow.data.AutoBackupWorker.WORK_NAME)
        }
    }
    fun setStartTab(v: String)           = viewModelScope.launch { prefs.setStartTab(v) }
    fun setIncognito(v: Boolean)         = viewModelScope.launch { prefs.setIncognito(v) }
    fun setQualityCellular(v: String)    = viewModelScope.launch { prefs.setQualityCellular(v) }
    fun setHistoryRetention(v: String)   = viewModelScope.launch { prefs.setHistoryRetention(v) }

    // ── Import subscriptions from a Google Takeout CSV or NewPipe JSON export ──
    fun importSubscriptions(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var imported = 0
            try {
                val text = app.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception("empty")
                val trimmed = text.trimStart()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    // NewPipe subscriptions export: {"subscriptions":[{"url":..,"name":..}]}
                    // A bare top-level array previously crashed JSONObject(...) and the
                    // whole import failed despite the startsWith("[") check
                    val arr = if (trimmed.startsWith("["))
                        org.json.JSONArray(trimmed)
                    else
                        org.json.JSONObject(trimmed).optJSONArray("subscriptions") ?: org.json.JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val url = o.optString("url")
                        if (url.contains("youtube.com")) {
                            db.subscriptionDao().insert(com.streamflow.data.local.entity.SubscriptionEntity(
                                channelUrl = url, name = o.optString("name"), avatarUrl = ""))
                            imported++
                        }
                    }
                } else {
                    // Google Takeout subscriptions.csv: "Channel Id,Channel Url,Channel Title"
                    text.lineSequence().drop(1).forEach { line ->
                        if (line.isBlank()) return@forEach
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            val id = parts[0].trim().trim('"')
                            val name = parts.drop(2).joinToString(",").trim().trim('"')
                                .ifBlank { id }
                            if (id.isNotBlank()) {
                                db.subscriptionDao().insert(com.streamflow.data.local.entity.SubscriptionEntity(
                                    channelUrl = "https://www.youtube.com/channel/$id",
                                    name = name, avatarUrl = ""))
                                imported++
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(app,
                    if (imported > 0) "Subscribed to $imported channels"
                    else "Import failed — use a Takeout CSV or NewPipe JSON export",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── OPML export: standard subscriptions format for RSS/podcast apps ──────
    fun exportOpml(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = try {
                fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;")
                val subs = db.subscriptionDao().getAllOnce()
                val sb = StringBuilder()
                sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                sb.append("<opml version=\"1.1\">\n<head><title>StreamFlow subscriptions</title></head>\n")
                sb.append("<body>\n<outline text=\"YouTube\" title=\"YouTube\">\n")
                subs.forEach { s ->
                    // Channel-id URLs get the official RSS feed; handle/custom
                    // URLs fall back to the channel page link
                    val id = s.channelUrl.substringAfter("/channel/", "").substringBefore("/").substringBefore("?")
                    val feed = if (id.isNotBlank())
                        "https://www.youtube.com/feeds/videos.xml?channel_id=$id"
                    else s.channelUrl
                    val name = esc(s.name.ifBlank { s.channelUrl })
                    sb.append("  <outline text=\"$name\" title=\"$name\" type=\"rss\" " +
                        "xmlUrl=\"${esc(feed)}\" htmlUrl=\"${esc(s.channelUrl)}\"/>\n")
                }
                sb.append("</outline>\n</body>\n</opml>\n")
                app.contentResolver.openOutputStream(uri)?.use {
                    it.write(sb.toString().toByteArray())
                } != null
            } catch (_: Exception) { false }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(app,
                    if (ok) "OPML exported" else "Export failed",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Backup & restore (JSON via Storage Access Framework) ──────
    fun exportBackup(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = try {
                // JSON building lives in BackupManager, shared with the weekly
                // AutoBackupWorker so the two can never drift apart field-wise
                val root = com.streamflow.data.BackupManager.buildBackupJson(db)
                app.contentResolver.openOutputStream(uri)?.use {
                    it.write(root.toString(2).toByteArray())
                } != null
            } catch (_: Exception) { false }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(app,
                    if (ok) "Backup saved" else "Backup failed",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importBackup(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var restored = 0
            val ok = try {
                val text = app.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception("empty")
                val root = org.json.JSONObject(text)

                root.optJSONArray("subscriptions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.subscriptionDao().insert(com.streamflow.data.local.entity.SubscriptionEntity(
                            channelUrl = o.getString("channelUrl"), name = o.optString("name"),
                            avatarUrl = o.optString("avatarUrl"),
                            subscribedAt = o.optLong("subscribedAt", System.currentTimeMillis()),
                            groupName = o.optString("groupName"),
                            notify = o.optBoolean("notify", true)))
                        restored++
                    }
                }
                root.optJSONArray("favorites")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.favoriteDao().insert(com.streamflow.data.local.entity.FavoriteEntity(
                            url = o.getString("url"), title = o.optString("title"),
                            thumbnailUrl = o.optString("thumbnailUrl"), uploaderName = o.optString("uploaderName"),
                            viewCount = o.optLong("viewCount"), duration = o.optLong("duration"),
                            savedAt = o.optLong("savedAt", System.currentTimeMillis())))
                        restored++
                    }
                }
                root.optJSONArray("watchLater")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.watchLaterDao().insert(com.streamflow.data.local.entity.WatchLaterEntity(
                            url = o.getString("url"), title = o.optString("title"),
                            thumbnailUrl = o.optString("thumbnailUrl"), uploaderName = o.optString("uploaderName"),
                            viewCount = o.optLong("viewCount"), duration = o.optLong("duration"),
                            addedAt = o.optLong("addedAt", System.currentTimeMillis())))
                        restored++
                    }
                }
                root.optJSONArray("history")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.historyDao().insert(com.streamflow.data.local.entity.HistoryEntity(
                            url = o.getString("url"), title = o.optString("title"),
                            thumbnailUrl = o.optString("thumbnailUrl"), uploaderName = o.optString("uploaderName"),
                            viewCount = o.optLong("viewCount"), duration = o.optLong("duration"),
                            watchedAt = o.optLong("watchedAt", System.currentTimeMillis()),
                            position = o.optLong("position")))
                        restored++
                    }
                }
                root.optJSONArray("blocked")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.blockedDao().insert(com.streamflow.data.local.entity.BlockedItemEntity(
                            itemKey = o.getString("itemKey"), type = o.optString("type", "VIDEO"),
                            name = o.optString("name")))
                        restored++
                    }
                }
                root.optJSONArray("playlists")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = db.playlistDao().create(com.streamflow.data.local.entity.PlaylistEntity(
                            name = o.optString("name", "Imported")))
                        o.optJSONArray("items")?.let { itemsArr ->
                            for (j in 0 until itemsArr.length()) {
                                val it2 = itemsArr.getJSONObject(j)
                                db.playlistDao().addItem(com.streamflow.data.local.entity.PlaylistItemEntity(
                                    playlistId = id, url = it2.getString("url"),
                                    title = it2.optString("title"), thumbnailUrl = it2.optString("thumbnailUrl"),
                                    uploaderName = it2.optString("uploaderName"), duration = it2.optLong("duration"),
                                    // Fallback preserves the backup's ordering (j) when
                                    // restoring an older backup without addedAt
                                    addedAt = it2.optLong("addedAt", System.currentTimeMillis() + j)))
                            }
                        }
                        restored++
                    }
                }
                root.optJSONArray("bookmarks")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.bookmarkDao().insert(com.streamflow.data.local.entity.BookmarkEntity(
                            videoUrl = o.getString("videoUrl"), title = o.optString("title"),
                            thumbnailUrl = o.optString("thumbnailUrl"),
                            uploaderName = o.optString("uploaderName"),
                            positionMs = o.optLong("positionMs"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())))
                        restored++
                    }
                }
                true
            } catch (_: Exception) { false }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(app,
                    if (ok) "Restored $restored items" else "Import failed — not a StreamFlow backup?",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
