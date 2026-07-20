package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.2.0"

    val notes = listOf(
        "Total ad block 🛡️ — the Donghua, Drama and MKissa tabs now block ads much harder",
        "No more pop-up/pop-under ad tabs: tapping the player just plays the video instead of opening an ad",
        "Blocks redirect ads, app-open ('open in Play Store') ad links, trackers and in-page crypto-miners",
        "Video players are untouched — same-site taps and the play button work exactly as before"
    )
}
