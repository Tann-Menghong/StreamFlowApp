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
        // Player
        val SKIP_SECONDS_KEY = stringPreferencesKey("skip_seconds")
        // Search
        val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")

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
    // Player
    val skipSeconds: Flow<String> = context.dataStore.data.map { it[SKIP_SECONDS_KEY] ?: "10" }
    // Search
    val recentSearches: Flow<List<String>> = context.dataStore.data.map {
        it[RECENT_SEARCHES_KEY]?.split("|||")?.filter { s -> s.isNotBlank() } ?: emptyList()
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
    // Player
    suspend fun setSkipSeconds(v: String) = context.dataStore.edit { it[SKIP_SECONDS_KEY] = v }
    // Search
    suspend fun addRecentSearch(query: String) = context.dataStore.edit { prefs ->
        val current = prefs[RECENT_SEARCHES_KEY]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
        val updated = (listOf(query) + current.filter { it != query }).take(8)
        prefs[RECENT_SEARCHES_KEY] = updated.joinToString("|||")
    }
    suspend fun clearRecentSearches() = context.dataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
}
