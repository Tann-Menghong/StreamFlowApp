package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.17.0"

    val notes = listOf(
        "Two new light themes 🎨 — Settings > Appearance > Theme. StreamFlow had six dark themes and only one light one, so if you prefer a light screen you had a single option and no say beyond it",
        "Warm Paper: an off-white with a paper tone instead of a blue one. Light themes get used in daylight, and warm neutrals are easier on the eyes there than cool grey",
        "Nordic Frost: cool blue-grey surfaces with crisp white cards — the light counterpart to Midnight Blue",
        "Both new themes were measured against the standard contrast guidelines before shipping, not just eyeballed: main text exceeds the strictest level (AAA) and secondary text meets AA, matching the existing Light theme",
        "Your chosen theme still persists exactly as before, and none of the existing themes changed"
    )
}
