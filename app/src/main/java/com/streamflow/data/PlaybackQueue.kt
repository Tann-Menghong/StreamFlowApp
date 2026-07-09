package com.streamflow.data

import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PlaybackQueue {
    private val _queue = MutableStateFlow<List<VideoItem>>(emptyList())
    val queue: StateFlow<List<VideoItem>> = _queue

    fun add(video: VideoItem) { _queue.value = _queue.value + video }

    fun remove(index: Int) {
        _queue.value = _queue.value.toMutableList().also {
            if (index in it.indices) it.removeAt(index)
        }
    }

    fun popNext(): VideoItem? {
        val list = _queue.value.toMutableList()
        if (list.isEmpty()) return null
        val item = list.removeAt(0)
        _queue.value = list
        return item
    }

    fun hasNext() = _queue.value.isNotEmpty()

    fun clear() { _queue.value = emptyList() }
}
