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
    const val VERSION_NAME = "6.23.0"

    val notes = listOf(
        "The Donghua tab plays properly now 🎬 — it was a mini web browser pointed at an outside site, and when that site's player went black there was nothing in StreamFlow that could fix it. It's a real StreamFlow tab now, using the same player as everything else",
        "So donghua episodes finally support everything the rest of the app does: download, audio-only, favourite, Watch Later, queue, bookmarks, history and background play — none of which could work on a web page",
        "Continue watching for donghua 📺 — pick a half-finished episode back up, with the progress bar showing where you stopped",
        "Rows that come back empty are hidden instead of shown as an empty shelf, and the same video no longer appears twice in two different rows"
    )
}
