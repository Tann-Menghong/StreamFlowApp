package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.3.2"

    val notes = listOf(
        "Second attempt at fixing the black video 🔴 — please tell me if this one works",
        "Removed the CRT scanline overlay that the Terminal style drew across the whole app, including on top of the video",
        "Video is drawn on its own display layer, so anything painted over it hides the picture instead of blending — that matches 'sound plays, screen black'",
        "The Terminal style keeps everything else: monospace text, square corners, ASCII panes and the green palette",
        "Everything else from 6.3.1 stays, including your own website tabs"
    )
}
