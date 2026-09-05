package com.streamflow.data

import com.streamflow.data.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonghuaCatalogTest {

    private fun v(url: String, channel: String = "Chan") = VideoItem(
        url = url, title = "t-$url", thumbnailUrl = "", uploaderName = channel,
        viewCount = 0L, duration = 600L
    )

    private val latest = DonghuaCatalog.sources[0].id
    private val popular = DonghuaCatalog.sources[1].id

    // §16 of the brief, and the rule the WebView tab had no way to express: an
    // empty shelf under a confident heading reads as the app being broken.
    @Test fun `a section with nothing in it is not shown`() {
        val out = DonghuaCatalog.assemble(mapOf(latest to emptyList()))
        assertTrue(out.isEmpty())
    }

    @Test fun `a section thinner than the minimum is dropped, not shown half-empty`() {
        val one = DonghuaCatalog.assemble(mapOf(latest to listOf(v("a"))), minPerSection = 2)
        assertTrue(one.isEmpty())
        val two = DonghuaCatalog.assemble(mapOf(latest to listOf(v("a"), v("b"))), minPerSection = 2)
        assertEquals(1, two.size)
    }

    @Test fun `a source that never loaded is treated as empty, not as an error`() {
        val out = DonghuaCatalog.assemble(mapOf(popular to listOf(v("a"), v("b"))))
        assertEquals(1, out.size)
        assertEquals(popular, out.first().source.id)
    }

    // The queries overlap by design -- a popular new episode matches "latest"
    // and "popular" both -- so without this the same card renders twice.
    @Test fun `a video appears in only one section`() {
        val out = DonghuaCatalog.assemble(
            mapOf(
                latest to listOf(v("a"), v("b"), v("c")),
                popular to listOf(v("b"), v("c"), v("d"), v("e")),
            )
        )
        val all = out.flatMap { it.videos }.map { it.url }
        assertEquals(all.size, all.toSet().size)
        assertEquals(listOf("a", "b", "c"), out[0].videos.map { it.url })
        assertEquals(listOf("d", "e"), out[1].videos.map { it.url })
    }

    // Deduplication must not turn a full section into a stub: if everything in
    // a later row was already shown above, that row goes away entirely.
    @Test fun `a section left too thin by deduplication is dropped`() {
        val out = DonghuaCatalog.assemble(
            mapOf(
                latest to listOf(v("a"), v("b"), v("c")),
                popular to listOf(v("a"), v("b")),
            )
        )
        assertEquals(1, out.size)
        assertEquals(latest, out.first().source.id)
    }

    // Rows load in parallel, so anything order-dependent would shuffle the tab
    // on every refresh.
    @Test fun `order follows the declared sources, not load order`() {
        val loaded = LinkedHashMap<String, List<VideoItem>>()
        // deliberately inserted last-source-first
        for (s in DonghuaCatalog.sources.reversed()) {
            loaded[s.id] = listOf(v("${s.id}1"), v("${s.id}2"))
        }
        val out = DonghuaCatalog.assemble(loaded)
        assertEquals(DonghuaCatalog.sources.map { it.id }, out.map { it.source.id })
    }

    @Test fun `blank urls cannot occupy a slot`() {
        val out = DonghuaCatalog.assemble(
            mapOf(latest to listOf(v(""), v(""), v("a"), v("b")))
        )
        assertEquals(listOf("a", "b"), out.single().videos.map { it.url })
    }

    // ── queries ─────────────────────────────────────────────────────────────

    @Test fun `the all genre leaves a query untouched`() {
        val all = DonghuaCatalog.genres.first { it.id == "all" }
        for (s in DonghuaCatalog.sources) {
            assertEquals(s.query, DonghuaCatalog.queryFor(s, all))
        }
    }

    @Test fun `a genre narrows a query without replacing it`() {
        val action = DonghuaCatalog.genres.first { it.id == "action" }
        val movies = DonghuaCatalog.sources.first { it.id == "movies" }
        val q = DonghuaCatalog.queryFor(movies, action)
        assertTrue("lost the source terms: $q", q.startsWith(movies.query))
        assertTrue("lost the genre: $q", q.contains("action"))
    }

    @Test fun `sources and genres have unique ids and no blank queries`() {
        assertEquals(
            DonghuaCatalog.sources.size,
            DonghuaCatalog.sources.map { it.id }.toSet().size
        )
        assertEquals(
            DonghuaCatalog.genres.size,
            DonghuaCatalog.genres.map { it.id }.toSet().size
        )
        for (s in DonghuaCatalog.sources) {
            assertTrue("blank query for ${s.id}", s.query.isNotBlank())
            assertTrue("blank title for ${s.id}", s.title.isNotBlank())
        }
        assertEquals(1, DonghuaCatalog.genres.count { it.term.isBlank() })
    }

    // ── continue watching ───────────────────────────────────────────────────

    @Test fun `a part-watched video on this tab is offered to resume`() {
        val a = v("a")
        val out = DonghuaCatalog.continueWatching(
            watched = listOf(a), progress = mapOf("a" to 0.4f), onScreen = listOf(a)
        )
        assertEquals(listOf("a"), out.map { it.url })
    }

    // The rule that makes the row still work a week later, once the episode has
    // dropped out of "latest" but its channel is still on the tab.
    @Test fun `another episode from a channel on this tab counts`() {
        val onScreen = listOf(v("ep5", channel = "Donghua Hub"))
        val watched = listOf(v("ep1", channel = "Donghua Hub"))
        val out = DonghuaCatalog.continueWatching(
            watched, mapOf("ep1" to 0.3f), onScreen
        )
        assertEquals(listOf("ep1"), out.map { it.url })
    }

    // The whole point of scoping by channel: an unrelated video the user
    // half-watched must not surface in a donghua row.
    @Test fun `an unrelated half-watched video is not donghua`() {
        val out = DonghuaCatalog.continueWatching(
            watched = listOf(v("cooking", channel = "Recipes Daily")),
            progress = mapOf("cooking" to 0.5f),
            onScreen = listOf(v("ep5", channel = "Donghua Hub"))
        )
        assertTrue(out.isEmpty())
    }

    @Test fun `barely-started and nearly-finished videos are both excluded`() {
        val a = v("a"); val b = v("b"); val c = v("c")
        val out = DonghuaCatalog.continueWatching(
            watched = listOf(a, b, c),
            progress = mapOf("a" to 0.001f, "b" to 0.5f, "c" to 0.99f),
            onScreen = listOf(a, b, c)
        )
        assertEquals(listOf("b"), out.map { it.url })
    }

    @Test fun `nothing loaded means nothing to resume`() {
        val out = DonghuaCatalog.continueWatching(
            watched = listOf(v("a")), progress = mapOf("a" to 0.5f), onScreen = emptyList()
        )
        assertTrue(out.isEmpty())
    }

    @Test fun `the resume row is capped`() {
        val items = (1..30).map { v("v$it", channel = "Donghua Hub") }
        val out = DonghuaCatalog.continueWatching(
            watched = items,
            progress = items.associate { it.url to 0.5f },
            onScreen = listOf(v("ep", channel = "Donghua Hub")),
            limit = 5
        )
        assertEquals(5, out.size)
    }
}
