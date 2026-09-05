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
    const val VERSION_NAME = "6.25.0"

    val notes = listOf(
        "The Donghua tab is back to donghuafun.com 🌐 — same ad-blocking browser as the Drama and MKissa tabs, desktop layout on by default",
        "Everything the last two updates added to playlists stays: tap an episode and the rest of the series queues behind it, and Continue picks up the episode you left half-watched",
        "Note that website tabs can't use the rest of the app — downloads, favourites, Watch Later, history and resume position need a video StreamFlow itself is playing, and a web page isn't one"
    )
}
