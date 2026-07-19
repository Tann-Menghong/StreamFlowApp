package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.5.1"

    val notes = listOf(
        "Live TV turbo 📺⚡ — PDTV channels start faster and play smoother",
        "Channels start in under a second and build a deep 60s safety buffer so network jitter doesn't cause stutters",
        "Channel zapping is near-instant: connections to the channel servers are opened ahead of time",
        "If a live stream drops, the app reconnects silently (up to 3 tries) and rejoins the live edge instead of stopping",
        "Flaky live segments are retried much harder before showing any error",
        "Hardware decoder fallback for live TV — playback recovers instead of freezing"
    )
}
