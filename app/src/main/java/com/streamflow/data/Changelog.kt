package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.2.0"

    val notes = listOf(
        "StreamFlow Pro Update ⚡ — 10 advanced features in one release",
        "Real dislike counts on every video (Return YouTube Dislike) — toggle in Settings",
        "Clickbait-free titles: community titles from DeArrow replace clickbait in the player",
        "SponsorBlock is now yours to tune — pick exactly which categories auto-skip (sponsors, intros, outros…) in Settings > Playback",
        "Audio language picker 🎙 — videos with dubbed audio get a language button next to quality",
        "Per-channel speed memory: remember 1.5× for your podcast channels, every video from them opens at that speed",
        "Search filters: filter results by length and upload date",
        "Downloads show live progress (% and MB) right in the Library, and in-flight downloads can be cancelled",
        "Weekly auto-backup: StreamFlow can save a backup to Documents/StreamFlow every week — enable it in Settings > Backup",
        "NEW Aurora design style ✨ — glass surfaces and gradient borders, in Settings > Appearance > Design style"
    )
}
