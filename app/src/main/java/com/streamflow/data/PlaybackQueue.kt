package com.streamflow.data

import com.streamflow.data.local.AppPreferences
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object PlaybackQueue {
    private val _queue = MutableStateFlow<List<VideoItem>>(emptyList())
    val queue: StateFlow<List<VideoItem>> = _queue

    // Restore the saved queue on app start, then persist every change so the
    // queue survives app restarts
    fun bind(prefs: AppPreferences, scope: CoroutineScope) {
        scope.launch {
            val saved = try { prefs.savedQueue.first() } catch (_: Exception) { emptyList() }
            if (_queue.value.isEmpty() && saved.isNotEmpty()) _queue.value = saved
            queue.drop(1).collect { items ->
                try { prefs.saveQueue(items) } catch (_: Exception) {}
            }
        }
    }

    fun add(video: VideoItem) {
        if (_queue.value.any { it.url == video.url }) return
        _queue.value = _queue.value + video
    }

    // Bulk replace for "Play all": one state emission (and one persisted write)
    // instead of one per video — add() in a loop hammered DataStore N times
    fun setAll(videos: List<VideoItem>) {
        _queue.value = videos.distinctBy { it.url }
    }

    // Puts the video at the front of the queue (it plays right after the current one)
    fun addNext(video: VideoItem) {
        _queue.value = listOf(video) + _queue.value.filter { it.url != video.url }
    }

    fun remove(index: Int) {
        _queue.value = _queue.value.toMutableList().also {
            if (index in it.indices) it.removeAt(index)
        }
    }

    fun move(from: Int, to: Int) {
        val list = _queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        _queue.value = list
    }

    fun shuffle() {
        if (_queue.value.size > 1) _queue.value = _queue.value.shuffled()
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
