package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.1.0"

    val notes = listOf(
        "NEW: Drama tab 🎬 — watch Asian dramas & movies (KissKH) ad-free, right next to Donghua; hide it or make it your start screen in Settings",
        "Streaming tabs got smarter ad-blocking: ad redirects that hijack the whole page are now blocked too",
        "Leaving the Donghua/Drama tab now really stops the site's audio, and exiting fullscreen no longer restarts the episode",
        "Sharing the same video to StreamFlow twice now opens it both times, and reopening the app from Recents no longer jumps back into an old shared video",
        "You can now download BOTH the video and the audio of the same video — they used to overwrite each other in Downloads",
        "Clearing all Favorites, History, or Watch Later now asks for confirmation first — one stray tap used to wipe the whole list",
        "Battery saver now also skips preloading the next Short, as promised",
        "Fixed a security hole in the streaming tabs: invalid TLS certificates are now rejected instead of silently accepted"
    )
}
