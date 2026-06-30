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
        val THEME_KEY = stringPreferencesKey("theme")
        val QUALITY_KEY = stringPreferencesKey("quality")
        val AUTO_PLAY_KEY = booleanPreferencesKey("auto_play")
        val DATA_SAVER_KEY = booleanPreferencesKey("data_saver")
        val COUNTRY_KEY = stringPreferencesKey("country")
        val ACCENT_KEY = stringPreferencesKey("accent_color")
        val DEFAULT_SPEED_KEY = stringPreferencesKey("default_speed")
        val HOME_LAYOUT_KEY = stringPreferencesKey("home_layout")

        @Volatile private var INSTANCE: AppPreferences? = null
        fun get(context: Context) = INSTANCE ?: synchronized(this) {
            AppPreferences(context.applicationContext).also { INSTANCE = it }
        }
    }

    val theme: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "DARK" }
    val quality: Flow<String> = context.dataStore.data.map { it[QUALITY_KEY] ?: "AUTO" }
    val autoPlay: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PLAY_KEY] ?: true }
    val dataSaver: Flow<Boolean> = context.dataStore.data.map { it[DATA_SAVER_KEY] ?: false }
    val country: Flow<String> = context.dataStore.data.map { it[COUNTRY_KEY] ?: "US" }
    val accentColor: Flow<String> = context.dataStore.data.map { it[ACCENT_KEY] ?: "RED" }
    val defaultSpeed: Flow<String> = context.dataStore.data.map { it[DEFAULT_SPEED_KEY] ?: "1.0" }
    val homeLayout: Flow<String> = context.dataStore.data.map { it[HOME_LAYOUT_KEY] ?: "LIST" }

    suspend fun setTheme(value: String) = context.dataStore.edit { it[THEME_KEY] = value }
    suspend fun setQuality(value: String) = context.dataStore.edit { it[QUALITY_KEY] = value }
    suspend fun setAutoPlay(value: Boolean) = context.dataStore.edit { it[AUTO_PLAY_KEY] = value }
    suspend fun setDataSaver(value: Boolean) = context.dataStore.edit { it[DATA_SAVER_KEY] = value }
    suspend fun setCountry(value: String) = context.dataStore.edit { it[COUNTRY_KEY] = value }
    suspend fun setAccentColor(value: String) = context.dataStore.edit { it[ACCENT_KEY] = value }
    suspend fun setDefaultSpeed(value: String) = context.dataStore.edit { it[DEFAULT_SPEED_KEY] = value }
    suspend fun setHomeLayout(value: String) = context.dataStore.edit { it[HOME_LAYOUT_KEY] = value }
}
