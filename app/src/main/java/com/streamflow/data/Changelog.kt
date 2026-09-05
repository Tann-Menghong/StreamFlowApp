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
    const val VERSION_NAME = "6.28.0"

    val notes = listOf(
        "Each website tab can now have its own layout 🖥 — flip \"Desktop site\" from a tab's ⋮ menu and it applies to that tab only, so Donghua can be desktop while Drama stays mobile",
        "Settings › Home is now the default every tab starts from, and it tells you how many tabs have chosen their own mode — a switch that looks like it does nothing is no longer a mystery",
        "\"Use the default for this site\" in a tab's ⋮ menu puts it back under that default, and Settings can reset every tab at once",
        "New: Desktop layout width — 900, 1000, 1100, 1280 or 1440px. Narrower is easier to read, wider looks more like a real desktop but shrinks further to fit",
        "Watch pages are still never scaled at any width, so last release's black-screen fix stays fixed"
    )
}
