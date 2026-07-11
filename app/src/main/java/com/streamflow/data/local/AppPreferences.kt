package com.streamflow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    companion object {
        val THEME_KEY         = stringPreferencesKey("theme")
        val QUALITY_KEY       = stringPreferencesKey("quality")
        val AUTO_PLAY_KEY     = booleanPreferencesKey("auto_play")
        val DATA_SAVER_KEY    = booleanPreferencesKey("data_saver")
        val COUNTRY_KEY       = stringPreferencesKey("country")
        val ACCENT_KEY        = stringPreferencesKey("accent_color")
        val DEFAULT_SPEED_KEY = stringPreferencesKey("default_speed")
        val HOME_LAYOUT_KEY   = stringPreferencesKey("home_layout")
        // Home display
        val SHOW_CONTINUE_WATCHING_KEY = booleanPreferencesKey("show_continue_watching")
        val SHOW_HERO_CARD_KEY         = booleanPreferencesKey("show_hero_card")
        val GRID_COLUMNS_KEY           = stringPreferencesKey("grid_columns")
        val HOME_CARD_STYLE_KEY        = stringPreferencesKey("home_card_style")
        val HOME_CATEGORIES_KEY        = stringPreferencesKey("home_categories")
        val HIDE_WATCHED_KEY           = booleanPreferencesKey("hide_watched")
        val HIDE_SHORTS_KEY            = booleanPreferencesKey("hide_shorts")
        val NOTIFY_NEW_VIDEOS_KEY      = booleanPreferencesKey("notify_new_videos")
        val VOLUME_BOOST_KEY           = stringPreferencesKey("volume_boost")
        val LANGUAGE_KEY               = stringPreferencesKey("language")
        val FONT_SCALE_KEY             = stringPreferencesKey("font_scale")
        val SHOW_DONGHUA_KEY           = booleanPreferencesKey("show_donghua")
        val START_TAB_KEY              = stringPreferencesKey("start_tab")
        val INCOGNITO_KEY              = booleanPreferencesKey("incognito")
        // Player
        val SKIP_SECONDS_KEY = stringPreferencesKey("skip_seconds")
        val AUDIO_ONLY_KEY   = booleanPreferencesKey("audio_only_mode")
        // "SAME" = use the Wi-Fi quality setting on mobile data too
        val QUALITY_CELLULAR_KEY = stringPreferencesKey("quality_cellular")
        // Days to keep watch history; "0" = forever
        val HISTORY_RETENTION_KEY = stringPreferencesKey("history_retention")
        // Notifications
        val NOTIFY_FREQ_KEY        = stringPreferencesKey("notify_freq")        // hours between checks
        val NOTIFY_MAX_KEY         = stringPreferencesKey("notify_max")         // max notifications per check, "0" = unlimited
        val NOTIFY_APP_UPDATES_KEY = booleanPreferencesKey("notify_app_updates")
        val QUIET_HOURS_KEY        = stringPreferencesKey("quiet_hours")        // "OFF" or "start-end" (24h)
        val LAST_NOTIFIED_VERSION_KEY = stringPreferencesKey("last_notified_version")
        // UI customization
        val CORNER_STYLE_KEY    = stringPreferencesKey("corner_style")      // SQUARE / ROUNDED / ROUND
        val NAV_LABELS_KEY      = stringPreferencesKey("nav_labels")        // ALWAYS / SELECTED / NEVER
        val REDUCE_MOTION_KEY   = booleanPreferencesKey("reduce_motion")
        val HAPTICS_KEY         = booleanPreferencesKey("haptics_enabled")
        val SHOW_CATEGORY_BAR_KEY = booleanPreferencesKey("show_category_bar")
        val PLAYER_GESTURES_KEY = booleanPreferencesKey("player_gestures")
        val CONFIRM_EXIT_KEY    = booleanPreferencesKey("confirm_exit")
        val CUSTOM_CATEGORIES_KEY = stringPreferencesKey("custom_categories") // user-added topic chips
        val SHOW_SEARCH_TAB_KEY = booleanPreferencesKey("show_search_tab")
        val FONT_FAMILY_KEY     = stringPreferencesKey("font_family")        // DEFAULT / SERIF / MONO
        val LIBRARY_TAB_KEY     = stringPreferencesKey("library_tab")        // default Library tab index
        // Search
        val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
        // What's New dialog: last app version the user has seen release notes for
        val LAST_SEEN_VERSION_KEY = stringPreferencesKey("last_seen_version")
        // Playback queue persisted across app restarts (JSON)
        val SAVED_QUEUE_KEY = stringPreferencesKey("saved_queue")

        @Volatile private var INSTANCE: AppPreferences? = null
        fun get(context: Context) = INSTANCE ?: synchronized(this) {
            AppPreferences(context.applicationContext).also { INSTANCE = it }
        }
    }

    val theme    : Flow<String>  = context.dataStore.data.map { it[THEME_KEY]    ?: "DARK" }
    val quality  : Flow<String>  = context.dataStore.data.map { it[QUALITY_KEY]  ?: "AUTO" }
    val autoPlay : Flow<Boolean> = context.dataStore.data.map { it[AUTO_PLAY_KEY] ?: true }
    val dataSaver: Flow<Boolean> = context.dataStore.data.map { it[DATA_SAVER_KEY] ?: false }
    val country  : Flow<String>  = context.dataStore.data.map { it[COUNTRY_KEY]  ?: "US" }
    val accentColor  : Flow<String> = context.dataStore.data.map { it[ACCENT_KEY]         ?: "RED" }
    val defaultSpeed : Flow<String> = context.dataStore.data.map { it[DEFAULT_SPEED_KEY]  ?: "1.0" }
    val homeLayout   : Flow<String> = context.dataStore.data.map { it[HOME_LAYOUT_KEY]    ?: "LIST" }
    // Home display
    val showContinueWatching: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CONTINUE_WATCHING_KEY] ?: true }
    val showHeroCard        : Flow<Boolean> = context.dataStore.data.map { it[SHOW_HERO_CARD_KEY]         ?: true }
    val gridColumns         : Flow<String>  = context.dataStore.data.map { it[GRID_COLUMNS_KEY]           ?: "2" }
    val homeCardStyle       : Flow<String>  = context.dataStore.data.map { it[HOME_CARD_STYLE_KEY]        ?: "COMFORT" }
    val homeCategories      : Flow<List<String>> = context.dataStore.data.map {
        it[HOME_CATEGORIES_KEY]?.split(",")?.filter { c -> c.isNotBlank() }
            ?: listOf("Music", "Gaming", "Sports", "News", "Tech", "Comedy", "Film")
    }
    val hideWatched         : Flow<Boolean> = context.dataStore.data.map { it[HIDE_WATCHED_KEY] ?: false }
    val hideShorts          : Flow<Boolean> = context.dataStore.data.map { it[HIDE_SHORTS_KEY] ?: false }
    val notifyNewVideos     : Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_NEW_VIDEOS_KEY] ?: false }
    val volumeBoost         : Flow<String>  = context.dataStore.data.map { it[VOLUME_BOOST_KEY] ?: "0" }
    val language            : Flow<String>  = context.dataStore.data.map { it[LANGUAGE_KEY] ?: "EN" }
    val fontScale           : Flow<String>  = context.dataStore.data.map { it[FONT_SCALE_KEY] ?: "DEFAULT" }
    val showDonghua         : Flow<Boolean> = context.dataStore.data.map { it[SHOW_DONGHUA_KEY] ?: true }
    val startTab            : Flow<String>  = context.dataStore.data.map { it[START_TAB_KEY] ?: "home" }
    val incognito           : Flow<Boolean> = context.dataStore.data.map { it[INCOGNITO_KEY] ?: false }
    // Player
    val skipSeconds: Flow<String> = context.dataStore.data.map { it[SKIP_SECONDS_KEY] ?: "10" }
    val audioOnlyMode: Flow<Boolean> = context.dataStore.data.map { it[AUDIO_ONLY_KEY] ?: false }
    val qualityCellular: Flow<String> = context.dataStore.data.map { it[QUALITY_CELLULAR_KEY] ?: "SAME" }
    val historyRetention: Flow<String> = context.dataStore.data.map { it[HISTORY_RETENTION_KEY] ?: "0" }
    // Notifications
    val notifyFreq         : Flow<String>  = context.dataStore.data.map { it[NOTIFY_FREQ_KEY] ?: "6" }
    val notifyMax          : Flow<String>  = context.dataStore.data.map { it[NOTIFY_MAX_KEY] ?: "5" }
    val notifyAppUpdates   : Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_APP_UPDATES_KEY] ?: true }
    val quietHours         : Flow<String>  = context.dataStore.data.map { it[QUIET_HOURS_KEY] ?: "OFF" }
    val lastNotifiedVersion: Flow<String>  = context.dataStore.data.map { it[LAST_NOTIFIED_VERSION_KEY] ?: "" }
    // UI customization
    val cornerStyle    : Flow<String>  = context.dataStore.data.map { it[CORNER_STYLE_KEY] ?: "ROUNDED" }
    val navLabels      : Flow<String>  = context.dataStore.data.map { it[NAV_LABELS_KEY] ?: "SELECTED" }
    val reduceMotion   : Flow<Boolean> = context.dataStore.data.map { it[REDUCE_MOTION_KEY] ?: false }
    val hapticsEnabled : Flow<Boolean> = context.dataStore.data.map { it[HAPTICS_KEY] ?: true }
    val showCategoryBar: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CATEGORY_BAR_KEY] ?: true }
    val playerGestures : Flow<Boolean> = context.dataStore.data.map { it[PLAYER_GESTURES_KEY] ?: true }
    val confirmExit    : Flow<Boolean> = context.dataStore.data.map { it[CONFIRM_EXIT_KEY] ?: false }
    val customCategories: Flow<List<String>> = context.dataStore.data.map {
        it[CUSTOM_CATEGORIES_KEY]?.split(",")?.filter { c -> c.isNotBlank() } ?: emptyList()
    }
    val showSearchTab  : Flow<Boolean> = context.dataStore.data.map { it[SHOW_SEARCH_TAB_KEY] ?: false }
    val fontFamily     : Flow<String>  = context.dataStore.data.map { it[FONT_FAMILY_KEY] ?: "DEFAULT" }
    val libraryTab     : Flow<String>  = context.dataStore.data.map { it[LIBRARY_TAB_KEY] ?: "0" }
    // Search
    val recentSearches: Flow<List<String>> = context.dataStore.data.map {
        it[RECENT_SEARCHES_KEY]?.split("|||")?.filter { s -> s.isNotBlank() } ?: emptyList()
    }
    val lastSeenVersion: Flow<Int> = context.dataStore.data.map {
        it[LAST_SEEN_VERSION_KEY]?.toIntOrNull() ?: 0
    }
    // Saved playback queue (restored on app start)
    val savedQueue: Flow<List<com.streamflow.data.model.VideoItem>> = context.dataStore.data.map { prefsMap ->
        try {
            val arr = org.json.JSONArray(prefsMap[SAVED_QUEUE_KEY] ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.streamflow.data.model.VideoItem(
                    url = o.getString("url"),
                    title = o.optString("title"),
                    thumbnailUrl = o.optString("thumbnailUrl"),
                    uploaderName = o.optString("uploaderName"),
                    viewCount = o.optLong("viewCount"),
                    duration = o.optLong("duration")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun setTheme(v: String)    = context.dataStore.edit { it[THEME_KEY]    = v }
    suspend fun setQuality(v: String)  = context.dataStore.edit { it[QUALITY_KEY]  = v }
    suspend fun setAutoPlay(v: Boolean)  = context.dataStore.edit { it[AUTO_PLAY_KEY]  = v }
    suspend fun setDataSaver(v: Boolean) = context.dataStore.edit { it[DATA_SAVER_KEY] = v }
    suspend fun setCountry(v: String)    = context.dataStore.edit { it[COUNTRY_KEY]    = v }
    suspend fun setAccentColor(v: String)  = context.dataStore.edit { it[ACCENT_KEY]         = v }
    suspend fun setDefaultSpeed(v: String) = context.dataStore.edit { it[DEFAULT_SPEED_KEY]  = v }
    suspend fun setHomeLayout(v: String)   = context.dataStore.edit { it[HOME_LAYOUT_KEY]    = v }
    // Home display
    suspend fun setShowContinueWatching(v: Boolean) = context.dataStore.edit { it[SHOW_CONTINUE_WATCHING_KEY] = v }
    suspend fun setShowHeroCard(v: Boolean)         = context.dataStore.edit { it[SHOW_HERO_CARD_KEY]         = v }
    suspend fun setGridColumns(v: String)           = context.dataStore.edit { it[GRID_COLUMNS_KEY]           = v }
    suspend fun setHomeCardStyle(v: String)         = context.dataStore.edit { it[HOME_CARD_STYLE_KEY]        = v }
    suspend fun setHomeCategories(v: List<String>)  = context.dataStore.edit { it[HOME_CATEGORIES_KEY]        = v.joinToString(",") }
    suspend fun setHideWatched(v: Boolean)          = context.dataStore.edit { it[HIDE_WATCHED_KEY]           = v }
    suspend fun setHideShorts(v: Boolean)           = context.dataStore.edit { it[HIDE_SHORTS_KEY]            = v }
    suspend fun setNotifyNewVideos(v: Boolean)      = context.dataStore.edit { it[NOTIFY_NEW_VIDEOS_KEY]      = v }
    suspend fun setVolumeBoost(v: String)           = context.dataStore.edit { it[VOLUME_BOOST_KEY]           = v }
    suspend fun setLanguage(v: String)              = context.dataStore.edit { it[LANGUAGE_KEY]               = v }
    suspend fun setFontScale(v: String)             = context.dataStore.edit { it[FONT_SCALE_KEY]             = v }
    suspend fun setShowDonghua(v: Boolean)          = context.dataStore.edit { it[SHOW_DONGHUA_KEY]           = v }
    suspend fun setStartTab(v: String)              = context.dataStore.edit { it[START_TAB_KEY]              = v }
    suspend fun setIncognito(v: Boolean)            = context.dataStore.edit { it[INCOGNITO_KEY]              = v }
    // Player
    suspend fun setSkipSeconds(v: String) = context.dataStore.edit { it[SKIP_SECONDS_KEY] = v }
    suspend fun setAudioOnlyMode(v: Boolean) = context.dataStore.edit { it[AUDIO_ONLY_KEY] = v }
    suspend fun setQualityCellular(v: String) = context.dataStore.edit { it[QUALITY_CELLULAR_KEY] = v }
    suspend fun setHistoryRetention(v: String) = context.dataStore.edit { it[HISTORY_RETENTION_KEY] = v }
    // Notifications
    suspend fun setNotifyFreq(v: String)          = context.dataStore.edit { it[NOTIFY_FREQ_KEY] = v }
    suspend fun setNotifyMax(v: String)           = context.dataStore.edit { it[NOTIFY_MAX_KEY] = v }
    suspend fun setNotifyAppUpdates(v: Boolean)   = context.dataStore.edit { it[NOTIFY_APP_UPDATES_KEY] = v }
    suspend fun setQuietHours(v: String)          = context.dataStore.edit { it[QUIET_HOURS_KEY] = v }
    suspend fun setLastNotifiedVersion(v: String) = context.dataStore.edit { it[LAST_NOTIFIED_VERSION_KEY] = v }
    // UI customization
    suspend fun setCornerStyle(v: String)      = context.dataStore.edit { it[CORNER_STYLE_KEY] = v }
    suspend fun setNavLabels(v: String)        = context.dataStore.edit { it[NAV_LABELS_KEY] = v }
    suspend fun setReduceMotion(v: Boolean)    = context.dataStore.edit { it[REDUCE_MOTION_KEY] = v }
    suspend fun setHapticsEnabled(v: Boolean)  = context.dataStore.edit { it[HAPTICS_KEY] = v }
    suspend fun setShowCategoryBar(v: Boolean) = context.dataStore.edit { it[SHOW_CATEGORY_BAR_KEY] = v }
    suspend fun setPlayerGestures(v: Boolean)  = context.dataStore.edit { it[PLAYER_GESTURES_KEY] = v }
    suspend fun setConfirmExit(v: Boolean)     = context.dataStore.edit { it[CONFIRM_EXIT_KEY] = v }
    suspend fun setCustomCategories(v: List<String>) = context.dataStore.edit { it[CUSTOM_CATEGORIES_KEY] = v.joinToString(",") }
    suspend fun setShowSearchTab(v: Boolean)   = context.dataStore.edit { it[SHOW_SEARCH_TAB_KEY] = v }
    suspend fun setFontFamily(v: String)       = context.dataStore.edit { it[FONT_FAMILY_KEY] = v }
    suspend fun setLibraryTab(v: String)       = context.dataStore.edit { it[LIBRARY_TAB_KEY] = v }
    suspend fun removeRecentSearch(query: String) = context.dataStore.edit { prefs ->
        val current = prefs[RECENT_SEARCHES_KEY]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
        prefs[RECENT_SEARCHES_KEY] = current.filter { it != query }.joinToString("|||")
    }
    suspend fun setLastSeenVersion(v: Int) = context.dataStore.edit { it[LAST_SEEN_VERSION_KEY] = v.toString() }
    suspend fun saveQueue(items: List<com.streamflow.data.model.VideoItem>) = context.dataStore.edit { prefsMap ->
        val arr = org.json.JSONArray()
        items.take(50).forEach { v ->
            arr.put(org.json.JSONObject()
                .put("url", v.url).put("title", v.title)
                .put("thumbnailUrl", v.thumbnailUrl).put("uploaderName", v.uploaderName)
                .put("viewCount", v.viewCount).put("duration", v.duration))
        }
        prefsMap[SAVED_QUEUE_KEY] = arr.toString()
    }
    // Search
    suspend fun addRecentSearch(query: String) = context.dataStore.edit { prefs ->
        val current = prefs[RECENT_SEARCHES_KEY]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
        val updated = (listOf(query) + current.filter { it != query }).take(8)
        prefs[RECENT_SEARCHES_KEY] = updated.joinToString("|||")
    }
    suspend fun clearRecentSearches() = context.dataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
}
