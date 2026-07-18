package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.20.0"

    val notes = listOf(
        "Scrolling loads much faster: loading more search results no longer secretly re-downloads the first page every time, and channel pages skip a full extra network round-trip",
        "Comments now show YouTube's badges: pinned comments, creator-hearted comments, and the creator's own comments highlighted",
        "Fixed: one network hiccup while scrolling could permanently stop a channel, playlist, or search feed from loading more — it now retries on the next scroll",
        "Fixed: the endless Home feed could dead-end after a brief offline moment",
        "Fixed: Undo after swipe-deleting from Library now restores the item to its original spot — history undo also keeps your watch progress and date",
        "Subscriptions feed now covers up to 20 channels on powerful phones (was 12)",
        "Playing a large playlist starts faster (queue is filled in one step)",
        "Shorts use less memory during long viewing sessions",
        "Search queries are trimmed so \" cats\" and \"cats\" aren't two different recent searches",
        "Fixed: downloads with symbol-only titles produced an invisible file"
    )
}
