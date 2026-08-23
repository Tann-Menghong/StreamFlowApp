package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.19.0"

    val notes = listOf(
        "The home feed now uses the whole screen 📱 — turn your phone sideways and you get four columns instead of two enormous cards. Nothing in the app looked at screen size before, anywhere",
        "Your column setting still wins in portrait, exactly as you set it. It is now read as a card SIZE rather than a fixed number, so a wider screen fits more cards of the same size instead of stretching two across it",
        "Cards stay the same size when you rotate rather than growing, and there are limits at both ends so a very wide screen cannot turn the feed into a wall of tiny thumbnails"
    )
}
