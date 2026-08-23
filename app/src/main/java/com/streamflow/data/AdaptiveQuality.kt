package com.streamflow.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The quality the APP has imposed on itself, as observable state.
 *
 * This was two private fields inside PlaybackService, which made the automatic
 * step-down invisible and, worse, unreachable. Two bugs followed directly from
 * that, and both were reported by the app as if nothing had happened:
 *
 *  1. After a step-down the player's quality button still showed the height the
 *     PLAYER SCREEN had extracted, because the service re-extracted behind it
 *     and had no way to say so. The user saw "Auto (1080p)" over a 480p picture
 *     -- and a toast, seconds earlier, saying it had been lowered to 480p.
 *
 *  2. Choosing a quality from the in-player menu only cleared the override if
 *     it CHANGED the stored preference. Someone whose preference was already
 *     1080p, watching a stream the app had stepped down, would tap 1080p, watch
 *     it come back at 1080p, and then be silently dropped to 480p again at the
 *     next re-extract -- because the override was still in force and nothing
 *     had contradicted it. The one gesture that most clearly means "stop
 *     managing this for me" was the one gesture that did not.
 *
 * Holding it here fixes both: the UI can see the override, and anyone can clear
 * it. The service still decides WHEN to step, which is the part that needs the
 * player.
 */
object AdaptiveQuality {

    /**
     * @param rung     the ceiling the app is currently imposing, or null when it
     *                 is imposing nothing and the user's preference stands alone
     * @param baseline the preference [rung] was measured against, so that the
     *                 user later changing that preference cancels the override
     */
    data class State(val rung: String? = null, val baseline: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val rung: String? get() = _state.value.rung
    val baseline: String? get() = _state.value.baseline

    /** The link could not carry [baseline]; hold it at [rung] for now. */
    fun lower(rung: String, baseline: String) {
        _state.value = State(rung = rung, baseline = baseline)
    }

    /** The link recovered: give one rung back, keeping the same baseline. */
    fun raise(rung: String) {
        _state.value = _state.value.copy(rung = rung)
    }

    /**
     * Stop imposing anything.
     *
     * Called when the user picks a quality (an explicit instruction outranks
     * any measurement), when the preference changes underneath the override,
     * and when the network changes -- the link that justified it is no longer
     * the link in use.
     */
    fun clear() {
        if (_state.value.rung != null || _state.value.baseline != null) {
            _state.value = State()
        }
    }
}
