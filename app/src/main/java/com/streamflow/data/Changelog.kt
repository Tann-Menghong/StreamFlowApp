package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.61.0"

    val notes = listOf(
        "Pro UI polish!",
        "Mini player is now a floating rounded card instead of a full-width bar",
        "New app logo mark next to StreamFlow in the top bar",
        "Category chips are flat borderless pills, just like YouTube",
        "Section titles (For You, Continue watching, Featured) are bigger and easier to scan"
    )
}
