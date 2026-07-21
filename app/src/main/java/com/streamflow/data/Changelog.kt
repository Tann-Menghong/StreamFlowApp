package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.2"

    val notes = listOf(
        "Hunting down the fake 'New message / premium account' pop-up 🎯 — the scanner now checks EVERY element on the page, several times a second",
        "It now also looks inside hidden shadow areas and sub-frames, which is where pop-ups like this hide from blockers",
        "Any change to the page triggers an instant re-scan, so the card is removed the moment it appears",
        "Kept fast — the heavy checks only run on elements that could actually be a pop-up"
    )
}
