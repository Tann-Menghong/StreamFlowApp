package com.streamflow.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MiniPlayerData(
    val url: String = "",
    val title: String = "",
    val thumbnailUrl: String = "",
    val uploaderName: String = "",
    val isPlaying: Boolean = false
)

object MiniPlayerState {
    private val _data = MutableStateFlow(MiniPlayerData())
    val data: StateFlow<MiniPlayerData> = _data

    fun update(data: MiniPlayerData) { _data.value = data }
    fun clear() { _data.value = MiniPlayerData() }
}
