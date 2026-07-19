package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.4.1"

    val notes = listOf(
        "PDTV tab fixed 📺 — the ad blocker was accidentally breaking the site's live player; channels play now",
        "Ad blocking made smarter: it no longer interferes with legitimate site players on any streaming tab",
        "Next video starts instantly ⚡ — the app now pre-buffers the beginning of the likely next video while you watch",
        "Plus everything from 4.4.0: PDTV tab, mobile-fit streaming tabs, on-device media cache, snappier seeking"
    )
}
