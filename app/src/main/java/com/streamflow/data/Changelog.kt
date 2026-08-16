package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.3.4"

    val notes = listOf(
        "Safety release — no new features, just making sure nothing else is broken",
        "Hardened the database upgrade for the new website tabs so it can't fail when you update from an older version",
        "Added automatic checks that scan the whole app for the kind of mistake that caused the black video, and for database upgrade problems",
        "All checks pass: video view reachable, no hidden unreachable code anywhere, ad blocker 10/10",
        "If video still doesn't play for you, please tell me — that would mean the cause is something I haven't found yet"
    )
}
