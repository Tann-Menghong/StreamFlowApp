package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.53.0"

    val notes = listOf(
        "App update alerts — get a notification when a new StreamFlow version is released, tap to install",
        "Check frequency — choose how often to check for new videos (hourly to once a day)",
        "Quiet hours — no notifications during the night window you pick",
        "Alerts per check — cap how many new-video notifications arrive at once",
        "Sound & vibration shortcut — jump straight to Android's per-channel notification options",
        "New-video checks now reschedule instantly when you change the frequency"
    )
}
