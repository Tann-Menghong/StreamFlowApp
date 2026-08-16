package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.9"

    val notes = listOf(
        "Terminal style is now complete ▮ — every last rounded corner is gone",
        "Avatars, chips, buttons, dialogs, the player and its controls are all square in Terminal now, instead of a few screens staying rounded",
        "Corner radius is now a theme setting rather than being hardcoded on 147 individual elements, so styles stay consistent everywhere",
        "No visual change at all if you use Modern, Aurora or Classic — those keep exactly the corners they had"
    )
}
