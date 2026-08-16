package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.4.3"

    val notes = listOf(
        "The app is now half the size ⚡ — 27.4 MB down to 15.3 MB",
        "It was shipping four copies of a large AI library, one per phone chip type, when your phone can only ever use one",
        "The two versions only ever used by emulators are gone; nothing changes on real phones",
        "Updates now download in about half the time, every time",
        "No features removed — the AI summary feature works exactly as before"
    )
}
