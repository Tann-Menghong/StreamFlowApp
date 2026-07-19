package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.1.1"

    val notes = listOf(
        "True fullscreen fix 📱 — the black band below the status bar is gone",
        "Every screen sat too low because the status-bar spacing was applied twice; all tabs now use the full height of your screen",
        "Plus from 5.1.0: MKissa tab and the redesigned tab settings"
    )
}
