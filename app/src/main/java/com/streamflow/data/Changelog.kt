package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.58.0"

    val notes = listOf(
        "Speed update! The app now opens straight to your feed - no more waiting spinner",
        "Videos start playing noticeably faster",
        "The next video is preloaded sooner, so Up Next feels instant",
        "Like button bounces with a satisfying animation and vibration",
        "Subscribed button now shows a checkmark",
        "If the network is slow or offline, you still see your last feed instead of an error"
    )
}
