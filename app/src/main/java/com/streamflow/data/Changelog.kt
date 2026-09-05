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
    const val VERSION_NAME = "6.22.0"

    val notes = listOf(
        "Errors finally say what actually went wrong 🔍 — \"You're offline\", \"Video unavailable\", \"Members only\" — instead of one generic line with a Retry button that could never have worked",
        "When YouTube changes something the app can't read, StreamFlow says so and offers to check for an update, rather than asking you to retry a request it already knows will fail",
        "Settings has a search box 🔎 — type \"dark\", \"pin\", \"wifi\" or \"bass\" and it takes you straight to the right page. Ten pages of settings are pleasant to browse and were awful to look things up in",
        "Empty screens across Search, Library and the queue now look like each other and tell you how to fill them, instead of one bare line of grey text each"
    )
}
