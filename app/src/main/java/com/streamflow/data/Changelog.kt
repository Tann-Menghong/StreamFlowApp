package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.18.0"

    val notes = listOf(
        "The text size setting now actually works 🔤 — it was only ever applied to a small part of the interface, so turning text up grew a few headings while the labels beside them stayed exactly the same. Every piece of text in the app now scales together and keeps its proportions",
        "This was worst for the people it mattered most to: if you raised the text size because something was hard to read, most of what you wanted bigger stayed the same size",
        "Bigger tap targets on the search bars. The clear-search button in Library was 18 pixels across and the one in Search was 20 — small, used often, and easy to miss. They are now 38 and 46, about five times the tappable area, with no change to the layout around them",
        "Voice search in the search bar got the same treatment",
        "Six other small controls are now measured and tracked by the accessibility check so they get fixed properly rather than forgotten"
    )
}
