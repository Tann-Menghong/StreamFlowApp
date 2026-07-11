package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.52.0"

    val notes = listOf(
        "Thumbnail corner style — pick Square, Rounded or Extra round in Settings > Appearance",
        "Bottom bar labels — always show, selected tab only, or icons only",
        "Reduce motion — calmer, faster screen transitions for a snappier feel",
        "Haptic feedback toggle — turn long-press vibration on or off",
        "Swipe to delete in Library — swipe any video sideways in Favorites, History or Watch Later, with Undo",
        "Show or hide the category chips bar from Customize Home",
        "Player swipe gestures (brightness/volume) can now be turned off",
        "Confirm before exit — optional double-back-press to close the app"
    )
}
