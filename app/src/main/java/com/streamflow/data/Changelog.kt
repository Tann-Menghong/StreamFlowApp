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
    const val VERSION_NAME = "6.27.0"

    val notes = listOf(
        "Fixed for real this time: video on website tabs no longer plays as a black screen with desktop mode on 🖥 — and you no longer have to turn any setting off to get it",
        "v6.26.0 split desktop mode in two but left you to find the right switch. This release measures which half was at fault instead of guessing: the site sends the identical page to a phone and a PC, so the desktop layout came entirely from the width override — and scaling a 1100px page into a phone window is what was blacking out the player",
        "So the width is now decided per page: browsing pages keep the wide desktop grid, and the page you actually watch on is never scaled",
        "\"Force desktop width\" stays in the ⋮ menu and Settings › Home for anything the automatic rule misses"
    )
}
