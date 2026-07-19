package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.5.0"

    val notes = listOf(
        "PDTV is now a real Live TV player 📺 — no more black screen",
        "The PDTV tab shows a channel grid (sports, Khmer, news, movies…) and plays streams directly in the app's own player — instant start, zero ads",
        "Tap a channel logo to switch, tap the fullscreen button to go big; your last channel auto-plays next time",
        "Live TV keeps your screen awake and works with the phone's volume/audio controls",
        "Next video starts instantly ⚡ — the app pre-buffers the beginning of the likely next video while you watch"
    )
}
