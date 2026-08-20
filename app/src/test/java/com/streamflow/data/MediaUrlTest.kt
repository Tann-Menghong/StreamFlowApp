package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule these tests pin down is an ORDERING rule: scheme before extension.
 *
 * Three copies of this predicate existed and had drifted. The player screen's
 * copy checked extensions only, so a downloaded file:///…/Title.mp4 matched
 * ".mp4", was classified as a remote direct stream, and got rendered in a
 * WebView that cannot load a file:// source from an https base URL. Every one
 * of these cases is a real value one of the three copies had to handle.
 */
class MediaUrlTest {

    // ── The bug that started this ────────────────────────────────────────────

    @Test
    fun `downloaded mp4 file uri is a local file, not a direct stream`() {
        val url = "file:///storage/emulated/0/Download/StreamFlow/Episode 12.mp4"
        assertEquals(MediaKind.LOCAL_FILE, MediaUrl.classify(url))
        assertTrue(MediaUrl.isLocalFile(url))
        assertFalse(MediaUrl.isDirectStream(url))
    }

    @Test
    fun `downloaded audio m4a file uri is a local file`() {
        assertEquals(
            MediaKind.LOCAL_FILE,
            MediaUrl.classify("file:///storage/emulated/0/Download/song.m4a")
        )
    }

    @Test
    fun `content uri is a local file even with a media extension`() {
        assertEquals(
            MediaKind.LOCAL_FILE,
            MediaUrl.classify("content://media/external/video/media/1042")
        )
        assertEquals(
            MediaKind.LOCAL_FILE,
            MediaUrl.classify("content://downloads/all_downloads/517.mp4")
        )
    }

    @Test
    fun `bare absolute path is a local file`() {
        assertEquals(
            MediaKind.LOCAL_FILE,
            MediaUrl.classify("/storage/emulated/0/Movies/clip.webm")
        )
    }

    // ── Remote direct streams still belong to the WebView player ─────────────

    @Test
    fun `remote hls manifest is a direct stream`() {
        assertEquals(MediaKind.DIRECT_STREAM, MediaUrl.classify("https://cdn.example.com/a/b.m3u8"))
    }

    @Test
    fun `remote path-style hls is a direct stream`() {
        assertEquals(MediaKind.DIRECT_STREAM, MediaUrl.classify("https://pdtvhd.com/hls/ch7/index"))
        assertEquals(MediaKind.DIRECT_STREAM, MediaUrl.classify("https://site.tv/stream/1234"))
    }

    @Test
    fun `remote mp4 is a direct stream`() {
        assertEquals(MediaKind.DIRECT_STREAM, MediaUrl.classify("https://cdn.example.com/v/ep1.mp4"))
    }

    // ── YouTube is everything else ───────────────────────────────────────────

    @Test
    fun `youtube watch url is youtube`() {
        assertEquals(MediaKind.YOUTUBE, MediaUrl.classify("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(MediaKind.YOUTUBE, MediaUrl.classify("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `only youtube urls may reach the extractor`() {
        assertFalse(MediaUrl.isLocalOrDirect("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(MediaUrl.isLocalOrDirect("file:///storage/emulated/0/Download/a.mp4"))
        assertTrue(MediaUrl.isLocalOrDirect("https://cdn.example.com/a.m3u8"))
    }

    @Test
    fun `classification ignores case and surrounding whitespace`() {
        assertEquals(MediaKind.LOCAL_FILE, MediaUrl.classify("  FILE:///storage/A/B.MP4  "))
        assertEquals(MediaKind.DIRECT_STREAM, MediaUrl.classify("HTTPS://CDN.EXAMPLE.COM/X.M3U8"))
    }
}

/**
 * The presence counter, whose boolean predecessor was the reason the autoplay
 * countdown stopped appearing after the first related-video hop.
 */
class PlayerUiPresenceTest {

    private fun drain() { repeat(8) { PlayerUiPresence.exit() } }

    @Test
    fun `starts inactive`() {
        drain()
        assertFalse(PlayerUiPresence.active)
    }

    @Test
    fun `overlapping screens stay active until the last one leaves`() {
        drain()
        // Player -> Player navigation: the incoming screen composes while the
        // outgoing one is still alive. This is the exact sequence a boolean got
        // wrong, ending on false with a player still on screen.
        PlayerUiPresence.enter()   // incoming
        PlayerUiPresence.enter()   // (outgoing was already in)
        PlayerUiPresence.exit()    // outgoing disposes after its exit transition
        assertTrue("a player screen is still on screen", PlayerUiPresence.active)
        PlayerUiPresence.exit()
        assertFalse(PlayerUiPresence.active)
    }

    @Test
    fun `an unbalanced exit cannot drive the count negative`() {
        drain()
        PlayerUiPresence.exit()
        PlayerUiPresence.exit()
        PlayerUiPresence.enter()
        // Without clamping, the count would be -1 here and one enter would leave
        // it at 0 — reporting "no player UI" forever afterwards.
        assertTrue(PlayerUiPresence.active)
        PlayerUiPresence.exit()
        assertFalse(PlayerUiPresence.active)
    }
}

/** Replaying a finished video must be able to auto-advance again. */
class AutoAdvanceReplayTest {

    @Test
    fun `claim is released when the same video restarts`() {
        AutoAdvance.reset()
        val id = "https://youtu.be/aaaaaaaaaaa"
        assertTrue(AutoAdvance.claim(id))
        assertFalse("second claim on the same video must fail", AutoAdvance.claim(id))

        // Seeking back to zero fires no media-item transition, so reset() never
        // ran and the claim stood — the second ending advanced nowhere.
        AutoAdvance.releaseIfClaimed(id)
        assertTrue("a restarted video may claim again", AutoAdvance.claim(id))
        AutoAdvance.reset()
    }

    @Test
    fun `releasing a different video leaves the claim alone`() {
        AutoAdvance.reset()
        assertTrue(AutoAdvance.claim("video-a"))
        AutoAdvance.releaseIfClaimed("video-b")
        assertFalse(AutoAdvance.claim("video-a"))
        AutoAdvance.reset()
    }
}

/** The diagnostics buffer must stay bounded and must honour incognito. */
class PlaybackLogTest {

    @Test
    fun `ring buffer never exceeds capacity`() {
        PlaybackLog.clear()
        repeat(PlaybackLog.CAPACITY + 50) { PlaybackLog.info("test", "event $it") }
        assertEquals(PlaybackLog.CAPACITY, PlaybackLog.snapshot().size)
        // Oldest entries are the ones dropped.
        assertTrue(PlaybackLog.snapshot().first().message.endsWith("50"))
        PlaybackLog.clear()
    }

    @Test
    fun `video reference is a bare id, and nothing at all while incognito`() {
        PlaybackLog.setRedactUrls(false)
        assertEquals("dQw4w9WgXcQ", PlaybackLog.ref("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", PlaybackLog.ref("https://youtu.be/dQw4w9WgXcQ"))

        PlaybackLog.setRedactUrls(true)
        assertEquals("(hidden)", PlaybackLog.ref("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        PlaybackLog.setRedactUrls(false)
    }

    @Test
    fun `empty log reads as a sentence, not a blank box`() {
        PlaybackLog.clear()
        assertTrue(PlaybackLog.asText().contains("No playback events"))
    }
}
