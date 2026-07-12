package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.66.0"

    val notes = listOf(
        "Choose your design! New switch: Settings > Appearance > Modern design",
        "Modern (default): card feed, floating pill bars, colorful settings icons",
        "Classic: the original flat full-width design, for those who prefer it",
        "Switches instantly - no restart needed"
    )
}
