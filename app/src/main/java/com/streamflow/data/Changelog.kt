package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.2.0"

    val notes = listOf(
        "New: sort comments by Top or Most liked - tap the sort chip next to Comments",
        "Fixed: new-upload notifications from different channels could silently overwrite each other if you hadn't checked your notification shade in a while",
        "Fixed: a failed app-update download could try to install a broken file instead of showing an error"
    )
}
