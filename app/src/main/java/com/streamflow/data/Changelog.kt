package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.11.0"

    val notes = listOf(
        "Your history and library open faster, and stay fast ⚡ — the app was re-reading and re-sorting your entire watch history several times a minute while a video played, just to keep \"Continue watching\" up to date. With a proper index it reads only the handful of rows it actually shows",
        "Losing signal no longer confuses the app about what kind of connection you are on. Going through a tunnel on mobile data was read as switching to a different network, which threw away the quality adjustment made for that exact bad patch — and briefly stopped applying your mobile-data quality limit",
        "The sleep timer is now immune to your phone changing its clock. It was measured against the wall clock, so an automatic time correction or crossing a timezone could make it stop your video early — or never stop it at all",
        "Checking your subscriptions for new uploads no longer loads every channel at once. With a lot of subscriptions that was a burst of memory and CPU in the background; it now works through them steadily and finishes just as quickly",
        "Less writing to storage while you watch. Two separate parts of the app were saving your playback position, doing the same work three times more often than needed",
        "The sleep timer countdown and the player no longer keep updating with the screen off during background listening",
        "Quality the app lowered for you is now visible and reversible. The player showed the quality it started with even after the app had lowered it, and picking a quality by hand did not always cancel the automatic one — so you could be dropped back down again a minute later. The quality menu now says what happened, and choosing anything overrides it for good"
    )
}
