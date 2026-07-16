package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.15.0"

    val notes = listOf(
        "The app now adapts to your phone's hardware automatically",
        "High-end phones (hardware VP9/AV1 decoding + plenty of RAM) now default to sharp 1080p on Auto quality instead of 720p",
        "Bigger playback buffers on high-RAM phones — smoother long videos, and small rewinds replay instantly without re-buffering",
        "High refresh rate now always runs at your screen's full resolution (phones with resolution switching could get a downscaled fast mode)",
        "Larger thumbnail caches on high-RAM phones — smoother scrolling, fewer re-downloads",
        "Budget phones keep the previous battery-friendly defaults — nothing gets slower"
    )
}
