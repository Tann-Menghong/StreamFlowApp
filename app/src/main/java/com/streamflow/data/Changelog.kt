package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.4.0"

    val notes = listOf(
        "NEW PDTV tab 🎬 — pdtvhd.com movies & series with ad blocking, right next to Donghua and Drama (hide it in Settings > Appearance)",
        "Streaming tabs now fit your phone screen — sites load their mobile layout, and you can pinch-zoom if needed",
        "Speed update ⚡ — faster everywhere",
        "Videos you replay or seek back in now stream from a new on-device cache — instant, no re-downloading",
        "Skips and seeking are much snappier (jump straight to the nearest keyframe)",
        "If a video decoder fails, playback now falls back to another decoder instead of stopping",
        "Faster app startup: background jobs are scheduled off the main thread",
        "First video opens faster: connections to SponsorBlock and dislike servers are pre-warmed",
        "Smoother scrolling on lower-RAM devices (lighter thumbnail decoding)"
    )
}
