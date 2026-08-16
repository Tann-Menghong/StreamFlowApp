package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.3.5"

    val notes = listOf(
        "Fixed: adding a website tab with a port in the address (like example.com:8080) was wrongly refused as invalid",
        "Tab names now read correctly from addresses with a port or a long path",
        "Added the app's first real automated tests — 15 of them, covering exactly what you type when adding a tab",
        "Verified the tests actually catch the bug: they fail on the old code and pass on the fixed code",
        "No changes to video playback in this release"
    )
}
