package com.streamflow.data

/**
 * A searchable index of every setting, and which page it lives on.
 *
 * Settings is now ten category pages holding around eighty controls. That is a
 * good structure for browsing and a bad one for looking something up: knowing
 * that "Data saver" exists tells you nothing about whether it is filed under
 * Playback, Downloads or Privacy, and the only way to find out is to open
 * pages until it appears. Categorising things well makes them harder to find,
 * not easier -- search is what pays that back.
 *
 * Two design decisions worth stating:
 *
 * The index is DATA, not a scan of the UI at runtime. Composables only exist
 * while they are on screen, so there is nothing to search until you are already
 * on the page you were looking for.
 *
 * Because it is hand-maintained it can lie -- an entry can point at a page
 * whose control was renamed or moved, and search would confidently navigate to
 * a setting that is not there. tools/settings-check/check-search-index.js
 * verifies every entry against the actual rows in SettingsScreen.kt and fails
 * the build when they disagree, which is the only reason this is safe to ship.
 *
 * [synonyms] carry the words people actually type. Someone looking for dark
 * mode does not search "Theme", and someone looking to stop the app eating
 * their data plan does not search "Data saver".
 */
object SettingsIndex {

    data class Entry(
        /** Must match the row's visible label in SettingsScreen.kt exactly. */
        val title: String,
        /** The category page it lives on — the same name the tile uses. */
        val category: String,
        val synonyms: List<String> = emptyList(),
    )

    val entries: List<Entry> = listOf(
        // ── Appearance ──────────────────────────────────────────────────────
        Entry("Theme", "Appearance", listOf("dark mode", "light mode", "night", "black", "amoled", "colour", "color")),
        Entry("Design style", "Appearance", listOf("look", "classic", "aurora", "terminal", "modern", "skin")),
        Entry("Accent color", "Appearance", listOf("accent", "colour", "highlight", "tint")),
        Entry("Font size", "Appearance", listOf("text size", "bigger text", "larger", "small", "scale", "accessibility")),
        Entry("Font style", "Appearance", listOf("typeface", "serif", "monospace", "font family")),
        Entry("Thumbnail corners", "Appearance", listOf("rounded", "square", "corner radius", "shape")),
        Entry("Reduce motion", "Appearance", listOf("animation", "animations", "motion", "accessibility")),
        Entry("Haptic feedback", "Appearance", listOf("vibration", "vibrate", "haptics", "touch feedback")),
        Entry("Confirm before exit", "Appearance", listOf("double back", "quit", "close app")),
        Entry("Language / ភាសា", "Appearance", listOf("language", "khmer", "english", "translate", "ភាសា")),

        // ── Home ────────────────────────────────────────────────────────────
        Entry("Grid layout", "Home", listOf("grid", "list", "layout", "columns")),
        Entry("Grid columns", "Home", listOf("columns", "card size", "density", "how many")),
        Entry("Continue Watching row", "Home", listOf("continue watching", "resume", "keep watching")),
        Entry("Hero featured card", "Home", listOf("featured", "banner", "big card", "hero")),
        Entry("Start screen", "Home", listOf("start", "opening tab", "launch", "first screen", "default tab")),
        Entry("Default Library tab", "Home", listOf("library tab", "default tab", "favorites first")),
        Entry("Trending country", "Home", listOf("country", "region", "trending", "location")),
        Entry("Bottom bar labels", "Home", listOf("nav labels", "navigation", "bottom bar", "icons only")),
        Entry("Search tab", "Home", listOf("hide search", "tabs", "bottom bar")),
        Entry("Donghua tab", "Home", listOf("donghua", "chinese anime", "anime", "tabs", "website")),
        Entry("Add website tab", "Home", listOf("custom tab", "website", "browser", "add site")),

        // ── Playback ────────────────────────────────────────────────────────
        Entry("Video quality", "Playback", listOf("quality", "resolution", "1080p", "720p", "hd")),
        Entry("Quality on mobile data", "Playback", listOf("cellular", "mobile data", "4g", "5g", "quality")),
        Entry("Default speed", "Playback", listOf("speed", "playback rate", "faster", "slower", "2x")),
        Entry("Double-tap skip", "Playback", listOf("skip", "seek", "forward", "rewind", "double tap")),
        Entry("Player swipe gestures", "Playback", listOf("gesture", "swipe", "brightness", "volume")),
        Entry("Pop-up video on exit", "Playback", listOf("pip", "picture in picture", "popup", "floating")),
        Entry("Auto-play", "Playback", listOf("autoplay", "next video", "continuous")),
        Entry("Volume boost", "Playback", listOf("loud", "louder", "volume", "gain", "amplify")),
        Entry("Equalizer", "Playback", listOf("eq", "bass", "treble", "audio", "sound")),
        Entry("SponsorBlock auto-skip", "Playback", listOf("sponsor", "skip ads", "sponsorblock", "segments")),
        Entry("Dislike counts", "Playback", listOf("dislikes", "return youtube dislike", "thumbs down")),
        Entry("Clickbait-free titles", "Playback", listOf("dearrow", "clickbait", "titles")),
        Entry("Data saver", "Playback", listOf("data", "save data", "mobile data", "bandwidth", "cheaper")),
        Entry("Battery saver", "Playback", listOf("battery", "power", "save battery")),

        // ── Downloads ───────────────────────────────────────────────────────
        Entry("Download on Wi-Fi only", "Downloads", listOf("wifi", "wi-fi", "mobile data", "download", "metered")),
        Entry("Auto-download Watch Later", "Downloads", listOf("auto download", "offline", "watch later", "automatic")),

        // ── Privacy ─────────────────────────────────────────────────────────
        Entry("App lock", "Privacy", listOf("lock", "pin", "fingerprint", "biometric", "password", "security", "private")),
        Entry("Incognito mode", "Privacy", listOf("incognito", "private", "no history", "anonymous")),
        Entry("Auto-clear history", "Privacy", listOf("clear history", "retention", "delete history", "auto delete")),
        Entry("Clear watch history", "Privacy", listOf("history", "delete", "clear", "erase")),
        Entry("Hidden videos & channels", "Privacy", listOf("blocked", "hidden", "not interested", "unblock")),

        // ── Storage ─────────────────────────────────────────────────────────
        Entry("Clear video cache", "Storage", listOf("cache", "space", "storage", "free up")),
        Entry("Clear thumbnails", "Storage", listOf("images", "thumbnails", "cache", "space")),
        Entry("Clear favorites", "Storage", listOf("favourites", "favorites", "saved", "delete")),

        // ── Backup ──────────────────────────────────────────────────────────
        Entry("Export backup", "Backup", listOf("backup", "export", "save data", "json")),
        Entry("Import backup", "Backup", listOf("restore", "import", "backup")),
        Entry("Weekly auto-backup", "Backup", listOf("automatic backup", "weekly", "scheduled")),
        Entry("Import YouTube subscriptions", "Backup", listOf("subscriptions", "takeout", "newpipe", "import", "csv")),
        Entry("Export subscriptions (OPML)", "Backup", listOf("opml", "rss", "subscriptions", "export")),

        // ── AI ──────────────────────────────────────────────────────────────
        Entry("On-device AI", "AI", listOf("ai", "llm", "qwen", "offline ai", "model", "assistant")),

        // ── About ───────────────────────────────────────────────────────────
        Entry("Check for updates", "About", listOf("update", "upgrade", "new version", "latest")),
        Entry("App version", "About", listOf("version", "build", "about")),
        Entry("What's new", "About", listOf("changelog", "release notes", "what changed")),
        Entry("Playback log", "About", listOf("log", "diagnostics", "debug", "troubleshoot")),
        Entry("Device performance", "About", listOf("benchmark", "performance", "codec", "hardware")),
        Entry("Report a problem", "About", listOf("bug", "issue", "report", "feedback", "support")),
        Entry("Source code", "About", listOf("github", "open source", "code", "licence", "license")),
    )

    /** Where a hit was found, so the UI can say why a result matched. */
    enum class Match { TITLE_EXACT, TITLE_PREFIX, TITLE_CONTAINS, SYNONYM }

    data class Result(val entry: Entry, val match: Match)

    /**
     * Ranked matches for [query], best first.
     *
     * Ordering is by how the match was made, then by title, so results are
     * stable between keystrokes -- a list that reshuffles under the finger is
     * worse than no search at all. Blank queries return nothing rather than
     * everything: the category cards are the browse view, and search should not
     * duplicate them.
     */
    fun search(query: String, limit: Int = 12): List<Result> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val hits = ArrayList<Result>()
        for (e in entries) {
            val title = e.title.lowercase()
            val match = when {
                title == q -> Match.TITLE_EXACT
                title.startsWith(q) -> Match.TITLE_PREFIX
                title.contains(q) -> Match.TITLE_CONTAINS
                e.synonyms.any { it.contains(q) } -> Match.SYNONYM
                // Also match the page name, so "privacy" finds what is on the
                // Privacy page even where no single row is called that.
                e.category.lowercase().startsWith(q) -> Match.SYNONYM
                else -> null
            } ?: continue
            hits.add(Result(e, match))
        }
        return hits
            .sortedWith(compareBy({ it.match.ordinal }, { it.entry.title }))
            .take(limit)
    }
}
