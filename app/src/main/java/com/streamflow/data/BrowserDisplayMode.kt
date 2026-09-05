package com.streamflow.data

import android.content.Context

/**
 * Display mode (desktop vs mobile) for every web-site tab — Donghua, Drama,
 * MKissa and every user-added custom tab.
 *
 * ## A global default, with deliberate per-tab exceptions
 *
 * This started as one global flag and nothing else, because the tabs behaving
 * *consistently* matters: opening Donghua → Drama → a custom tab should not
 * hand back a different layout each time. Silent per-tab drift is exactly how
 * that consistency rots.
 *
 * What the flag could not express is that these sites are not the same. One
 * may read far better as a desktop grid while another's mobile layout is the
 * good one, and forcing a single answer onto all of them makes the setting
 * useless for whichever site loses.
 *
 * So there are two levels, and the distinction is *who chose*:
 *
 *  - the global flag is the DEFAULT, set in Settings, and every tab follows it
 *  - a tab whose mode was flipped from its own toolbar remembers that choice
 *    and stops following the default
 *
 * A per-tab value therefore only ever exists because the user stood in that
 * tab and asked for it — it is an exception, not drift. [siteMode] returns
 * [SiteMode.DEFAULT] until then, and "Use the default for this site" puts a
 * tab back under the global flag. Settings shows how many tabs have opted out,
 * so a global switch that "does nothing" is explainable rather than mysterious.
 *
 * ## Why SharedPreferences and not DataStore
 *
 * It has to be readable *synchronously*, before the first frame. DataStore is
 * a Flow: the WebView would be built with the default, then rebuilt when the
 * real value arrived, reloading the page and losing the user's scroll position
 * on every single tab open. [AdblockBrowserScreen] already keys its per-site
 * "last page" state off SharedPreferences for the same reason.
 *
 * ## Why desktop is the default
 *
 * These sites' mobile layouts show fewer episodes per screen and bury the
 * player. The desktop layout is the one worth looking at on a large phone.
 *
 * NOTE — this was tried once before and reverted (see the UA comment in
 * AdblockBrowserScreen): a desktop UA alone made donghuafun load unreliably
 * and surfaced more ads. That attempt swapped the user-agent and nothing else,
 * so the site sent a desktop page that WebView then laid out at phone width.
 * This implementation also pins the viewport and lets overview mode scale it,
 * which is the part that was missing. The toolbar toggle exists so that if a
 * site still misbehaves, it is one tap to fix rather than another release.
 */
object BrowserDisplayMode {

    private const val PREFS = "browser_display"
    private const val KEY_DESKTOP = "desktop"
    private const val KEY_PIN_VIEWPORT = "pin_viewport"
    private const val KEY_DESKTOP_WIDTH = "desktop_width"
    /** One key per tab that has opted out of the global default. */
    private const val KEY_SITE_PREFIX = "site_mode_"

    /** Layout width, in CSS px, that sites are asked to render at by default. */
    const val DEFAULT_DESKTOP_WIDTH_CSS_PX = 1100

    /**
     * The widths worth offering, in CSS px.
     *
     * Deliberately a short list of presets rather than a free slider. The width
     * is not a taste knob — it decides how far the engine has to scale the page
     * down to fit the window, and every value in between two presets looks
     * essentially identical while giving the user a number to fiddle with
     * forever. The useful span is "narrow enough to read" (900) to "a real
     * desktop, accept the shrinking" (1440).
     */
    val WIDTH_CHOICES = listOf(900, 1000, 1100, 1280, 1440)

    /**
     * How one tab relates to the global default.
     *
     * [DEFAULT] is not a third layout — it means "whatever Settings says",
     * which is the state every tab starts in and returns to.
     */
    enum class SiteMode { DEFAULT, DESKTOP, MOBILE }

    /**
     * Chrome-on-Windows. Sites branch on "Mobile" being absent from the UA
     * token, so this must not carry an Android platform string.
     */
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Chrome-on-Android — what the ad-blocking rules were originally tuned against. */
    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDesktop(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DESKTOP, true)

    fun setDesktop(context: Context, desktop: Boolean) {
        prefs(context).edit().putBoolean(KEY_DESKTOP, desktop).apply()
    }

    /**
     * Whether desktop mode also PINS the layout width, on top of swapping the
     * user-agent.
     *
     * These are two separate interventions that were welded into one switch,
     * and they fail differently. The user-agent asks the site for its desktop
     * page. The viewport override then deletes the page's own viewport tag and
     * forces the engine to lay out at [DEFAULT_DESKTOP_WIDTH_CSS_PX], which the
     * WebView scales down to fit the window.
     *
     * That scaling is what makes a desktop page readable on a phone, and it is
     * also the half most likely to break a `<video>`: a player that measures
     * the window at init, or a video surface being composited under a page
     * scale, can end up drawing nothing at all — a page that renders correctly
     * with a black rectangle where the video should be.
     *
     * Separating them means a site whose player dislikes the scaling can still
     * have the desktop layout, instead of the only remedy being to give up
     * desktop mode entirely. Defaults to true, which is exactly the behaviour
     * this setting was carved out of.
     */
    fun isViewportPinned(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PIN_VIEWPORT, true)

    fun setViewportPinned(context: Context, pinned: Boolean) {
        prefs(context).edit().putBoolean(KEY_PIN_VIEWPORT, pinned).apply()
    }

    // ── the layout width ────────────────────────────────────────────────────

    /**
     * Snaps any stored value onto [WIDTH_CHOICES].
     *
     * Pure, so the clamping is testable, and applied on read rather than only
     * on write: a value that predates a change to the preset list, or one left
     * behind by a downgrade, would otherwise be laid out at a width the UI
     * cannot show as selected — a setting whose screen disagrees with what the
     * page is doing.
     */
    fun normalizeWidth(px: Int): Int =
        WIDTH_CHOICES.minByOrNull { kotlin.math.abs(it - px) } ?: DEFAULT_DESKTOP_WIDTH_CSS_PX

    fun desktopWidth(context: Context): Int =
        normalizeWidth(prefs(context).getInt(KEY_DESKTOP_WIDTH, DEFAULT_DESKTOP_WIDTH_CSS_PX))

    fun setDesktopWidth(context: Context, px: Int) {
        prefs(context).edit().putInt(KEY_DESKTOP_WIDTH, normalizeWidth(px)).apply()
    }

    // ── per-tab exceptions to the global default ────────────────────────────

    /**
     * Resolves the two levels into the single answer a tab needs.
     *
     * Pure and separate from storage so the precedence rule — an explicit
     * per-tab choice beats the default, and only [SiteMode.DEFAULT] defers —
     * is tested directly rather than inferred from what the browser does.
     */
    fun resolveDesktop(globalDesktop: Boolean, mode: SiteMode): Boolean = when (mode) {
        SiteMode.DEFAULT -> globalDesktop
        SiteMode.DESKTOP -> true
        SiteMode.MOBILE -> false
    }

    /**
     * [siteKey] is the tab's own SharedPreferences name ("donghua_prefs"), which
     * is already unique per tab and stable across restarts and renames.
     */
    fun siteMode(context: Context, siteKey: String): SiteMode {
        val raw = prefs(context).getString(KEY_SITE_PREFIX + siteKey, null) ?: return SiteMode.DEFAULT
        // An unrecognised value means a downgrade wrote something this build
        // does not know; following the default is the harmless reading.
        return runCatching { SiteMode.valueOf(raw) }.getOrDefault(SiteMode.DEFAULT)
    }

    fun setSiteMode(context: Context, siteKey: String, mode: SiteMode) {
        val e = prefs(context).edit()
        // DEFAULT is stored as absence, so "follows the default" and "was never
        // touched" cannot drift apart and [siteOverrideCount] stays truthful.
        if (mode == SiteMode.DEFAULT) e.remove(KEY_SITE_PREFIX + siteKey)
        else e.putString(KEY_SITE_PREFIX + siteKey, mode.name)
        e.apply()
    }

    fun isDesktopFor(context: Context, siteKey: String): Boolean =
        resolveDesktop(isDesktop(context), siteMode(context, siteKey))

    /** How many tabs have opted out — shown in Settings so a default that looks ignored is explainable. */
    fun siteOverrideCount(context: Context): Int =
        prefs(context).all.keys.count { it.startsWith(KEY_SITE_PREFIX) }

    fun clearSiteModes(context: Context) {
        val e = prefs(context).edit()
        prefs(context).all.keys.filter { it.startsWith(KEY_SITE_PREFIX) }.forEach { e.remove(it) }
        e.apply()
    }

    fun userAgent(desktop: Boolean): String = if (desktop) DESKTOP_UA else MOBILE_UA

    /**
     * Pages that are showing a video, where the layout width must never be
     * pinned no matter what the setting says.
     *
     * v6.26.0 split the width pin out of desktop mode on the theory that the
     * pin, not the user-agent, was blacking out video. That theory is now
     * measured rather than assumed. Fetching the same donghuafun episode page
     * under both user-agents returns byte-identical HTML apart from a 9-char
     * Cloudflare request id; there is no `userAgent` or mobile-detection
     * branch in the served markup or in the site's player.js; and every page —
     * home, series, episode — ships `width=device-width`. So the user-agent
     * changes nothing at all on these sites, and the desktop layout users see
     * is produced *entirely* by [viewportScript] rewriting that tag.
     *
     * Which makes the two facts fit together exactly: the only thing desktop
     * mode really does is pin the width, and pinning 1100px into a ~400px
     * window means the engine composites the whole page — the video layer
     * included — at roughly a third scale. That is the black rectangle.
     *
     * The pin is what makes a browsing grid readable and it is what breaks a
     * player, so it is applied per page rather than per app: pinned while
     * browsing, never on the page with the video. Matching is on the path and
     * query only — the host is dropped, so a site with "watch" in its domain
     * does not put every one of its pages in mobile layout.
     */
    private val PLAYER_PATH = Regex(
        // /index.php/vod/play/id/1/... but NOT /user/plays.html
        """/play(?:[/.?]|$)""" +
        """|/watch""" +
        """|/embed/""" +
        // Episode-1, /ep-12, /episode/3 — the trailing digit keeps an
        // "/episodes" index page, which is browsing, out of this.
        """|(?:^|[/\-_])ep(?:isode)?[-_/]?\d"""
    )

    fun isPlayerPage(url: String): Boolean {
        val afterScheme = url.substringAfter("://", url)
        val slash = afterScheme.indexOf('/')
        if (slash < 0) return false
        return PLAYER_PATH.containsMatchIn(afterScheme.substring(slash).lowercase())
    }

    /**
     * The viewport script to run for one specific page.
     *
     * This is the entry point [AdblockBrowserScreen] calls; [viewportScript] is
     * the mode-level primitive underneath it.
     */
    fun viewportScriptFor(
        url: String,
        desktop: Boolean,
        pinned: Boolean,
        widthPx: Int = DEFAULT_DESKTOP_WIDTH_CSS_PX
    ): String? = when {
        !desktop -> viewportScript(desktop = false, pinned = pinned)
        isPlayerPage(url) -> UNPIN_ON_PLAYER_JS
        pinned -> viewportScript(desktop = true, pinned = true, widthPx = widthPx)
        else -> null
    }

    /**
     * Guarantees scale 1 on a page with a player, without trampling a viewport
     * tag the site already got right.
     *
     * Most of these pages ship `width=device-width, initial-scale=1,
     * viewport-fit=cover`, and blindly replacing that would drop the
     * `viewport-fit` the site uses to lay out under a display cutout — trading
     * a black video for a notch bug. So the tag is only rewritten when it is
     * missing or does not ask for device width, which is also the case that
     * would otherwise leave WebView on its 980px default and scale the page
     * anyway.
     */
    private val UNPIN_ON_PLAYER_JS = """
        (function(){
          try{
            var tags = document.querySelectorAll('meta[name="viewport"]');
            for(var i=0;i<tags.length;i++){
              var c = tags[i].getAttribute('content') || '';
              if(c.indexOf('device-width') !== -1) return;
            }
            var head = document.head || document.getElementsByTagName('head')[0];
            if(!head) return;
            for(var j=0;j<tags.length;j++) tags[j].parentNode.removeChild(tags[j]);
            var m = document.createElement('meta');
            m.setAttribute('name','viewport');
            m.setAttribute('content','width=device-width, initial-scale=1');
            head.appendChild(m);
          }catch(e){}
        })();
    """.trimIndent()

    /**
     * Forces the page to lay out at a fixed desktop width instead of obeying its
     * own `width=device-width` viewport tag.
     *
     * Sending a desktop user-agent is only half of desktop mode. Plenty of these
     * sites ship one responsive stylesheet and decide the layout purely from the
     * viewport meta tag, so with the UA swapped but the viewport left alone the
     * page still collapses to a single mobile column — the site looks broken in a
     * new way rather than looking like a desktop.
     *
     * Rewriting the tag to a fixed width, with useWideViewPort +
     * loadWithOverviewMode set on the WebView, makes the engine lay the page out
     * at [DEFAULT_DESKTOP_WIDTH_CSS_PX] and then scale that down to whatever the window
     * actually is. That scaling is what keeps it responsive: the same page fits a
     * small phone, a large phone, a tablet and a rotated screen without any
     * width-specific handling here, and re-fits itself on rotation.
     *
     * Returning to mobile mode restores `width=device-width` rather than just
     * dropping the override, because the original tag is gone by then.
     */
    fun viewportScript(
        desktop: Boolean,
        pinned: Boolean = true,
        widthPx: Int = DEFAULT_DESKTOP_WIDTH_CSS_PX
    ): String? {
        // Desktop layout without width pinning: leave the page's own viewport
        // tag exactly as the site wrote it. Injecting nothing is the point --
        // this is the escape hatch for players that break under a page scale,
        // and rewriting the tag "harmlessly" would defeat it.
        if (desktop && !pinned) return null
        val content =
            if (desktop) "width=${normalizeWidth(widthPx)}"
            else "width=device-width, initial-scale=1"
        return """
        (function(){
          try{
            var head = document.head || document.getElementsByTagName('head')[0];
            if(!head) return;
            var tags = document.querySelectorAll('meta[name="viewport"]');
            for(var i=0;i<tags.length;i++) tags[i].parentNode.removeChild(tags[i]);
            var m = document.createElement('meta');
            m.setAttribute('name','viewport');
            m.setAttribute('content','$content');
            head.appendChild(m);
          }catch(e){}
        })();
        """.trimIndent()
    }
}
