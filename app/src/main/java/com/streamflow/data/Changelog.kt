package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.56.2"

    val notes = listOf(
        "Found it! The huge empty space in the player was the channel name being squeezed invisible by too many buttons on one row",
        "The player now uses YouTube's layout: channel + Subscribe on one row, action buttons on their own scrollable row",
        "The channel name and view count are visible again next to the avatar",
        "The speed row scrolls sideways too, so nothing gets cut off on small screens"
    )
}
