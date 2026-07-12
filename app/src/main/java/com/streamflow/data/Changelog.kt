package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.66.2"

    val notes = listOf(
        "Settings is now a dashboard! Colorful section tiles at the top - tap any tile to jump straight to that section",
        "Settings cards have a clearer grouped look",
        "Library now opens on the History tab by default (change it in Settings > Home > Default Library tab)"
    )
}
