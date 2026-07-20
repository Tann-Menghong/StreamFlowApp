package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.0.1"

    val notes = listOf(
        "Fixed the Donghua tab not loading 🛠️ — it now always opens on the working home page instead of getting stuck on a stale or ad-redirect page",
        "The streaming tabs are back to the mobile layout, which fits your phone and blocks ads better than the desktop mode did",
        "The tab only remembers real pages from the site now, never an ad page",
        "If a site changes its address, the tab recovers automatically instead of breaking"
    )
}
