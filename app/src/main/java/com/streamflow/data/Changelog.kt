package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.7.0"

    val notes = listOf(
        "Playback now recovers on its own when the network drops 📶 — it waits for signal to come back, retries with growing pauses, and resumes from exactly where it stopped",
        "Fixed the big one: audio or video stopping for good after you left the app. Expired stream links are now detected and refreshed automatically instead of ending playback",
        "The next episode plays automatically even with the screen off or the app closed — auto-play used to only work while you were actually watching the player",
        "The next video is prepared about 25 seconds before the current one ends, so episodes run into each other instead of pausing to load",
        "Your position is saved every few seconds now, so a phone that kills the app in the background no longer restarts the episode from zero",
        "A queued video that has been deleted, made private or blocked is skipped instead of silently ending the whole queue",
        "\"Buffering\" now tells you the truth: Reconnecting 2/5, Waiting for network, or a clear message with a Try again button when it genuinely cannot recover",
        "Lighter on older phones like the Galaxy Note 9 — buffer memory is now capped per device instead of being allowed to grow past 100 MB on any phone",
        "Much easier on the battery when listening with the screen off: the player was waking the phone four times a second to update a screen nobody was looking at",
        "Fixed: the custom Equalizer always opened with every slider at 0 dB, and pressing Apply wiped the band levels you had saved"
    )
}
