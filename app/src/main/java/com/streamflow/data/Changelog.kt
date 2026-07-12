package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.1.3"

    val notes = listOf(
        "More bug fixes!",
        "Fixed: Battery saver's quality cap could be bypassed by manually picking a quality from the player's quality menu",
        "Fixed: a rare glitch where quickly re-grabbing the video right after canceling swipe-to-minimize could cause a visual stutter"
    )
}
