package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.15.0"

    val notes = listOf(
        "The video engine has been updated 🎬 — StreamFlow now uses a newer version of the library that actually plays your videos (ExoPlayer/Media3 1.3.1 to 1.4.1). Please tell me if you notice ANY problem with playback after this update",
        "Fixed: if another app takes the video decoder away — an incoming video call, the camera, another video app — StreamFlow used to say \"This video cannot be played on this device\" and stop. It now simply reclaims the decoder and carries on. This affects downloaded videos too",
        "Live streams benefit from several fixes in the new engine: playlists no longer reload repeatedly, and live streams refresh correctly while playing",
        "More reliable audio focus: the new engine correctly reports when another app takes over sound while StreamFlow is paused",
        "When loading fails partway, playback can now start with whatever has already buffered instead of waiting for more"
    )
}
