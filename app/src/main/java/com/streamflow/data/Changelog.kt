package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.1"

    val notes = listOf(
        "Killed the fake 'Your premium account is activated' pop-up 🎯 — the check for scam wording was being skipped unless the pop-up floated a certain way, so this one slipped past every time",
        "Scam wording is now detected no matter how the pop-up is positioned on the page",
        "The sweep also covers more page elements, so these fake 'New message' cards get removed on sight",
        "Plus the previous fix: ad blocking now runs inside the video player's frame too"
    )
}
