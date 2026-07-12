package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.5.0"

    val notes = listOf(
        "Fixed: a rare crash when a video's comments or Up Next list contained a repeated entry — also hardened channel, search and playlist pages against the same issue",
        "Fixed: videos you already finished no longer clutter Continue Watching (they were quietly restarting from the beginning when tapped)",
        "Fixed: fullscreen video now fills the screen properly on phones with a notch or punch-hole camera"
    )
}
