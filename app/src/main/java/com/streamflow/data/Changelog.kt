package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.16.0"

    val notes = listOf(
        "The video engine moved up again 🎬 — Media3 1.4.1 to 1.8.1, with the build tools it needed. Please tell me if you see ANY playback problem; v6.15.0 is the known-good version to fall back to",
        "No devices lose support. The next version of the engine (1.9) requires Android 6.0, which would drop every Android 5.0 and 5.1 phone still running StreamFlow — that is a decision about who loses the app, not a routine update, so it was not taken. StreamFlow still runs on Android 5.0",
        "Fixed before it could bite: the new engine hides the media notification while the player is idle — which is exactly where StreamFlow sits while it waits for your connection to come back, for up to two minutes. Your controls would have vanished at the moment the app was working hardest to recover. The old behaviour is kept",
        "A broken subtitle or metadata track now switches off just that track instead of stopping the video",
        "Further fixes for live streams, and live streams no longer send a seek position to car head units, which was causing display glitches in Android Auto"
    )
}
