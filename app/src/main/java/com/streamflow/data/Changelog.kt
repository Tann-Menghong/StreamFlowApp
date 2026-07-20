package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.7.0"

    val notes = listOf(
        "New: a Next ⏭️ button on the media notification and lock screen — skip to your queued video (or the next related one) without opening the app",
        "Redesigned Settings 🎨 — a clean, professional grouped layout with sections, icons and live status, like a system settings app",
        "The mini player stays in sync when you skip from the notification"
    )
}
