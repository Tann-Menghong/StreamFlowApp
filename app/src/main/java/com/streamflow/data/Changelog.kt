package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.13.0"

    val notes = listOf(
        "New-video notification checks now run in parallel — much faster and lighter on battery",
        "Comments and replies now properly retry after a failed load instead of showing empty forever",
        "Search tab: added a Retry button, and your typed query is no longer lost when you navigate away",
        "YouTube playlists: added a Retry button when a playlist fails to load",
        "Fixed: reordering playlist videos with the arrows sometimes did nothing (items imported together)",
        "Fixed a connection leak in the SponsorBlock lookup",
        "Fixed a resource leak when closing the video player",
        "Shorts: retrying while a load is in progress can no longer mix results",
        "Faster, more reliable background subscription checks",
        "Stability and polish fixes across the app"
    )
}
