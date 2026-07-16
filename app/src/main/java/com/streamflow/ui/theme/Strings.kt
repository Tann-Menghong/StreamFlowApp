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
        // Bottom-bar tabs that were missing (stayed English in Khmer mode)
        "Donghua" to "ដុងហួ",
        "Shorts" to "វីដេអូខ្លី",
        "Feed" to "វីដេអូថ្មីៗ",
        "Favorites" to "ចូលចិត្ត",
        "History" to "ប្រវត្តិ",
        "Watch Later" to "មើលពេលក្រោយ",
        "Channels" to "ប៉ុស្តិ៍",
        "Playlists" to "បញ្ជីចាក់",
        "Downloads" to "ទាញយក",
        "For You" to "សម្រាប់អ្នក",
        "Trending" to "ពេញនិយម",
        "Bookmarks" to "ចំណាំ",
        // Settings sections + dashboard tiles
        "Appearance" to "រូបរាង",
        "Playback" to "ការចាក់",
        "Notifications" to "ការជូនដំណឹង",
        "AI" to "AI",
        "Storage" to "ឃ្លាំងផ្ទុក",
        "Backup" to "បម្រុងទុក",
        "About" to "អំពី",
        // Common labels
        "Subscribe" to "តាមដាន",
        "Subscribed" to "កំពុងតាមដាន",
        "Share" to "ចែករំលែក",
        "Download" to "ទាញយក",
        "Save" to "រក្សាទុក",
        "Like" to "ចូលចិត្ត",
        "Watch later" to "មើលពេលក្រោយ",
        "Continue watching" to "បន្តមើល",
        "Featured" to "ពិសេស",
        "Retry" to "ព្យាយាមម្តងទៀត",
        "Cancel" to "បោះបង់",
        "Next" to "បន្ទាប់",
        "Theme" to "ផ្ទៃរូបរាង",
        "Equalizer" to "អេហ្គុយ",
        "Battery saver" to "សន្សំថ្ម",
        "Data saver" to "សន្សំទិន្នន័យ",
        "Up Next" to "បន្ទាប់"
    )

    fun t(label: String, lang: String): String =
        if (lang == "KM") km[label] ?: label else label
}
