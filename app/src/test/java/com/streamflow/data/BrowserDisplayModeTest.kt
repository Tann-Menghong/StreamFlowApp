package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDisplayModeTest {

    // The bug this split exists for: desktop mode did two things at once, and
    // one of them -- pinning the layout width, which makes the engine scale the
    // whole page -- can leave a video compositing as a black rectangle on a
    // page that otherwise renders correctly. Welded together, the only remedy
    // was giving up the desktop layout entirely.
    @Test fun `desktop layout without width pinning injects nothing`() {
        assertNull(BrowserDisplayMode.viewportScript(desktop = true, pinned = false))
    }

    @Test fun `desktop with pinning forces the desktop width`() {
        val js = BrowserDisplayMode.viewportScript(desktop = true, pinned = true)
        assertNotNull(js)
        assertTrue(js!!.contains("width=${BrowserDisplayMode.DESKTOP_WIDTH_CSS_PX}"))
    }

    // Mobile mode has to actively restore device-width: by the time it runs, the
    // page's own viewport tag has already been removed by a previous desktop
    // pass, so merely dropping the override would leave no tag at all.
    @Test fun `mobile restores device width, pinned or not`() {
        for (pinned in listOf(true, false)) {
            val js = BrowserDisplayMode.viewportScript(desktop = false, pinned = pinned)
            assertNotNull("mobile must always inject (pinned=$pinned)", js)
            assertTrue(js!!.contains("width=device-width"))
            assertTrue(js.contains("initial-scale=1"))
        }
    }

    @Test fun `pinning defaults to on, preserving the old behaviour`() {
        assertEquals(
            BrowserDisplayMode.viewportScript(desktop = true, pinned = true),
            BrowserDisplayMode.viewportScript(desktop = true)
        )
    }

    // The script removes every existing viewport tag before adding its own;
    // appending a second one leaves the engine reading whichever it likes.
    @Test fun `the script clears existing viewport tags first`() {
        val js = BrowserDisplayMode.viewportScript(desktop = true, pinned = true)!!
        assertTrue(js.contains("querySelectorAll"))
        assertTrue(js.contains("removeChild"))
        assertTrue(js.indexOf("removeChild") < js.indexOf("appendChild"))
    }

    // It runs on every page load of every site tab, including hostile ones, so
    // a throw would break the tab rather than the tag.
    @Test fun `the script cannot throw out of itself`() {
        for (desktop in listOf(true, false)) {
            val js = BrowserDisplayMode.viewportScript(desktop, pinned = true)!!
            assertTrue("no try/catch for desktop=$desktop", js.contains("try{"))
            assertTrue("no catch for desktop=$desktop", js.contains("catch(e)"))
        }
    }

    // ── user agent ──────────────────────────────────────────────────────────

    // Sites branch on the literal "Mobile" token, so a desktop UA carrying an
    // Android platform string gets served the mobile page anyway.
    @Test fun `the desktop agent does not look mobile`() {
        val ua = BrowserDisplayMode.userAgent(true)
        assertTrue("desktop UA must not say Mobile: $ua", !ua.contains("Mobile"))
        assertTrue("desktop UA must not say Android: $ua", !ua.contains("Android"))
        assertTrue(ua.contains("Windows"))
    }

    @Test fun `the mobile agent looks mobile`() {
        val ua = BrowserDisplayMode.userAgent(false)
        assertTrue(ua.contains("Mobile"))
        assertTrue(ua.contains("Android"))
    }

    @Test fun `the two agents are different`() {
        assertTrue(BrowserDisplayMode.userAgent(true) != BrowserDisplayMode.userAgent(false))
    }

    // The UA and the width are now independent, which is the whole point: the
    // agent must not change just because pinning was switched off.
    @Test fun `width pinning does not affect the user agent`() {
        assertEquals(BrowserDisplayMode.DESKTOP_UA, BrowserDisplayMode.userAgent(true))
        assertEquals(BrowserDisplayMode.MOBILE_UA, BrowserDisplayMode.userAgent(false))
    }
}
