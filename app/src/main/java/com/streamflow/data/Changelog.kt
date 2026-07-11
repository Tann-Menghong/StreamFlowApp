package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.59.0"

    val notes = listOf(
        "Music player upgrade! Tapping the playback notification now opens StreamFlow",
        "Full control from the notification, lock screen, and Bluetooth/headset buttons",
        "New: Settings > Playback > Background play protection - stops Vivo/iQOO/Xiaomi phones from killing playback in the background",
        "Tip: turn on Audio-only in the player to listen like a music app with the screen off"
    )
}
