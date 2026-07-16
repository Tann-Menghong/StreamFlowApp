package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.12.0"

    val notes = listOf(
        "Fixed: tapping a second video while the first was still loading could play the wrong one — the newest tap now always wins",
        "Fixed: changing quality right before switching videos could load the old video's stream",
        "Fixed: comments now retry when you reopen them after a network hiccup (they used to stay empty)",
        "Fixed: a slow failed refresh can no longer replace fresh Home results with an error page",
        "Fixed: switching between Videos/Channels/Playlists results mid-search no longer shows the previous query's results",
        "Subscriptions feed: channels with notifications ON are now always included first",
        "Fixed a small resource leak when leaving screens with the mini-player",
        "Bookmark time labels now format correctly in every region",
        "More stale-result protection in Search and the subscriptions Feed",
        "Stability and polish fixes across the app"
    )
}
