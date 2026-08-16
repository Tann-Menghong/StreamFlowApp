package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the custom-tab URL rules.
 *
 * This is what the user actually types, so it is the part most likely to be
 * wrong and the part where being wrong is most visible ("that doesn't look like
 * a website address" for a perfectly good address).
 */
class CustomTabsTest {

    // ── The common case: people type a bare domain ────────────────────────────

    @Test fun `bare domain gets https`() {
        assertEquals("https://kisskh.co", CustomTabs.normalizeUrl("kisskh.co"))
    }

    @Test fun `existing scheme is preserved`() {
        assertEquals("https://kisskh.co", CustomTabs.normalizeUrl("https://kisskh.co"))
        assertEquals("http://example.com", CustomTabs.normalizeUrl("http://example.com"))
    }

    @Test fun `paths and queries survive`() {
        assertEquals("https://site.com/browse?x=1",
            CustomTabs.normalizeUrl("site.com/browse?x=1"))
    }

    @Test fun `whitespace and pasted newlines are cleaned`() {
        assertEquals("https://kisskh.co", CustomTabs.normalizeUrl("  kisskh.co \n"))
    }

    // ── Regression: host:port used to be rejected as a "scheme" ───────────────

    @Test fun `host with port is accepted`() {
        // The old rule matched any leading word followed by ':' and treated it
        // as a non-web scheme, so this was refused as an invalid address.
        assertEquals("https://example.com:8080", CustomTabs.normalizeUrl("example.com:8080"))
    }

    @Test fun `localhost with port is accepted`() {
        assertEquals("https://localhost:3000", CustomTabs.normalizeUrl("localhost:3000"))
    }

    // ── Rejections ────────────────────────────────────────────────────────────

    @Test fun `bare word without a dot is rejected`() {
        assertNull(CustomTabs.normalizeUrl("kisskh"))
    }

    @Test fun `empty input is rejected`() {
        assertNull(CustomTabs.normalizeUrl(""))
        assertNull(CustomTabs.normalizeUrl("   "))
    }

    @Test fun `dangerous schemes are refused`() {
        assertNull(CustomTabs.normalizeUrl("javascript:alert(1)"))
        assertNull(CustomTabs.normalizeUrl("file:///sdcard/x.html"))
        assertNull(CustomTabs.normalizeUrl("content://media/external"))
        assertNull(CustomTabs.normalizeUrl("intent://scan/#Intent;end"))
        assertNull(CustomTabs.normalizeUrl("data:text/html,<h1>x"))
    }

    @Test fun `other schemes with an authority are refused`() {
        assertNull(CustomTabs.normalizeUrl("ftp://files.example.com"))
    }

    @Test fun `malformed hosts are rejected`() {
        assertNull(CustomTabs.normalizeUrl(".com"))
        assertNull(CustomTabs.normalizeUrl("site..com"))
        assertNull(CustomTabs.normalizeUrl("site.com."))
    }

    // ── Derived tab name ──────────────────────────────────────────────────────

    @Test fun `title comes from the host`() {
        assertEquals("Kisskh", CustomTabs.titleFromUrl("https://kisskh.co"))
        assertEquals("Example", CustomTabs.titleFromUrl("https://www.example.com/a/b"))
    }

    @Test fun `title ignores port and path`() {
        assertEquals("Example", CustomTabs.titleFromUrl("https://example.com:8080/watch"))
    }

    @Test fun `title falls back when there is no host`() {
        assertEquals("Site", CustomTabs.titleFromUrl(""))
    }

    @Test fun `title is length capped`() {
        val t = CustomTabs.titleFromUrl("https://averyveryverylongdomainname.com")
        assert(t.length <= 14) { "title too long: $t" }
    }
}
