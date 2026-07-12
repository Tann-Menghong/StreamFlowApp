package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.8.0"

    val notes = listOf(
        "Redesigned Home: a persistent rounded search bar now sits right under the logo — tap anywhere on it to search, no more hunting for the search icon",
        "New cinematic Featured strip — big hero cards with the title and channel laid over the thumbnail, in a smooth peeking carousel",
        "Cleaner section headers with an accent bar, plus a friendlier 'For You' with its own subtitle",
        "Follows last update's elevated feed cards for one coherent, more premium look"
    )
}
