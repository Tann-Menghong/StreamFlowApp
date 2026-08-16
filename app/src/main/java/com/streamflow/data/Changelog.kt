package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.5.0"

    val notes = listOf(
        "Fixed: Retry on a failed video now actually retries 🔁 — it used to reuse the same expired link and fail the same way for up to 30 minutes",
        "The app now tells you when you're offline, and reminds you your downloads and library still work",
        "If StreamFlow ever crashes, it now saves the details and offers to report them — nothing is sent unless you choose to",
        "Your watch history and logins no longer get copied into Google cloud backup, which matches what App Lock promises",
        "Screen reader users can now use every button in the app — ten unlabelled buttons were unreachable before"
    )
}
