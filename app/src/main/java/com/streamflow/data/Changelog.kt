package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.4.1"

    val notes = listOf(
        "Fixed: your custom website tabs were missing from backups 💾 — they're now saved and restored with everything else",
        "Restoring adds tabs you don't already have instead of replacing yours, and skips duplicates",
        "Older backup files still restore fine; they just don't contain tabs",
        "Added a check that fails the build if any of your data is ever left out of a backup again — this was the second time it happened"
    )
}
