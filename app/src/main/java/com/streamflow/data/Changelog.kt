package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
//
// v6.20.0 shipped without this file being touched, so everyone who updated to
// it opened "What's New" and read a dialog headed "Version 6.19.0" describing
// the previous release. Nothing failed to compile and nothing failed a test,
// which is exactly why tools/version-check now compares this constant against
// versionName in app/build.gradle and fails the build when they drift.
object Changelog {
    const val VERSION_NAME = "6.24.0"

    val notes = listOf(
        "Playlists are series now 📺 — tap any episode and the rest of the show queues up behind it. Before, tapping an episode played that one and stopped dead at the end of it, and \"Play all\" from episode 1 was the only way to get continuous playback",
        "A Continue button that knows where you are: it opens the furthest episode you left half-watched and says how much of it is left, instead of sending you back to episode 1 of a 280-episode series",
        "Episode numbers come from the uploader's own titles, so a playlist that opens with a trailer or starts partway into a season is numbered the way the show is",
        "The Donghua tab has a Series row 🎬 — pick a show, get its full episode list, watch straight through",
        "Playlists that fail to load now use the app's shared error screen, so they offer an action that can actually work instead of Retry for everything"
    )
}
