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
    const val VERSION_NAME = "6.21.0"

    val notes = listOf(
        "Library had two dashboards 📊 — one above the tabs and a second one the History tab added underneath it. They measured 'this week' differently, so the two numbers on screen disagreed. There is one dashboard now, and the top-channel list and all-time watch time moved into it",
        "Your Library stats also stopped going stale: rewatching a video updates the chart straight away instead of waiting for something to be added or removed",
        "App lock was buried under Settings › Playback 🔒 — it now has its own Privacy page, together with incognito mode, auto-clear history and everything else that decides what the app remembers",
        "Download settings were split across two unrelated pages. Settings › Downloads now owns the Wi-Fi-only switch, the automatic Watch Later saver and how much space your downloads take"
    )
}
