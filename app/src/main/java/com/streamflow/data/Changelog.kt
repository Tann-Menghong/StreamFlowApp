package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.12.0"

    val notes = listOf(
        "One broken video no longer stops your whole queue 🔁 — if the app moved on to a video that turns out to be deleted, private or blocked in your country, it used to leave you on an error screen with the rest of your queue stranded behind it. It now skips past and keeps playing, up to three in a row before it stops and asks",
        "An error screen now offers \"Skip to next video\" whenever there is something queued. Previously the only ways out were Go back and Retry — neither of which could reach the videos still waiting",
        "Videos you chose yourself are never skipped past. If you deliberately open a video and it fails, the app stays there and lets you retry, because you asked for that one specifically"
    )
}
