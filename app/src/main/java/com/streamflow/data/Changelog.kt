package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.3.3"

    val notes = listOf(
        "Brought back the stronger ad blocking from the older version 🛡️ — refuses the ad timers that schedule pop-ups and the fake 'premium account' notification",
        "Now also removes ad iframes and screen-covering overlays, not just text pop-ups",
        "Safe for the video player — the block targets ad/pop scriptlets, and players run in a separate frame it doesn't touch",
        "Covers the Donghua, Drama and MKissa tabs plus the in-app player"
    )
}
