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
        assertTrue(js!!.contains("width=${BrowserDisplayMode.DEFAULT_DESKTOP_WIDTH_CSS_PX}"))
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

    // ── which pages carry a player ──────────────────────────────────────────
    //
    // Measured, not guessed: donghuafun returns byte-identical HTML for both
    // user-agents and ships width=device-width on every page, so the desktop
    // layout is produced solely by the width pin -- and the pin is therefore
    // also the only thing that could have blacked out the video.

    @Test fun `real watch URLs are recognised`() {
        val watching = listOf(
            "https://donghuafun.com/index.php/vod/play/id/1/sid/1/nid/1.html",
            "https://kisskh.co/Drama/Some-Title/Episode-1?id=1234&ep=5678",
            "https://example.com/anime/title/ep-12",
            "https://example.com/watch/9981",
            "https://example.com/embed/abc123",
        )
        for (u in watching) {
            assertTrue("should be a player page: $u", BrowserDisplayMode.isPlayerPage(u))
        }
    }

    // Every one of these is a browsing page that must keep the desktop grid.
    // "/user/plays.html" and "/episodes" are the two that a loose substring
    // match gets wrong, which is why the patterns require a separator or a
    // following digit.
    @Test fun `browsing URLs keep the desktop layout`() {
        val browsing = listOf(
            "https://donghuafun.com/",
            "https://donghuafun.com/index.php/vod/detail/id/1.html",
            "https://donghuafun.com/index.php/art/index.html",
            "https://donghuafun.com/index.php/user/plays.html",
            "https://donghuafun.com/index.php/label/weekday.html",
            "https://example.com/anime/title/episodes",
        )
        for (u in browsing) {
            assertTrue("should NOT be a player page: $u", !BrowserDisplayMode.isPlayerPage(u))
        }
    }

    // The host is dropped before matching, so a site whose DOMAIN says "watch"
    // does not put its entire catalogue into mobile layout.
    @Test fun `the host is not matched`() {
        assertTrue(!BrowserDisplayMode.isPlayerPage("https://watchanime.com/"))
        assertTrue(!BrowserDisplayMode.isPlayerPage("https://episode4.example.com/browse"))
    }

    // ── the per-page decision ───────────────────────────────────────────────

    @Test fun `a player page is never pinned, even with pinning on`() {
        val js = BrowserDisplayMode.viewportScriptFor(
            "https://donghuafun.com/index.php/vod/play/id/1/sid/1/nid/1.html",
            desktop = true, pinned = true
        )
        assertNotNull(js)
        assertTrue(
            "must not pin the desktop width on a watch page",
            !js!!.contains("width=${BrowserDisplayMode.DEFAULT_DESKTOP_WIDTH_CSS_PX}")
        )
        assertTrue(js.contains("width=device-width"))
    }

    // A site that already asked for device width keeps its own tag, so
    // viewport-fit=cover (display-cutout handling) is not thrown away.
    @Test fun `a correct viewport tag is left alone on a player page`() {
        val js = BrowserDisplayMode.viewportScriptFor(
            "https://donghuafun.com/index.php/vod/play/id/1/sid/1/nid/1.html",
            desktop = true, pinned = true
        )!!
        assertTrue(js.contains("device-width"))
        assertTrue("must bail out before rewriting", js.contains("return"))
        assertTrue(js.indexOf("indexOf('device-width')") < js.indexOf("appendChild"))
    }

    @Test fun `browsing pages still get the desktop width`() {
        val js = BrowserDisplayMode.viewportScriptFor(
            "https://donghuafun.com/index.php/vod/detail/id/1.html",
            desktop = true, pinned = true
        )
        assertNotNull(js)
        assertTrue(js!!.contains("width=${BrowserDisplayMode.DEFAULT_DESKTOP_WIDTH_CSS_PX}"))
    }

    // The manual switch still wins everywhere it applied before.
    @Test fun `pinning off still injects nothing while browsing`() {
        assertNull(
            BrowserDisplayMode.viewportScriptFor(
                "https://donghuafun.com/index.php/vod/detail/id/1.html",
                desktop = true, pinned = false
            )
        )
    }

    @Test fun `mobile mode is unchanged on every kind of page`() {
        for (u in listOf("https://donghuafun.com/", "https://donghuafun.com/vod/play/id/1.html")) {
            val js = BrowserDisplayMode.viewportScriptFor(u, desktop = false, pinned = true)
            assertNotNull(js)
            assertTrue(js!!.contains("width=device-width"))
        }
    }

    // ── the layout width is a setting now ───────────────────────────────────

    @Test fun `each width choice reaches the viewport tag`() {
        for (px in BrowserDisplayMode.WIDTH_CHOICES) {
            val js = BrowserDisplayMode.viewportScript(desktop = true, pinned = true, widthPx = px)
            assertNotNull(js)
            assertTrue("width $px missing from the tag", js!!.contains("width=$px"))
        }
    }

    @Test fun `the default width is what an unspecified call still produces`() {
        assertEquals(
            BrowserDisplayMode.viewportScript(
                desktop = true, pinned = true,
                widthPx = BrowserDisplayMode.DEFAULT_DESKTOP_WIDTH_CSS_PX
            ),
            BrowserDisplayMode.viewportScript(desktop = true, pinned = true)
        )
    }

    @Test fun `the default is one of the offered choices`() {
        assertTrue(
            BrowserDisplayMode.WIDTH_CHOICES.contains(BrowserDisplayMode.DEFAULT_DESKTOP_WIDTH_CSS_PX)
        )
    }

    // A stored value the current build does not offer -- written by an older or
    // newer version -- must still resolve to something selectable, or Settings
    // would show no option chosen while pages laid out at the stale width.
    @Test fun `any stored width snaps onto a real choice`() {
        for (px in listOf(-40, 0, 1, 640, 950, 1101, 1379, 5000)) {
            assertTrue(
                "normalize($px) escaped the choices",
                BrowserDisplayMode.WIDTH_CHOICES.contains(BrowserDisplayMode.normalizeWidth(px))
            )
        }
    }

    @Test fun `a width that is already a choice is left alone`() {
        for (px in BrowserDisplayMode.WIDTH_CHOICES) {
            assertEquals(px, BrowserDisplayMode.normalizeWidth(px))
        }
    }

    @Test fun `normalizing picks the nearest choice`() {
        assertEquals(900, BrowserDisplayMode.normalizeWidth(880))
        assertEquals(1100, BrowserDisplayMode.normalizeWidth(1090))
        assertEquals(1440, BrowserDisplayMode.normalizeWidth(9999))
    }

    // A junk width must not leak into the page even by the direct path.
    @Test fun `a nonsense width never reaches the viewport tag`() {
        val js = BrowserDisplayMode.viewportScript(desktop = true, pinned = true, widthPx = 99999)!!
        assertTrue(js.contains("width=1440"))
        assertTrue(!js.contains("99999"))
    }

    @Test fun `the chosen width flows through the per-page decision`() {
        val js = BrowserDisplayMode.viewportScriptFor(
            "https://donghuafun.com/index.php/vod/detail/id/1.html",
            desktop = true, pinned = true, widthPx = 1440
        )
        assertNotNull(js)
        assertTrue(js!!.contains("width=1440"))
    }

    // The width is a browsing-layout knob; a watch page is unscaled whatever it
    // is set to, or the fix from the previous release would be undone by it.
    @Test fun `no width choice re-scales a watch page`() {
        for (px in BrowserDisplayMode.WIDTH_CHOICES) {
            val js = BrowserDisplayMode.viewportScriptFor(
                "https://donghuafun.com/index.php/vod/play/id/1/sid/1/nid/1.html",
                desktop = true, pinned = true, widthPx = px
            )!!
            assertTrue("width $px leaked onto a watch page", !js.contains("width=$px"))
            assertTrue(js.contains("width=device-width"))
        }
    }

    // ── the global default vs a tab's own choice ────────────────────────────

    @Test fun `a tab with no choice of its own follows the default`() {
        assertTrue(BrowserDisplayMode.resolveDesktop(true, BrowserDisplayMode.SiteMode.DEFAULT))
        assertTrue(!BrowserDisplayMode.resolveDesktop(false, BrowserDisplayMode.SiteMode.DEFAULT))
    }

    // The whole point of an override: it holds regardless of what the default
    // is doing, in both directions.
    @Test fun `an explicit tab choice beats the default either way`() {
        for (global in listOf(true, false)) {
            assertTrue(BrowserDisplayMode.resolveDesktop(global, BrowserDisplayMode.SiteMode.DESKTOP))
            assertTrue(!BrowserDisplayMode.resolveDesktop(global, BrowserDisplayMode.SiteMode.MOBILE))
        }
    }

    // DEFAULT must stay a deferral, not a hidden third layout -- if it ever
    // resolved to a fixed value, flipping the Settings switch would silently
    // stop moving untouched tabs.
    @Test fun `DEFAULT is the only mode that tracks the global flag`() {
        val tracks = BrowserDisplayMode.SiteMode.values().filter { mode ->
            BrowserDisplayMode.resolveDesktop(true, mode) !=
                BrowserDisplayMode.resolveDesktop(false, mode)
        }
        assertEquals(listOf(BrowserDisplayMode.SiteMode.DEFAULT), tracks)
    }

    @Test fun `the player-page script cannot throw out of itself`() {
        val js = BrowserDisplayMode.viewportScriptFor(
            "https://example.com/watch/1", desktop = true, pinned = true
        )!!
        assertTrue(js.contains("try{"))
        assertTrue(js.contains("catch(e)"))
    }
}
