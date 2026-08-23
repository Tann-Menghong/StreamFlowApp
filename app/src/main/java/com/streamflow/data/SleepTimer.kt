package com.streamflow.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// App-wide sleep timer. Lives outside the player composable because the player
// screen is recreated on every autoplay/related-video switch — a remember{}-based
// timer silently died there, letting playback run all night. PlaybackService
// enforces the deadline; the UI only displays and sets it.
object SleepTimer {
    /**
     * elapsedRealtime ms, NOT epoch ms; 0 = off.
     *
     * This used to be System.currentTimeMillis(). A sleep timer is a DURATION,
     * and the wall clock is not a duration source: an NTP correction, crossing a
     * timezone with automatic time on, or the user editing the clock all move it
     * underneath a running timer. Both readers computed `deadline - now` against
     * that moving reference, so a clock that jumped forward fired the timer
     * early -- pausing playback in the user's face -- and one that jumped back
     * pushed it out, which is the failure the timer exists to prevent.
     *
     * elapsedRealtime counts monotonically from boot, is immune to every one of
     * those, and (unlike uptimeMillis) keeps counting while the device sleeps.
     */
    private val _deadlineAt = MutableStateFlow(0L)
    val deadlineAt: StateFlow<Long> = _deadlineAt

    /** The clock both the service deadline and the on-screen countdown read.
     *  One accessor so the two can never drift onto different clocks again. */
    fun now(): Long = android.os.SystemClock.elapsedRealtime()

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
            if (minutes <= 0) 0L else now() + minutes * 60_000L
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
