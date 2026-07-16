package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.17.0"

    val notes = listOf(
        "Big reliability pass: 30 fixes and improvements across the whole app",
        "Fixed: after the feed refreshed, tapping a video could open the WRONG video (affected feed cards, compact cards, featured cards, Continue Watching, and channel avatars)",
        "Fixed: swiping the mini player up after autoplay reopened the PREVIOUS video",
        "Fixed: swiping the mini player away sometimes didn't pause the audio",
        "Fixed: toggling subtitles while paused force-started playback",
        "Fixed: direct-stream videos could keep playing audio after you pressed back",
        "Fixed: long YouTube playlists stopped loading more videos partway through",
        "Fixed: rotating the phone dismissed the What's New dialog and reset the channel page's tab and search text",
        "Fixed: a failed download permanently blocked that video from auto-downloading",
        "Fixed: repeat mode chosen right after opening a video was silently dropped",
        "Feed, grid and search results no longer re-animate cards when you scroll back up",
        "Clearing the search box no longer reshuffles your feed for no reason",
        "Khmer translations added for the Donghua, Shorts and Feed tabs",
        "Less battery use on the video page (background glow no longer recomputed on every update)",
        "Donghua: fullscreen episode switching and ad-blocking are more reliable",
        "Plus a dozen smaller polish fixes (Shorts pause icon, Library default tab, blank-search guard, and more)"
    )
}
