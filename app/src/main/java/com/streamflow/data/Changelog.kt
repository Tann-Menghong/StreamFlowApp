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
    const val VERSION_NAME = "6.26.0"

    val notes = listOf(
        "Fixed: video playing as a black screen on website tabs with desktop mode on 🖥 — desktop mode was doing two things at once, and one of them was breaking the player",
        "Desktop mode is now two switches. \"Desktop site layout\" still asks the site for its full version; \"Force desktop width\" is the part that scales the page down, and that is the half that can black out a video",
        "So you can keep the desktop layout and just turn the width forcing off — before, the only fix was giving up desktop mode completely",
        "Both are in the ⋮ menu inside any website tab, and under Settings › Home › Website tab layout"
    )
}
