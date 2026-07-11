package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.57.0"

    val notes = listOf(
        "YouTube-style player redesign!",
        "Views and upload date now show right under the title, like YouTube",
        "Action buttons are now labeled pills: Like, Share, Watch later, Download, Save, Ask AI",
        "The Like pill doubles as your Favorite button and shows the like count",
        "Description now sits in a rounded card with a \"...more\" expander"
    )
}
