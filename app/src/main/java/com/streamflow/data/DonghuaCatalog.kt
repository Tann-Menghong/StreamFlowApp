package com.streamflow.data

import com.streamflow.data.model.VideoItem

/**
 * What the Donghua tab is made of.
 *
 * The tab used to be a WebView pointed at a third-party streaming site, which
 * meant it shared nothing with the rest of the app: no VideoItem, so no
 * download, no favourite, no Watch Later, no history, no resume position, no
 * queue, and none of the shared empty/error states. It was a browser that
 * happened to live in the bottom bar.
 *
 * It is now an ordinary content source over YouTube, so every one of those
 * features works for free -- not because they were re-implemented here, but
 * because a Donghua video IS a VideoItem and flows through exactly the same
 * repository, player route and Room tables as one found on Home or in Search.
 *
 * Sections are SEARCH QUERIES, deliberately, not pinned video or channel ids.
 * A pinned id rots silently: the video is deleted or the channel renames, the
 * section renders empty, and nothing fails. A query keeps returning whatever
 * YouTube currently has, which is the same mechanism Home and Search already
 * depend on.
 *
 * Pure data and list arithmetic, no Android types, so the rules below -- drop
 * empty sections, never show the same video twice, keep the order stable --
 * are provable in JVM unit tests rather than by looking at a phone.
 */
object DonghuaCatalog {

    /** One row of the tab, and the search that fills it. */
    data class Source(
        val id: String,
        val title: String,
        val query: String,
    )

    /** A resolved row: a source plus the videos that came back for it. */
    data class Section(
        val source: Source,
        val videos: List<VideoItem>,
    )

    /**
     * The rows, in the order they appear.
     *
     * "donghua" is the term the audience uses for Chinese animation and is what
     * the uploads are actually titled and tagged with, so it carries the search
     * far better than "chinese anime" does.
     */
    val sources: List<Source> = listOf(
        Source("latest", "Latest episodes", "donghua latest episode english sub"),
        Source("popular", "Popular this season", "donghua full episode"),
        Source("movies", "Donghua movies", "donghua movie english sub full"),
        Source("cultivation", "Cultivation & xianxia", "xianxia donghua english sub"),
        Source("classics", "Long-running series", "donghua series english sub episode 1"),
    )

    /** Genre chips. The selected one narrows every row's query. */
    data class Genre(val id: String, val label: String, val term: String)

    val genres: List<Genre> = listOf(
        Genre("all", "All", ""),
        Genre("action", "Action", "action"),
        Genre("fantasy", "Fantasy", "fantasy"),
        Genre("cultivation", "Cultivation", "cultivation"),
        Genre("romance", "Romance", "romance"),
        Genre("comedy", "Comedy", "comedy"),
    )

    /**
     * The query to actually run for [source] under [genre].
     *
     * The genre term is appended rather than replacing the source's own terms,
     * so "Donghua movies" filtered to Action stays a search for movies.
     */
    fun queryFor(source: Source, genre: Genre): String =
        if (genre.term.isBlank()) source.query
        else "${source.query} ${genre.term}"

    /**
     * The search that finds SERIES rather than single episodes.
     *
     * Separate from the episode queries because the thing being looked for is
     * different: a playlist collecting a whole show, not one upload. "full
     * series" and "all episodes" are how uploaders actually name those.
     */
    fun seriesQueryFor(genre: Genre): String =
        if (genre.term.isBlank()) "donghua full series all episodes english sub"
        else "donghua ${genre.term} full series all episodes english sub"

    /**
     * Turn loaded results into the rows to render.
     *
     * Three rules, all of which the WebView tab had no way to express:
     *
     *  - A row with nothing in it is not shown. An empty shelf under a
     *    confident heading reads as the app being broken, and every row here
     *    can legitimately come back empty when YouTube has nothing for that
     *    query today.
     *  - A video appears once. The queries overlap heavily by design -- a
     *    popular new episode matches "latest" and "popular" both -- and the
     *    same card twice on one screen looks like a bug.
     *  - Order follows [sources], never the order results happened to arrive
     *    in. The rows load in parallel, so anything else would shuffle the tab
     *    differently on every refresh.
     *
     * @param loaded results by source id; a missing id is treated as empty
     * @param minPerSection rows thinner than this are dropped rather than shown
     *        as a near-empty shelf
     */
    fun assemble(
        loaded: Map<String, List<VideoItem>>,
        minPerSection: Int = 2,
    ): List<Section> {
        val seen = HashSet<String>()
        val out = ArrayList<Section>(sources.size)
        for (source in sources) {
            val fresh = (loaded[source.id] ?: emptyList())
                .filter { it.url.isNotBlank() && seen.add(it.url) }
            if (fresh.size >= minPerSection) out.add(Section(source, fresh))
        }
        return out
    }

    /**
     * Half-watched donghua, for the Continue Watching row.
     *
     * There is no "this video is donghua" flag anywhere -- history is one flat
     * table and adding a column would mean a Room migration to record something
     * we can already infer. So an entry counts as donghua when it is a video
     * currently on this tab, or when its uploader is one of the channels the
     * tab is showing. The second rule is what makes the row useful a week
     * later, when the episode has dropped out of "latest" but the channel is
     * still there.
     *
     * The cost of a wrong guess is one unrelated card in a row, so the bar is
     * deliberately "same channel", not "similar title" -- matching on words in
     * a title would put any video with "action" in it here.
     *
     * @param progress url -> fraction watched
     * @param onScreen every video currently loaded into the tab
     */
    fun continueWatching(
        watched: List<VideoItem>,
        progress: Map<String, Float>,
        onScreen: List<VideoItem>,
        limit: Int = 12,
    ): List<VideoItem> {
        if (onScreen.isEmpty()) return emptyList()
        val urls = onScreen.mapTo(HashSet()) { it.url }
        val channels = onScreen.mapNotNullTo(HashSet()) { it.uploaderName.takeIf(String::isNotBlank) }
        return watched
            .filter { v ->
                val p = progress[v.url] ?: 0f
                // Started but not finished. Below 2% is a video that was opened
                // and closed; past 92% is one the user is done with, and both
                // are noise in a "pick up where you left off" row.
                p in 0.02f..0.92f &&
                    (v.url in urls || v.uploaderName in channels)
            }
            .take(limit)
    }
}
