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

    // ── DB counts ─────────────────────────────────────────────────
    val favoritesCount = db.favoriteDao().count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val historyCount   = db.historyDao().count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val appVersion: String     = BuildConfig.VERSION_NAME
    val githubReleasesUrl: String = BuildConfig.GITHUB_RELEASES_URL

    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update

    init { checkForUpdate() }

    fun checkForUpdate() {
        viewModelScope.launch {
            _update.value = UpdateState(checking = true)
            val info = updater.checkForUpdate(appVersion)
            _update.value = UpdateState(info = info)
        }
    }

    fun downloadUpdate() {
        val url = _update.value.info?.downloadUrl ?: return
        viewModelScope.launch {
            _update.value = _update.value.copy(downloading = true, error = null)
            try {
                updater.downloadAndInstall(url) { p -> _update.value = _update.value.copy(progress = p) }
                _update.value = _update.value.copy(downloading = false, done = true)
            } catch (e: Exception) {
                _update.value = _update.value.copy(downloading = false, error = e.message)
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
}
