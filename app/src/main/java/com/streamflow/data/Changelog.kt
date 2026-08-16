package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.4.2"

    val notes = listOf(
        "The stats overlay now shows real playback numbers 📊 — tap the chart icon in the player",
        "See how long the video took to load, how long until it started, and how many times it stalled",
        "Every number is measured, never estimated — if it can't be measured it isn't shown",
        "This is groundwork: playback speed was being tuned blind before, and now it can be tuned from real data",
        "No change to how video plays in this release"
    )
}
