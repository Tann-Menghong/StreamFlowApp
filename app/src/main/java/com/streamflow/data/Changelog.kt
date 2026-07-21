package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.1.0"

    val notes = listOf(
        "Closed the last big ad hole 🛡️ — an ad script can no longer silently replace the whole page with an ad (this was the 'page suddenly becomes an ad' pop-up)",
        "Blocked ad injection via document.write, and removed the 'Are you sure you want to leave?' exit traps ads use",
        "Added ~25 more pop-up / pop-under and push-ad networks to the blocklist",
        "New: a shield counter in the tab's top bar shows how many ads were blocked on the page, so you can see it working"
    )
}
