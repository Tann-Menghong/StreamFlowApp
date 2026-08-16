package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.6"

    val notes = listOf(
        "Stability release — fixed 6 crashes that hit Android 5, 6 and 7 phones 🩹",
        "Biggest one: browsing, searching or opening the Feed crashed instantly on Android 5-7 because video dates used a feature those versions don't have",
        "Opening the video player crashed on Android 5.x (volume control), and the app lock and data-saver checks crashed too",
        "Fixed two network connections that were left open when a download failed",
        "Sound & vibration in Settings now opens properly on older Android instead of doing nothing",
        "Version picker polish: no more stacked dialogs, and it can't clash with a running update download"
    )
}
