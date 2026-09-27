package com.forzups

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.random.Random

class Anizium : MainAPI() {

override var mainUrl = "https://anizium.co"
private val apiUrl = "https://api.anizium.co"
private val siteUrl = "https://x.anizium.co"

override var name = "Anizium"
override val hasMainPage = true
override val hasQuickSearch = true
override var lang = "tr"

override val supportedTypes = setOf(
    TvType.Anime,
    TvType.AnimeMovie
)

private fun generateCfControl(): String {
    val chars = "0123456789abcdef"
    return (1..48).map { chars[Random.nextInt(chars.length)] }.joinToString("")
}

private fun getApiHeaders(referer: String? = null): Map<String, String> {
    return mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Content-Type" to "application/json",
        "Origin" to siteUrl,
        "Referer" to (referer ?: "$siteUrl/"),
        "cf-control" to generateCfControl(),
        "site" to "main",
        "device" to "browser",
        "language" to "tr",
        "user-profile" to "null",
        "user-session" to "01570d01545f55510102040f525d390e00094f0556514e2d1d5201580100071e030350115a53534c05550f535143455c085c54101215",
        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Chromium\";v=\"120\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\""
    )
}

private fun text(node: JsonNode?, vararg keys: String): String? {
    if (node == null) return null

    for (key in keys) {
        val value = node.get(key) ?: continue
        if (!value.isNull) {
            val result = value.asText()
            if (result.isNotBlank() && result != "null") return result
        }
    }

    return null
}

private fun int(node: JsonNode?, vararg keys: String): Int? {
    if (node == null) return null

    for (key in keys) {
        val value = node.get(key) ?: continue

        if (value.isNumber) {
            return value.asInt()
        }

        value.asText().toIntOrNull()?.let {
            return it
        }
    }

    return null
}

private fun array(node: JsonNode?, vararg keys: String): JsonNode? {
    if (node == null) return null

    for (key in keys) {
        val value = node.get(key)
        if (value != null && value.isArray) return value
    }

    return null
}

private fun unwrap(node: JsonNode?): JsonNode? {
    if (node == null) return null

    val data = node.get("data")
    return if (data != null && !data.isNull) data else node
}

private fun parseAnimeList(list: JsonNode?): List<SearchResponse> {
    if (list == null || !list.isArray) return emptyList()

    return list.mapNotNull { anime ->
        val title = text(anime, "name_tr", "name", "title") ?: return@mapNotNull null
        val id = text(anime, "ID", "id", "series_id", "slug") ?: return@mapNotNull null

        newAnimeSearchResponse(title, id, TvType.Anime) {
            posterUrl = fixUrlNull(
                text(
                    anime,
                    "poster",
                    "poster_url",
                    "mobile_poster_link"
                )
            )
        }
    }
}

private fun extractPageList(node: JsonNode?): JsonNode? {
    if (node == null) return null

    if (node.isArray) return node

    val page = node.get("page")

    if (page != null) {
        if (page.isArray) return page

        array(
            page,
            "data",
            "items",
            "episodes"
        )?.let {
            return it
        }
    }

    return array(
        node,
        "data",
        "items",
        "results"
    )
}

private fun addEpisodesFromNode(
    container: JsonNode?,
    episodes: MutableList<Episode>,
    animeId: String,
    defaultSeason: Int = 1
) {
    if (container == null) return

    val seasons = array(container, "seasons")

    if (seasons != null) {
        seasons.forEach { seasonNode ->
            val seasonNumber =
                int(
                    seasonNode,
                    "number",
                    "season_number",
                    "season"
                ) ?: defaultSeason

            val seasonEpisodes =
                array(
                    seasonNode,
                    "episodes",
                    "series",
                    "data"
                )

            seasonEpisodes?.forEach {
                addSingleEpisode(
                    it,
                    episodes,
                    animeId,
                    seasonNumber
                )
            }
        }

        return
    }

    val directEpisodes =
        array(
            container,
            "episodes",
            "series",
            "data"
        )

    directEpisodes?.forEach {
        addSingleEpisode(
            it,
            episodes,
            animeId,
            defaultSeason
        )
    }
}

private fun addSingleEpisode(
    episodeNode: JsonNode?,
    episodes: MutableList<Episode>,
    animeId: String,
    defaultSeason: Int
) {
    if (episodeNode == null) return

    val id =
        text(
            episodeNode,
            "id",
            "ID",
            "episode_id"
        ) ?: return

    val episodeNumber =
        int(
            episodeNode,
            "number",
            "episode_number",
            "episode"
        ) ?: 1

    val seasonNumber =
        int(
            episodeNode,
            "season",
            "season_number"
        ) ?: defaultSeason

    val episodeName =
        text(
            episodeNode,
            "name",
            "title"
        ) ?: "Bölüm $episodeNumber"

    val poster =
        fixUrlNull(
            text(
                episodeNode,
                "poster",
                "poster_url",
                "thumbnail",
                "thumbnail_url",
                "image",
                "image_url",
                "thumb",
                "thumb_url",
                "still",
                "still_url",
                "mobile_poster_link",
                "episode_poster",
                "episode_poster_url",
                "cover",
                "cover_url"
            )
        )

    episodes.add(
        newEpisode(
            "$animeId|$seasonNumber|$episodeNumber|$id"
        ) {
            name = episodeName
            season = seasonNumber
            episode = episodeNumber
            posterUrl = poster
        }
    )
}

override suspend fun getMainPage(
    page: Int,
    request: MainPageRequest
): HomePageResponse {
    val homePageList = ArrayList<HomePageList>()

    try {
        val latestUrl =
            "$apiUrl/page/last-added-episodes?page=$page"

        val latestResponse =
            app.get(
                latestUrl,
                headers = getApiHeaders()
            ).text

        val latestRoot =
            mapper.readTree(latestResponse)

        val latestList =
            extractPageList(latestRoot)

        val latestItems =
            parseAnimeList(latestList)

        if (latestItems.isNotEmpty()) {
            homePageList.add(
                HomePageList(
                    "Son Eklenen Bölümler",
                    latestItems
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        val homeResponse =
            app.get(
                "$apiUrl/page/home",
                headers = getApiHeaders()
            ).text

        val homeRoot =
            mapper.readTree(homeResponse)

        val homeData =
            unwrap(homeRoot) ?: homeRoot

        val top =
            parseAnimeList(
                homeData.get("settlement_top")
            )

        if (top.isNotEmpty()) {
            homePageList.add(
                HomePageList(
                    "Öne Çıkan Animeler",
                    top
                )
            )
        }

        val middle =
            parseAnimeList(
                homeData.get("settlement_middle")
            )

        if (middle.isNotEmpty()) {
            homePageList.add(
                HomePageList(
                    "Haftanın En Çok İzlenenleri",
                    middle
                )
            )
        }

        val special =
            homeData.get("special_list")

        if (special != null && special.isArray) {
            special.forEach { category ->
                val categoryName =
                    text(category, "name")
                        ?: return@forEach

                val categoryItems =
                    parseAnimeList(
                        category.get("data")
                    )

                if (categoryItems.isNotEmpty()) {
                    homePageList.add(
                        HomePageList(
                            categoryName,
                            categoryItems
                        )
                    )
                }
            }
        }

        val lower =
            parseAnimeList(
                homeData.get("settlement_lower")
            )

        if (lower.isNotEmpty()) {
            homePageList.add(
                HomePageList(
                    "Önerilen Animeler",
                    lower
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return newHomePageResponse(
        list = homePageList,
        hasNext = page < 10
    )
}

override suspend fun search(
    query: String
): List<SearchResponse> {
    return try {
        val encoded =
            URLEncoder.encode(
                query.trim(),
                "UTF-8"
            )

        val response =
            app.get(
                "$apiUrl/page/search?value=$encoded&page=1",
                headers = getApiHeaders()
            ).text

        val root =
            mapper.readTree(response)

        parseAnimeList(
            extractPageList(root)
        )
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

override suspend fun load(
    url: String
): LoadResponse {
    val animeId =
        url.substringAfterLast("/")
            .substringBefore("?")
            .trim()

    var title = "Anime"
    var poster: String? = null
    var banner: String? = null
    var description: String? = null

    val tags = ArrayList<String>()
    val episodes = ArrayList<Episode>()

    try {
        val response =
            app.get(
                "$apiUrl/anime/get?id=$animeId",
                headers = getApiHeaders()
            ).text

        val root =
            mapper.readTree(response)

        val data =
            unwrap(root) ?: root

        title =
            text(
                data,
                "name_tr",
                "name",
                "title"
            ) ?: "Anime"

        poster =
            fixUrlNull(
                text(
                    data,
                    "poster",
                    "mobile_poster_link",
                    "poster_url"
                )
            )

        banner =
            fixUrlNull(
                text(
                    data,
                    "details_banner",
                    "banner_link",
                    "banner",
                    "background"
                )
            )

        description =
            text(
                data,
                "overview",
                "description",
                "overview_short"
            )

        val genres =
            data.get("genre")
                ?: data.get("genres")

        if (genres != null && genres.isArray) {
            genres.forEach {
                text(it, "name", "title")
                    ?.takeIf { name -> name.isNotBlank() }
                    ?.let(tags::add)
            }
        }

        addEpisodesFromNode(
            data,
            episodes,
            animeId
        )

        if (episodes.isEmpty()) {
            val seriesId =
                text(data, "series_id")
                    ?: data.get("series")?.let {
                        if (it.isObject) {
                            text(it, "id", "ID")
                        } else {
                            it.asText()
                        }
                    }
                    ?: text(data, "ID", "id")
                    ?: animeId

            val seriesResponse =
                app.get(
                    "$apiUrl/anime/series?id=$seriesId",
                    headers = getApiHeaders()
                ).text

            val seriesRoot =
                mapper.readTree(seriesResponse)

            val seriesData =
                unwrap(seriesRoot) ?: seriesRoot

            addEpisodesFromNode(
                seriesData,
                episodes,
                animeId
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return newAnimeLoadResponse(
        title,
        "$mainUrl/anime/$animeId",
        TvType.Anime
    ) {
        posterUrl = poster
        backgroundPosterUrl = banner
        plot = description
        this.tags = tags

        addEpisodes(
            DubStatus.Subbed,
            episodes
        )
    }
}

private data class VideoItem(
    val url: String,
    val quality: Int,
    val isM3u8: Boolean,
    val name: String,
    val referer: String
)

private fun qualityFromUrl(url: String): Int {
    val match =
        Regex(
            """(\d{3,4})p"""
        ).find(
            url.lowercase()
        )

    return match?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: Qualities.Unknown.value
}

private fun collectVideoUrls(
    node: JsonNode?,
    result: MutableList<VideoItem>,
    referer: String,
    visited: MutableSet<String> = HashSet()
) {
    if (node == null) return

    if (node.isTextual) {
        val value = node.asText().trim()

        if (
            value.startsWith("http://") ||
            value.startsWith("https://")
        ) {
            if (
                (
                    value.contains(".m3u8", true) ||
                    value.contains(".mp4", true)
                ) &&
                visited.add(value)
            ) {
                val m3u8 =
                    value.contains(
                        ".m3u8",
                        true
                    )

                result.add(
                    VideoItem(
                        url = value,
                        quality = qualityFromUrl(value),
                        isM3u8 = m3u8,
                        name = if (m3u8) {
                            "Anizium HLS"
                        } else {
                            "Anizium MP4"
                        },
                        referer = referer
                    )
                )
            }
        }

        return
    }

    if (node.isArray) {
        node.forEach {
            collectVideoUrls(
                it,
                result,
                referer,
                visited
            )
        }
        return
    }

    if (node.isObject) {
        node.fields().forEach { (_, value) ->
            collectVideoUrls(
                value,
                result,
                referer,
                visited
            )
        }
    }
}

private fun collectSubtitles(
    node: JsonNode?,
    subtitleCallback: (SubtitleFile) -> Unit,
    visited: MutableSet<String> = HashSet()
) {
    if (node == null) return

    if (node.isArray) {
        node.forEach {
            collectSubtitles(
                it,
                subtitleCallback,
                visited
            )
        }
        return
    }

    if (!node.isObject) return

    val url =
        text(
            node,
            "link",
            "url",
            "file",
            "src"
        )

    if (!url.isNullOrBlank()) {
        val fixed =
            fixUrlNull(url) ?: url

        if (visited.add(fixed)) {
            val language =
                text(
                    node,
                    "language",
                    "lang",
                    "name"
                ) ?: "Türkçe"

            subtitleCallback(
                SubtitleFile(
                    language,
                    fixed
                )
            )
        }
    }

    node.fields().forEach { (key, value) ->
        if (
            key.equals(
                "subtitles",
                true
            )
        ) {
            collectSubtitles(
                value,
                subtitleCallback,
                visited
            )
        }
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    offsetCallback: (ExtractorLink) -> Unit
): Boolean {
    if (data.isBlank()) return false

    val parts =
        data.split("|")

    if (parts.size < 4) return false

    val season =
        parts[1].toIntOrNull() ?: 1

    val episode =
        parts[2].toIntOrNull() ?: 1

    val episodeId =
        parts[3].trim()

    val videoReferer =
        "$siteUrl/watch/$episodeId?season=$season&episode=$episode"

    val allVideos =
        ArrayList<VideoItem>()

    val allSubtitles =
        HashSet<String>()

    for (server in 1..5) {
        try {
            val sourceUrl =
                "$apiUrl/anime/source?id=${
                    URLEncoder.encode(
                        episodeId,
                        "UTF-8"
                    )
                }&site=main&plan=standart&season=$season&episode=$episode&server=$server"

            val response =
                app.get(
                    sourceUrl,
                    headers = getApiHeaders(videoReferer)
                ).text

            val root =
                mapper.readTree(response)

            val content =
                unwrap(root) ?: root

            collectSubtitles(
                content,
                { subtitle ->
                    val key =
                        "${subtitle.lang}|${subtitle.url}"

                    if (allSubtitles.add(key)) {
                        subtitleCallback(subtitle)
                    }
                }
            )

            collectVideoUrls(
                content,
                allVideos,
                videoReferer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (allVideos.isEmpty()) {
        return false
    }

    val uniqueVideos =
        allVideos
            .distinctBy { it.url }
            .sortedByDescending {
                it.quality
            }

    val mp4 =
        uniqueVideos.filter {
            !it.isM3u8
        }

    val hls =
        uniqueVideos.filter {
            it.isM3u8
        }

    mp4.forEach { video ->
        try {
            val quality =
                if (
                    video.quality ==
                    Qualities.Unknown.value
                ) {
                    Qualities.Unknown.value
                } else {
                    video.quality
                }

            offsetCallback(
                newExtractorLink(
                    source = "Anizium MP4",
                    name = "Anizium MP4 ${quality}p",
                    url = video.url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    referer = video.referer
                    this.quality = quality
                    headers = mapOf(
                        "User-Agent" to getApiHeaders(
                            videoReferer
                        )["User-Agent
