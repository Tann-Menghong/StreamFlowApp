package com.streamflow.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Helpers for user-added website tabs.
 *
 * The URL a person types is almost never a valid one: "kisskh.co", with a stray
 * space, or pasted with a trailing newline from another app. Normalising here
 * (one place, before anything is stored) is what makes the feature feel like it
 * works rather than silently loading about:blank.
 */
object CustomTabs {

    /** Hard cap. See [tooManyMessage] for why a cap exists at all. */
    const val MAX_TABS = 5

    val tooManyMessage =
        "You can add up to $MAX_TABS custom tabs — the bottom bar runs out of room beyond that."

    /**
     * Extracts the host from a URL using plain string operations.
     *
     * Deliberately NOT android.net.Uri: that class is a stub in JVM unit tests
     * (every method returns null), which made this logic impossible to test
     * without Robolectric. Hand-parsing is also more predictable than
     * java.net.URI, which rejects hosts containing underscores that browsers
     * accept quite happily.
     *
     * Not handled: bracketed IPv6 literals. Nobody types one as a video-site tab.
     */
    private fun hostOf(url: String): String? {
        var s = url
        val schemeEnd = s.indexOf("://")
        if (schemeEnd >= 0) s = s.substring(schemeEnd + 3)
        s = s.takeWhile { it != '/' && it != '?' && it != '#' }
        val at = s.lastIndexOf('@')
        if (at >= 0) s = s.substring(at + 1)          // strip user:pass@
        val colon = s.lastIndexOf(':')
        if (colon >= 0) s = s.substring(0, colon)      // strip :port
        return s.lowercase().ifEmpty { null }
    }

    /**
     * Turns typed input into a loadable URL, or null if it cannot be one.
     *
     * - trims whitespace and stray newlines from pasting
     * - adds https:// when no scheme is given (the overwhelmingly common case)
     * - rejects anything without a dot in the host, so a typo like "kisskh"
     *   fails here with a clear message instead of becoming a search-engine
     *   redirect or a blank page later
     * - rejects non-web schemes: a tab is a web view, and javascript:/file:
     *   URLs in one would be a way to point the app at local storage
     *
     * Covered by CustomTabsTest.
     */
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            // A non-web scheme WITH an authority (foo://bar) is refused.
            //
            // This deliberately requires "://" rather than just a colon. The
            // earlier version matched any leading word followed by ':', which
            // silently rejected "example.com:8080" and "localhost:3000" — a
            // host:port is not a scheme.
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed) -> return null
            // Schemes that take no authority but would still be dangerous or
            // useless in a web tab, so they can never reach the WebView.
            Regex("^(javascript|data|file|content|intent|about|blob|mailto|tel|sms):",
                  RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> return null
            else -> "https://$trimmed"
        }

        val host = hostOf(withScheme) ?: return null
        if (host.startsWith(".") || host.endsWith(".") || host.contains("..")) return null
        // "localhost" aside, a real site has a dot. This catches the single most
        // common mistake (a bare word) before it becomes a confusing blank tab.
        if (!host.contains(".") && host != "localhost") return null

        return withScheme
    }

    /** A sensible default tab name from a URL: "kisskh.co" -> "Kisskh". */
    fun titleFromUrl(url: String): String {
        val host = hostOf(url)?.removePrefix("www.") ?: return "Site"
        val name = host.substringBefore('.')
        if (name.isEmpty()) return "Site"
        return name.replaceFirstChar { it.uppercase() }.take(14)
    }

    /**
     * WebView state (cookies, logins, scroll) is namespaced per tab so two custom
     * sites never share a session. Keyed on the row id, which is stable for the
     * life of the tab.
     */
    fun prefsNameFor(id: Long) = "custom_tab_$id"

    /** Icon choices offered in the editor. Keys are stored, not the vectors. */
    val iconChoices: List<Pair<String, ImageVector>> = listOf(
        "LANGUAGE"   to Icons.Rounded.Language,
        "LIVETV"     to Icons.Rounded.LiveTv,
        "MOVIE"      to Icons.Rounded.Movie,
        "THEATERS"   to Icons.Rounded.Theaters,
        "TV"         to Icons.Rounded.Tv,
        "PLAY"       to Icons.Rounded.PlayCircle,
        "MUSIC"      to Icons.Rounded.MusicNote,
        "SPORTS"     to Icons.Rounded.SportsSoccer,
        "BOOK"       to Icons.Rounded.MenuBook,
        "STAR"       to Icons.Rounded.Star,
        "PUBLIC"     to Icons.Rounded.Public,
        "BOOKMARK"   to Icons.Rounded.Bookmark,
    )

    fun iconFor(key: String): ImageVector =
        iconChoices.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Language
}
