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
     * Turns typed input into a loadable URL, or null if it cannot be one.
     *
     * - trims whitespace and stray newlines from pasting
     * - adds https:// when no scheme is given (the overwhelmingly common case)
     * - rejects anything without a dot in the host, so a typo like "kisskh"
     *   fails here with a clear message instead of becoming a search-engine
     *   redirect or a blank page later
     * - rejects non-web schemes: a tab is a web view, and javascript:/file:
     *   URLs in one would be a way to point the app at local storage
     */
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim().replace("\n", "").replace(" ", "")
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            // Anything with an explicit non-web scheme is rejected outright.
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed) -> return null
            else -> "https://$trimmed"
        }

        val host = try {
            android.net.Uri.parse(withScheme).host?.lowercase()
        } catch (_: Exception) { null } ?: return null

        // "localhost" aside, a real site has a dot. This catches the single most
        // common mistake (a bare word) before it becomes a confusing blank tab.
        if (!host.contains(".") && host != "localhost") return null
        if (host.startsWith(".") || host.endsWith(".")) return null

        return withScheme
    }

    /** A sensible default tab name from a URL: "kisskh.co" -> "Kisskh". */
    fun titleFromUrl(url: String): String {
        val host = try {
            android.net.Uri.parse(url).host?.removePrefix("www.")
        } catch (_: Exception) { null } ?: return "Site"
        val name = host.substringBefore('.')
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
