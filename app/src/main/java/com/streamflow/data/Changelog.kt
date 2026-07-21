package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.0"

    val notes = listOf(
        "FOUND IT 🎯 — the ad blocking never ran inside the video player's own frame, which is exactly where these ads come from. It now runs in EVERY frame on the page",
        "It also runs before the page's own scripts, so pop-up and ad code is disabled before it can even start",
        "This is the real fix for ads that appeared when you tapped the player",
        "Playback is protected — the stricter rules are relaxed inside the player frame so the video itself can't be broken"
    )
}
