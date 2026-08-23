package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.14.0"

    val notes = listOf(
        "Housekeeping release 🧹 — nothing in the app looks or behaves differently. The player screen had grown into a single file of over 3,000 lines, and that size has been the direct cause of several recent bugs: the same work ended up written twice in two places because nobody could see the whole file at once",
        "Around 290 lines of self-contained pieces have been moved into their own file, with no change to what any of them do. Everything is verified by the same 162 automated tests and the same checks as before"
    )
}
