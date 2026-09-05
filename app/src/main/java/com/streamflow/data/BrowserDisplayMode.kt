package com.streamflow.data

import android.content.Context

/**
 * Display mode (desktop vs mobile) for every web-site tab — Donghua, Drama,
 * MKissa and every user-added custom tab.
 *
 * ## Why this is deliberately GLOBAL and not per-tab
 *
 * The requirement is that the site tabs behave *consistently*: opening
 * Donghua → Drama → a custom tab must never hand back a different layout than
 * the one before it. A per-tab setting is exactly how that consistency rots —
 * one tab silently keeps an old value and the app feels broken.
 *
 * So there is a single stored flag, read by every tab. Flipping it from any
 * tab's toolbar flips it for all of them. The only way the mode changes is a
 * deliberate user action, which is the difference between "consistent" and
 * "frozen".
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

    /** Layout width, in CSS px, that sites are asked to render at. */
    const val DESKTOP_WIDTH_CSS_PX = 1100

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
     * forces the engine to lay out at [DESKTOP_WIDTH_CSS_PX], which the
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

    fun userAgent(desktop: Boolean): String = if (desktop) DESKTOP_UA else MOBILE_UA

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
     * at [DESKTOP_WIDTH_CSS_PX] and then scale that down to whatever the window
     * actually is. That scaling is what keeps it responsive: the same page fits a
     * small phone, a large phone, a tablet and a rotated screen without any
     * width-specific handling here, and re-fits itself on rotation.
     *
     * Returning to mobile mode restores `width=device-width` rather than just
     * dropping the override, because the original tag is gone by then.
     */
    fun viewportScript(desktop: Boolean, pinned: Boolean = true): String? {
        // Desktop layout without width pinning: leave the page's own viewport
        // tag exactly as the site wrote it. Injecting nothing is the point --
        // this is the escape hatch for players that break under a page scale,
        // and rewriting the tag "harmlessly" would defeat it.
        if (desktop && !pinned) return null
        val content =
            if (desktop) "width=$DESKTOP_WIDTH_CSS_PX"
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
