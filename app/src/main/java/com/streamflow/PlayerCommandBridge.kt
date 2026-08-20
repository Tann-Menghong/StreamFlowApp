package com.streamflow

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Makes the standard "next track" / "previous track" commands work.
 *
 * StreamFlow plays exactly one MediaItem at a time — the queue and related-video
 * autoplay are app-managed rather than an ExoPlayer playlist. A consequence
 * nobody had chased down: with a single item loaded, ExoPlayer never reports
 * COMMAND_SEEK_TO_NEXT as available, so every controller that speaks the
 * standard media vocabulary gets nothing when the user asks for the next video.
 *
 * The service worked around half of that with a custom SessionCommand, which
 * put buttons in our own notification layout. But a custom command can only be
 * invoked by something that knows the command exists — and a Bluetooth headset,
 * a car head unit, Android Auto and Wear all speak plain AVRCP / media-button
 * vocabulary instead. So on the surface where it matters most, the one where
 * the phone is in a pocket, the next button did nothing at all.
 *
 * This wrapper advertises the two commands and routes them into the service's
 * existing playNext() / playPrevious(), which already carry the `advancing`
 * guard, the queue-then-related fallback and the play-history back-stack. No
 * behaviour is reimplemented here; it is only made reachable.
 *
 * Note the notification consequence: media3's notification provider renders
 * native prev/next as soon as these commands are available, so PlaybackService
 * no longer supplies its own pair in the custom layout — otherwise the
 * notification would show two of each.
 */
class PlayerCommandBridge(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

    // getAvailableCommands() is not the only path: controllers also ask this
    // directly, and ForwardingPlayer would otherwise answer from the wrapped
    // player, which still says no.
    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_NEXT ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            super.isCommandAvailable(command)

    // There is always a "next" as far as the outside world is concerned — the
    // queue, or a related video. When there is genuinely nothing, advance()
    // fails silently, which is the right behaviour for a phone in a pocket.
    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = onNext()
    override fun seekToNextMediaItem() = onNext()

    /**
     * Matches the convention every other player follows: past the first few
     * seconds, "previous" restarts the current video, and only a second press
     * goes back. A car's back button behaves the way the driver expects, and
     * an accidental press does not lose their place.
     */
    override fun seekToPrevious() {
        val pos = try { currentPosition } catch (_: Exception) { 0L }
        if (pos > RESTART_THRESHOLD_MS && isCurrentMediaItemSeekable) seekTo(0L)
        else onPrevious()
    }

    override fun seekToPreviousMediaItem() = onPrevious()

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
