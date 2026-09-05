package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {

    private fun titles(q: String) = SettingsIndex.search(q).map { it.entry.title }

    // Blank queries must not dump the whole index: the category cards are the
    // browse view, and search replaces them on screen.
    @Test fun `an empty query returns nothing`() {
        assertTrue(SettingsIndex.search("").isEmpty())
        assertTrue(SettingsIndex.search("   ").isEmpty())
    }

    @Test fun `an exact title outranks a partial one`() {
        val r = SettingsIndex.search("theme")
        assertEquals("Theme", r.first().entry.title)
        assertEquals(SettingsIndex.Match.TITLE_EXACT, r.first().match)
    }

    // The reason search exists. Someone who wants dark mode types "dark", and
    // no setting in the app is called that.
    @Test fun `the words people actually type find the right setting`() {
        assertTrue("dark -> Theme", titles("dark").contains("Theme"))
        assertTrue("pin -> App lock", titles("pin").contains("App lock"))
        assertTrue("fingerprint -> App lock", titles("fingerprint").contains("App lock"))
        assertTrue("pip -> Pop-up video on exit", titles("pip").contains("Pop-up video on exit"))
        assertTrue("bass -> Equalizer", titles("bass").contains("Equalizer"))
        assertTrue("changelog -> What's new", titles("changelog").contains("What's new"))
        assertTrue("takeout -> subscriptions import",
            titles("takeout").contains("Import YouTube subscriptions"))
    }

    @Test fun `search is case-insensitive and ignores surrounding space`() {
        assertEquals(titles("app lock"), titles("  APP Lock "))
        assertTrue(titles("WI-FI").contains("Download on Wi-Fi only"))
    }

    // Typing a page name should surface what is on that page — this is how
    // someone finds the privacy controls without knowing their names.
    @Test fun `a page name finds the settings on it`() {
        val r = titles("privacy")
        assertTrue("expected App lock among $r", r.contains("App lock"))
        assertTrue("expected Incognito mode among $r", r.contains("Incognito mode"))
    }

    @Test fun `a query that matches nothing returns nothing`() {
        assertTrue(SettingsIndex.search("zzzzqqq").isEmpty())
    }

    // A list that reshuffles between keystrokes is worse than no search, and
    // ordering by anything time- or hash-dependent would do exactly that.
    @Test fun `results are stable across repeated calls`() {
        assertEquals(titles("data"), titles("data"))
        assertEquals(titles("d"), titles("d"))
    }

    @Test fun `the result limit is respected`() {
        assertTrue(SettingsIndex.search("a", limit = 3).size <= 3)
        assertTrue(SettingsIndex.search("e", limit = 12).size <= 12)
    }

    // Every entry must name a category that is actually a Settings page. The
    // structural checker proves this against the UI too, but a wrong category
    // here is a navigation dead end and worth failing fast on.
    @Test fun `every entry points at a known category`() {
        val known = setOf(
            "Appearance", "Home", "Playback", "Notifications", "Downloads",
            "Storage", "Backup", "Privacy", "AI", "About",
        )
        for (e in SettingsIndex.entries) {
            assertTrue("unknown category \"${e.category}\" for \"${e.title}\"", e.category in known)
            assertTrue("blank title in ${e.category}", e.title.isNotBlank())
        }
    }

    @Test fun `no setting is indexed twice`() {
        val seen = SettingsIndex.entries.map { it.title.lowercase() }
        assertEquals(seen.size, seen.toSet().size)
    }

    // Synonyms are the whole point; an entry with none is only findable by
    // someone who already knows its exact name.
    @Test fun `entries carry synonyms`() {
        val bare = SettingsIndex.entries.filter { it.synonyms.isEmpty() }
        assertTrue("entries with no synonyms: ${bare.map { it.title }}", bare.isEmpty())
    }
}
