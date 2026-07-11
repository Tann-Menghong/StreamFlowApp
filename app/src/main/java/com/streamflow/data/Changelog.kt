package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.55.1"

    val notes = listOf(
        "Fixed: AI model download now keeps going when you leave the Settings screen",
        "Fixed: AI answers could leak into the next video if you switched mid-answer",
        "Fixed: the keyboard no longer covers the Ask field, and the keyboard's Send key now works",
        "Fixed: AI could crash if you removed the model while it was answering",
        "AI answers now hide any leftover template text from the model",
        "Custom category names are cleaned up so they can't corrupt your saved list"
    )
}
