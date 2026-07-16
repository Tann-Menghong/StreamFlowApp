package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.16.0"

    val notes = listOf(
        "Fixed: the sleep timer kept working through video changes — autoplay used to silently cancel it",
        "Your saved data (favorites, history, playlists) is now fully protected during app updates",
        "Failed downloads now have a Retry button instead of dead-ending",
        "Shorts play at sharper 720p on high-end phones",
        "Fixed: changing any setting during playback could cause a brief audio glitch (equalizer was being rebuilt unnecessarily)",
        "Settings > About now shows what performance tier the app detected for your phone",
        "The playback queue now remembers channel info across app restarts",
        "Hide-Shorts filter now catches all shorts (some 1-minute-plus shorts slipped through)",
        "More videos stay instantly re-openable on high-RAM phones",
        "Small feed recommendation improvements"
    )
}
