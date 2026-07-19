package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.3.0"

    val notes = listOf(
        "Player Pro + Discovery update 🚀 — 12 upgrades in one release",
        "NEW home-screen widget: Latest uploads from your subscriptions, scrollable with thumbnails — tap a video to play it instantly",
        "Cleaner Home 🧹 — the search bar is tucked behind a search icon in the top bar",
        "Sleep timer can now stop at the end of the current video",
        "Your video bookmarks show as amber dots on the seek bar",
        "Custom equalizer 🎚 — 5 band sliders when you pick Custom in Settings > Playback",
        "Video downloads now grab English subtitles alongside (saved as .vtt)",
        "Voice search 🎤 added to the Search tab",
        "Watch stats dashboard: 7-day activity chart and your top channels in Library > History",
        "Export your subscriptions as OPML from Settings > Backup (works with RSS readers and other apps)",
        "Long-press the app icon for shortcuts to channels you visit often",
        "New-upload notifications have a \"Watch later\" button — save without opening the app",
        "Smoother predictive back gesture on Android 13+"
    )
}
