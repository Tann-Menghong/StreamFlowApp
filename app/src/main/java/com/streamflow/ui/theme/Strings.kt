package com.streamflow.ui.theme

// Lightweight UI translation for the most visible labels. The app's strings are
// hardcoded in composables, so this covers navigation + main sections first;
// deeper coverage can be added incrementally.
object KmStrings {
    private val km = mapOf(
        "Home" to "ទំព័រដើម",
        "Library" to "បណ្ណាល័យ",
        "Settings" to "ការកំណត់",
        "Search" to "ស្វែងរក",
        "Favorites" to "ចូលចិត្ត",
        "History" to "ប្រវត្តិ",
        "Watch Later" to "មើលពេលក្រោយ",
        "Channels" to "ប៉ុស្តិ៍",
        "Playlists" to "បញ្ជីចាក់",
        "Downloads" to "ទាញយក",
        "For You" to "សម្រាប់អ្នក",
        "Trending" to "ពេញនិយម"
    )

    fun t(label: String, lang: String): String =
        if (lang == "KM") km[label] ?: label else label
}
