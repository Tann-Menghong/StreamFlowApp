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

    suspend fun setTheme(value: String) = context.dataStore.edit { it[THEME_KEY] = value }
    suspend fun setQuality(value: String) = context.dataStore.edit { it[QUALITY_KEY] = value }
    suspend fun setAutoPlay(value: Boolean) = context.dataStore.edit { it[AUTO_PLAY_KEY] = value }
    suspend fun setDataSaver(value: Boolean) = context.dataStore.edit { it[DATA_SAVER_KEY] = value }
    suspend fun setCountry(value: String) = context.dataStore.edit { it[COUNTRY_KEY] = value }
}
