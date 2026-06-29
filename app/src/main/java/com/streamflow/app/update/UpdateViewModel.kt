package com.streamflow.app.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val checking: Boolean = false,
    val available: UpdateInfo? = null,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val upToDate: Boolean = false,
    val error: String? = null
)

class UpdateViewModel(
    private val updateManager: UpdateManager,
    private val currentVersionName: String
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdate(announceUpToDate: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null) }
            val info = runCatching { updateManager.checkForUpdate(currentVersionName) }.getOrNull()
            _state.update {
                it.copy(checking = false, available = info, upToDate = announceUpToDate && info == null)
            }
        }
    }

    fun startUpdate(context: Context) {
        val info = _state.value.available ?: return
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, downloadProgress = 0f, error = null) }
            runCatching {
                val apkFile = updateManager.download(context, info.downloadUrl) { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                }
                updateManager.install(context, apkFile)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Update failed") }
            }
            _state.update { it.copy(downloading = false, downloadProgress = 0f) }
        }
    }

    fun dismiss() {
        _state.update { it.copy(available = null) }
    }

    fun dismissUpToDate() {
        _state.update { it.copy(upToDate = false) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
