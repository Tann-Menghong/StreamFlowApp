package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.8.0"

    val notes = listOf(
        "The notification Next ⏭️ button is smarter — it keeps your chosen playback speed, handles live streams and downloads, won't reload the current video, and tells you when there's nothing up next",
        "Shorts play better 🎬 — faster start, smoother decoding on tricky videos, and scrolling back replays from cache instead of re-downloading",
        "Shorts now pause when you unplug your headphones instead of blasting the speaker",
        "Small reliability tidy-ups under the hood"
    )
}
