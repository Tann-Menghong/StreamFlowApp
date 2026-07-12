package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.66.1"

    val notes = listOf(
        "Status bar fixed: clock, battery and wifi icons now always stay visible above the app",
        "Search tab no longer sits under the status bar",
        "Fullscreen video now fills around the camera punch-hole for a true full screen",
        "Status bar icon colors now follow the app theme (light icons on dark theme)"
    )
}
