package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.2.3"

    val notes = listOf(
        "Zapping the floating gift/prize box 🎁 — the blocker now catches small pop-up widgets that hover in a corner with a red badge or a close X",
        "Recognises promo widgets by their gift / prize / lucky-draw / coupon naming too, so they go before you even see them",
        "New: tap the shield icon in the top bar to instantly sweep away any pop-up that slips through — it tells you how many it removed",
        "The fake 'premium account' message card stays blocked as before"
    )
}
