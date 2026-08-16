package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.7"

    val notes = listOf(
        "NEW look: Terminal 🖥️ — a green-on-black command-line theme. Settings › Appearance › Design style › Terminal",
        "Terminal turns the whole app into a CLI: monospace text, square corners, ASCII panes, blinking cursors and a subtle CRT scanline glow",
        "Fixed: in fullscreen, swipe-to-scrub and double-tap-to-skip could stop working after returning from picture-in-picture",
        "Fixed: video length badges now match the rest of the design on every card style",
        "Terminal fixes the colours and font by design, and the Appearance rows now say so instead of looking like they do nothing",
        "Your normal look is untouched — Modern, Aurora and Classic all work exactly as before"
    )
}
