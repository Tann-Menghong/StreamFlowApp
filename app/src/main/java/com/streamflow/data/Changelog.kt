package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.3.2"

    val notes = listOf(
        "Killed the fake 'New message / premium account activated' pop-up ad 🛡️ — the in-page dialog ads that covered the video are now removed on sight",
        "Blocks floating overlay ads that carry scam text or off-site links, and stops ads from renaming the tab to fake a notification",
        "Video players are protected — only the ad overlay is removed, playback is untouched",
        "Ad protection now also covers the in-app player's web view; includes the 5.3.0 Premium Minimal redesign"
    )
}
