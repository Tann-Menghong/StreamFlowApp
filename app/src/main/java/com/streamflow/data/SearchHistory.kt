package com.streamflow.data

import android.content.Context

/**
 * Recent search terms, offered back as suggestions.
 *
 * Searching for the same show every evening meant retyping it every evening.
 * The extractor has no suggestion endpoint wired up here, so the honest source
 * of suggestions is what this user actually searched before — no network call,
 * no third-party autocomplete, nothing leaves the device.
 *
 * The list manipulation lives in [merged] and [matching] as pure functions over
 * a List<String>, with no Android types, specifically so it can be tested on the
 * JVM in milliseconds. The SharedPreferences wrapper around them is a handful of
 * lines with nothing to get wrong.
 */
object SearchHistory {

    private const val PREFS = "search_history"
    private const val KEY = "recent"
    /**
     * Ten is what fits on screen without pushing results below the fold. A
     * longer memory sounds better and in practice just means scrolling past
     * stale terms to reach the one you want.
     */
    const val MAX = 10

    /** Separator that cannot appear in a query — normalizeQuery strips control chars. */
    private const val SEP = ""

    /**
     * Collapses whitespace and trims. Without this "one piece" and "one  piece "
     * are two different history entries, and the list fills up with the same
     * search wearing different amounts of spacing.
     */
    fun normalizeQuery(raw: String): String =
        raw.replace(Regex("[\\p{Cntrl}]"), " ").trim().replace(Regex("\\s+"), " ")

    /**
     * Returns [existing] with [query] moved to the front, capped at [max].
     *
     * Case-INSENSITIVE de-duplication, but the newly typed casing wins: someone
     * who types "Naruto" after having searched "naruto" gets one entry reading
     * "Naruto". Matching case-sensitively left near-identical duplicates in the
     * list, which is the specific thing that makes a history feel unmaintained.
     *
     * A blank query returns the list untouched — submitting an empty field
     * should not evict a real entry.
     */
    fun merged(existing: List<String>, query: String, max: Int = MAX): List<String> {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return existing
        val rest = existing.filterNot { it.equals(q, ignoreCase = true) }
        return (listOf(q) + rest).take(max)
    }

    /**
     * History entries that could complete [prefix], best-first.
     *
     * Entries that START with the typed text rank above ones that merely contain
     * it — typing "one" should offer "one piece" before "the last one", because
     * the first is a completion of what is being typed and the second is not.
     * An empty prefix returns the whole list, which is what the idle state shows.
     */
    fun matching(existing: List<String>, prefix: String, limit: Int = 6): List<String> {
        val p = normalizeQuery(prefix)
        if (p.isEmpty()) return existing.take(limit)
        val starts = existing.filter { it.startsWith(p, ignoreCase = true) }
        val contains = existing.filter {
            !it.startsWith(p, ignoreCase = true) && it.contains(p, ignoreCase = true)
        }
        // An entry identical to what's already typed is not a suggestion.
        return (starts + contains).filterNot { it.equals(p, ignoreCase = true) }.take(limit)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recent(context: Context): List<String> =
        prefs(context).getString(KEY, "")
            ?.split(SEP)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun store(context: Context, list: List<String>) {
        prefs(context).edit().putString(KEY, list.joinToString(SEP)).apply()
    }

    /** Records a search and returns the updated list, so callers need no re-read. */
    fun record(context: Context, query: String): List<String> =
        merged(recent(context), query).also { store(context, it) }

    fun remove(context: Context, query: String): List<String> =
        recent(context).filterNot { it.equals(query, ignoreCase = true) }.also { store(context, it) }

    fun clear(context: Context) = store(context, emptyList())
}
