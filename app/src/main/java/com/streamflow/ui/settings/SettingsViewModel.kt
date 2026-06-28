package com.streamflow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.BuildConfig
import com.streamflow.StreamFlowApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = (app as StreamFlowApp).prefs
    private val db = (app as StreamFlowApp).database

    val theme: StateFlow<String> = prefs.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK")

    val quality: StateFlow<String> = prefs.quality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")

    val autoPlay: StateFlow<Boolean> = prefs.autoPlay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dataSaver: StateFlow<Boolean> = prefs.dataSaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val favoritesCount: StateFlow<Int> = db.favoriteDao().count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val historyCount: StateFlow<Int> = db.historyDao().count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val appVersion: String = BuildConfig.VERSION_NAME
    val githubReleasesUrl: String = BuildConfig.GITHUB_RELEASES_URL

    fun setTheme(value: String) = viewModelScope.launch { prefs.setTheme(value) }
    fun setQuality(value: String) = viewModelScope.launch { prefs.setQuality(value) }
    fun setAutoPlay(value: Boolean) = viewModelScope.launch { prefs.setAutoPlay(value) }
    fun setDataSaver(value: Boolean) = viewModelScope.launch { prefs.setDataSaver(value) }
    fun clearHistory() = viewModelScope.launch { db.historyDao().clearAll() }
    fun clearFavorites() = viewModelScope.launch { db.favoriteDao().clearAll() }
}
