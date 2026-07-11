package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.51.0"

    val notes = listOf(
        "Shorts player — tap the Shorts icon on Home and swipe vertically through short videos",
        "Search filters — switch between Videos, Channels and Playlists results",
        "Import your YouTube subscriptions from a Google Takeout CSV or NewPipe export (Settings > Backup)",
        "Custom accent color — pick any hue with the new color slider in Settings > Appearance",
        "Separate video quality for mobile data (Settings > Playback)",
        "Playlists: Play all and Shuffle buttons, plus reorder videos with up/down arrows",
        "Timestamps in video descriptions are now tappable and seek the video; links open in your browser",
        "Per-channel notification bell — choose which subscriptions notify you about new videos",
        "Auto-clear old watch history after 30 or 90 days (Settings > Storage)",
        "Mini player gestures — swipe sideways to dismiss, swipe up to reopen the full player",
        "Double-tap seek now accumulates (10s, 20s, 30s…) with a YouTube-style ripple",
        "Channel pages now show the channel description — tap to expand"
    )
}
