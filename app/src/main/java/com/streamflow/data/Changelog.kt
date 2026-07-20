package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.4.0"

    val notes = listOf(
        "Streaming tabs now load in desktop mode 🖥️ — like opening the site in a desktop browser, which serves the cleaner desktop layout with far fewer of the aggressive mobile pop-up ads",
        "Keeps the full Brave-level ad blocking on top: pop-up/pop-under timers, fake 'premium account' notifications, ad iframes and screen-covering overlays are all still refused",
        "Fits the phone screen — the page is scaled down to full width and you can pinch-zoom as needed",
        "Applies to the Donghua, Drama and MKissa tabs"
    )
}
