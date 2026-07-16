package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.11.0"

    val notes = listOf(
        "Fixed: the AI model download could get permanently stuck after an interrupted download — it now recovers and restarts cleanly",
        "Fixed: Shorts no longer shows an empty screen when the first results pages have no shorts — it keeps looking, and there's a Retry button",
        "Fixed: watching a short no longer resets the saved resume position of a video already in your history",
        "Fixed: 'Check for updates' now says when the check itself failed (offline) instead of claiming you're up to date",
        "Security: download-complete events from other apps can no longer alter your downloads list",
        "Stability and polish fixes across the app"
    )
}
