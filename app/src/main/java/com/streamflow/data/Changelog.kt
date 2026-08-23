package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.10.0"

    val notes = listOf(
        "Videos no longer get stuck on a loading screen 🔄 — StreamFlow now watches whether video is actually arriving, not just how long it has been waiting. A stream that has gone silent is retried within seconds instead of spinning forever, and one that is merely slow is given more time to finish loading rather than being cut off",
        "You can finally see that a video is starting. The spinner only appeared once playback had already begun, so the slowest part of opening a video showed nothing at all — just a black frame and no way to tell whether anything was happening",
        "Quality that drops on a weak signal now comes back. A twenty-second dead spot used to pin the rest of a long video to the lowest quality with no way to undo it short of restarting the app; StreamFlow now notices when your connection has recovered and quietly restores it",
        "Quality drops before the video stops, not after. It used to wait for three stalls — meaning you watched the video freeze three times before anything happened — and now acts on the buffer running low",
        "Fixed: recovering from a dropped connection could restart a long video from the beginning instead of resuming where it stopped",
        "Fixed: a video that could not be recovered kept retrying forever, quietly using data and battery in your pocket. Recovery now stops and tells you, with a button to try again",
        "Fixed: skipping forward or scrubbing on perfect Wi-Fi was mistaken for a weak connection, so a few double-taps told you your signal was too slow and interrupted the video to \"fix\" it",
        "Fixed: on a video only uploaded in low quality, \"lowering quality for a smoother stream\" reloaded the identical stream — an interruption and a message for no change at all"
    )
}
