package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the pure list logic behind search suggestions. No Android types are
 * involved, so this runs on the JVM with no device and no Robolectric.
 */
class SearchHistoryTest {

    // ── normalizeQuery ───────────────────────────────────────────────────────

    @Test fun `collapses runs of whitespace`() {
        assertEquals("one piece", SearchHistory.normalizeQuery("one   piece"))
    }

    @Test fun `trims surrounding whitespace`() {
        assertEquals("naruto", SearchHistory.normalizeQuery("  naruto  "))
    }

    @Test fun `strips control characters from pasted text`() {
        // Pasting from another app routinely brings a trailing newline
        assertEquals("solo leveling", SearchHistory.normalizeQuery("solo\nleveling\t"))
    }

    // ── merged ───────────────────────────────────────────────────────────────

    @Test fun `newest query goes to the front`() {
        assertEquals(
            listOf("b", "a"),
            SearchHistory.merged(listOf("a"), "b")
        )
    }

    @Test fun `repeat search moves the entry up instead of duplicating`() {
        assertEquals(
            listOf("a", "c", "b"),
            SearchHistory.merged(listOf("c", "b", "a"), "a")
        )
    }

    @Test fun `deduplication ignores case and keeps the newest casing`() {
        assertEquals(
            listOf("Naruto"),
            SearchHistory.merged(listOf("naruto"), "Naruto")
        )
    }

    @Test fun `blank query never evicts a real entry`() {
        val existing = listOf("a", "b")
        assertEquals(existing, SearchHistory.merged(existing, "   "))
    }

    @Test fun `list is capped at max, dropping the oldest`() {
        val existing = listOf("5", "4", "3", "2", "1")
        assertEquals(
            listOf("6", "5", "4"),
            SearchHistory.merged(existing, "6", max = 3)
        )
    }

    @Test fun `stored query is normalized, not stored raw`() {
        assertEquals(
            listOf("one piece"),
            SearchHistory.merged(emptyList(), "  one   piece ")
        )
    }

    // ── matching ─────────────────────────────────────────────────────────────

    @Test fun `empty prefix returns the whole history`() {
        val existing = listOf("a", "b")
        assertEquals(existing, SearchHistory.matching(existing, ""))
    }

    @Test fun `prefix matches rank above substring matches`() {
        val existing = listOf("the last one", "one piece")
        assertEquals(
            listOf("one piece", "the last one"),
            SearchHistory.matching(existing, "one")
        )
    }

    @Test fun `matching is case insensitive`() {
        assertEquals(
            listOf("Naruto Shippuden"),
            SearchHistory.matching(listOf("Naruto Shippuden"), "naru")
        )
    }

    @Test fun `an exact match is not offered back as a suggestion`() {
        // Suggesting the text already in the box is a wasted row
        assertEquals(
            emptyList<String>(),
            SearchHistory.matching(listOf("naruto"), "naruto")
        )
    }

    @Test fun `non-matching entries are excluded`() {
        assertEquals(
            emptyList<String>(),
            SearchHistory.matching(listOf("one piece", "bleach"), "zzz")
        )
    }

    @Test fun `suggestion count is limited`() {
        val existing = listOf("aa", "ab", "ac", "ad")
        assertEquals(listOf("aa", "ab"), SearchHistory.matching(existing, "a", limit = 2))
    }
}
