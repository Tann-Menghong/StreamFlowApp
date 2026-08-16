package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.8"

    val notes = listOf(
        "Library is now a dashboard 📊 — watch time, videos watched, favorites and downloads at the top, with a 7-day activity chart",
        "Tap \"Show activity\" to expand or hide the chart; it remembers your choice and stays out of the way when you just want your tabs",
        "History tab now shows a proper Top Channels leaderboard with ranked bars instead of one cramped scrolling line",
        "Settings opens with a System status panel — favorites, history, blocked channels and whether your version is up to date",
        "Both dashboards share one design, so they look right in every style including Terminal (where the bars become ASCII)"
    )
}
