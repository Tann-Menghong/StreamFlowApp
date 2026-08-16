package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.4.0"

    val notes = listOf(
        "NEW: see exactly what's using your space 💾 — Settings › Storage now shows real sizes, not just counts",
        "Bars break it down: video cache, thumbnails and downloads, so you can see what's actually filling the phone",
        "Clear the video cache or thumbnails in one tap — both rebuild themselves, so it's always safe",
        "Your downloads are shown but never cleared automatically; those are your files",
        "More automated tests behind the scenes: 29 now, up from 15"
    )
}
