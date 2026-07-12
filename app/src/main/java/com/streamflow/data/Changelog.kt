package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.1.4"

    val notes = listOf(
        "Stability fixes!",
        "Fixed a crash: scrolling for more videos on a channel page, search results, or your subscriptions feed could occasionally close the app if the same video appeared twice",
        "All three of those lists are now protected the same way the home feed already was"
    )
}
