package com.streamflow.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// App-wide sleep timer. Lives outside the player composable because the player
// screen is recreated on every autoplay/related-video switch — a remember{}-based
// timer silently died there, letting playback run all night. PlaybackService
// enforces the deadline; the UI only displays and sets it.
object SleepTimer {
    private val _deadlineAt = MutableStateFlow(0L) // epoch ms; 0 = off
    val deadlineAt: StateFlow<Long> = _deadlineAt

    // "Stop when this video ends" mode — enforced by PlaybackService on
    // STATE_ENDED, and it also suppresses autoplay/queue advance in the player
    private val _endOfVideo = MutableStateFlow(false)
    val endOfVideo: StateFlow<Boolean> = _endOfVideo

    // The minutes choice backing the deadline, for menu highlighting
    var activeMinutes = 0
        private set

    fun set(minutes: Int) {
        activeMinutes = minutes.coerceAtLeast(0)
        _endOfVideo.value = false
        _deadlineAt.value =
            if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
    }

    fun setEndOfVideo() {
        activeMinutes = 0
        _deadlineAt.value = 0L
        _endOfVideo.value = true
    }

    fun clear() {
        activeMinutes = 0
        _deadlineAt.value = 0L
        _endOfVideo.value = false
    }
}
