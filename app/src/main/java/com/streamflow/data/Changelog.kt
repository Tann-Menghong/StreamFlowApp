package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.1.0"

    val notes = listOf(
        "Settings redesigned as a real dashboard!",
        "Every category tile now shows its current status at a glance - Theme: Dark, Autoplay: on, and more",
        "Tapping a tile opens a clean dedicated page for that category instead of one giant scrolling list",
        "Faster to navigate, easier to scan - a pro settings experience"
    )
}
