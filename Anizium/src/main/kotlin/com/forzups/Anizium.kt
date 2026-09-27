package com.forzups

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.databind.JsonNode
import java.net.URLEncoder
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

    // Dinamik cf-control başlığı üreten yardımcı fonksiyon (Çalışan eklentideki mantık)
    private fun generateCfControl(): String {
        val chars = "0123456789abcdef"
        return (1..48).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun getApiHeaders(refererUrl: String? = null): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Content-Type" to "application/json",
            "Origin" to siteUrl,
            "Referer" to (refererUrl ?: "$siteUrl/"),
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
        return headers
    }

    private fun text(node: JsonNode?, vararg keys: String): String? {
        if (node == null) return null
        for (key in keys) {
            val value = node.get(key)
            if (value != null && !value.isNull) {
                val result = value.asText()
                if (result.isNotBlank() && result != "null") {
                    return result
                }
            }
        }
        return null
    }

    private fun int(node: JsonNode?, vararg keys: String): Int? {
        if (node == null) return null
        for (key in keys) {
            val value = node.get(key)
            if (value != null && !value.isNull) {
                if (value.isInt || value.isLong || value.isNumber) {
                    return value.asInt()
                }
                value.asText().toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun array(node: JsonNode?, vararg keys: String): JsonNode? {
        if (node == null) return null
        for (key in keys) {
            val value = node.get(key)
            if (value != null && value.isArray) {
                return value
            }
        }
        return null
    }

    private fun unwrap(node: JsonNode?): JsonNode? {
        if (node == null) return null
        return if (node.has("data") && !node.get("data").isNull) {
            node.get("data")
        } else {
            node
        }
    }

    private fun parseAnimeList(list: JsonNode?): List<SearchResponse> {
        if (list == null || !list.isArray) return emptyList()

        return list.mapNotNull { anime ->
            val animeName = text(anime, "name_tr", "name", "title") ?: return@mapNotNull null
            val id = text(anime, "ID", "id", "series_id", "slug") ?: return@mapNotNull null

            newAnimeSearchResponse(animeName, id, TvType.Anime) {
                posterUrl = fixUrlNull(text(anime, "poster", "poster_url", "mobile_poster_link"))
            }
        }
    }

    private fun extractPageList(node: JsonNode?): JsonNode? {
        if (node == null) return null
        if (node.isArray) return node

        val page = node.get("page")
        if (page != null) {
            if (page.isArray) return page
            array(page, "data", "items", "episodes")?.let { return it }
        }

        return array(node, "data", "items", "results")
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
                val seasonNumber = int(seasonNode, "number", "season_number", "season") ?: defaultSeason
                val seasonEpisodes = array(seasonNode, "episodes", "series", "data")
                seasonEpisodes?.forEach { episode ->
                    addSingleEpisode(episode, episodes, animeId, seasonNumber)
                }
            }
            return
        }

        val directEpisodes = array(container, "episodes", "series", "data")
        directEpisodes?.forEach { episode ->
            addSingleEpisode(episode, episodes, animeId, defaultSeason)
        }
    }

    private fun addSingleEpisode(
        episodeNode: JsonNode?,
        episodes: MutableList<Episode>,
        animeId: String,
        defaultSeason: Int
    ) {
        if (episodeNode == null) return

        val id = text(episodeNode, "id", "ID", "episode_id") ?: return
        val episodeNumber = int(episodeNode, "number", "episode_number", "episode") ?: 1
        val seasonNumber = int(episodeNode, "season", "season_number") ?: defaultSeason
        val episodeName = text(episodeNode, "name", "title") ?: "Bölüm $episodeNumber"

        val episodePoster = fixUrlNull(
            text(
                episodeNode,
                "poster", "poster_url", "thumbnail", "thumbnail_url",
                "image", "image_url", "thumb", "thumb_url", "still",
                "still_url", "mobile_poster_link", "episode_poster",
                "episode_poster_url", "cover", "cover_url"
            )
        )

        val data = "$animeId|$seasonNumber|$episodeNumber|$id"

        episodes.add(
            newEpisode(data) {
                name = episodeName
                season = seasonNumber
                episode = episodeNumber
                posterUrl = episodePoster
            }
        )
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()

        try {
            val latestText = app.get("$apiUrl/page/last-added-episodes?page=1", headers = getApiHeaders()).text
            val latestNode = mapper.readTree(latestText)
            val latestList = extractPageList(latestNode)
            val latestItems = parseAnimeList(latestList)

            if (latestItems.isNotEmpty()) {
                homePageList.add(HomePageList("Son Eklenen Bölümler", latestItems))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val homeText = app.get("$apiUrl/page/home", headers = getApiHeaders()).text
            val home = mapper.readTree(homeText)
            val homeData = unwrap(home) ?: home

            val top = parseAnimeList(homeData.get("settlement_top"))
            if (top.isNotEmpty()) homePageList.add(HomePageList("Öne Çıkan Animeler", top))

            val middle = parseAnimeList(homeData.get("settlement_middle"))
            if (middle.isNotEmpty()) homePageList.add(HomePageList("Haftanın En Çok İzlenenleri", middle))

            val special = homeData.get("special_list")
            if (special != null && special.isArray) {
                special.forEach { category ->
                    val categoryName = text(category, "name") ?: return@forEach
                    val categoryData = parseAnimeList(category.get("data"))
                    if (categoryData.isNotEmpty()) {
                        homePageList.add(HomePageList(categoryName, categoryData))
                    }
                }
            }

            val lower = parseAnimeList(homeData.get("settlement_lower"))
            if (lower.isNotEmpty()) homePageList.add(HomePageList("Önerilen Animeler", lower))

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(list = homePageList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "$apiUrl/page/search?value=$encodedQuery&page=1"
            val response = app.get(searchUrl, headers = getApiHeaders()).text
            val node = mapper.readTree(response)
            val list = extractPageList(node)

            parseAnimeList(list)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val rawAnimeId = url.substringAfterLast("/").substringBefore("?").trim()
        val cleanAnimeId = rawAnimeId.ifBlank { url.trim() }

        var title = "Anime"
        var poster: String? = null
        var banner: String? = null
        var description: String? = null
        val tags = ArrayList<String>()
        val episodes = ArrayList<Episode>()

        try {
            val detailUrl = "$apiUrl/anime/get?id=$cleanAnimeId"
            val response = app.get(detailUrl, headers = getApiHeaders()).text
            val root = mapper.readTree(response)
            val data = unwrap(root) ?: root

            title = text(data, "name_tr", "name", "title") ?: "Anime"
            poster = fixUrlNull(text(data, "poster", "mobile_poster_link", "poster_url"))
            banner = fixUrlNull(text(data, "details_banner", "banner_link", "banner", "background"))
            description = text(data, "overview", "description", "overview_short")

            val genreNode = data.get("genre") ?: data.get("genres")
            if (genreNode != null && genreNode.isArray) {
                genreNode.forEach { genre ->
                    val genreName = text(genre, "name", "title")
                    if (!genreName.isNullOrBlank()) tags.add(genreName)
                }
            }

            addEpisodesFromNode(data, episodes, cleanAnimeId)

            if (episodes.isEmpty()) {
                val seriesId = text(data, "series_id")
                    ?: data.get("series")?.let {
                        if (it.isObject) text(it, "id", "ID") else it.asText()
                    } ?: text(data, "ID", "id") ?: cleanAnimeId

                try {
                    val seriesUrl = "$apiUrl/anime/series?id=$seriesId"
                    val seriesResponse = app.get(seriesUrl, headers = getApiHeaders()).text
                    val seriesRoot = mapper.readTree(seriesResponse)
                    val seriesData = unwrap(seriesRoot) ?: seriesRoot

                    addEpisodesFromNode(seriesData, episodes, cleanAnimeId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newAnimeLoadResponse(title, "$mainUrl/anime/$cleanAnimeId", TvType.Anime) {
            posterUrl = poster
            backgroundPosterUrl = banner
            plot = description
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        try {
            val parts = data.split("|")
            if (parts.size < 4) return false

            val season = parts[1].toIntOrNull() ?: 1
            val episode = parts[2].toIntOrNull() ?: 1
            val episodeId = parts[3]

            val sourceApiUrl = "$apiUrl/anime/source?id=${URLEncoder.encode(episodeId, "UTF-8")}&site=main&plan=standart&season=$season&episode=$episode&server=2"
            val videoReferer = "$siteUrl/watch/$episodeId?season=$season&episode=$episode"

            val response = app.get(sourceApiUrl, headers = getApiHeaders(videoReferer)).text
            val root = mapper.readTree(response)
            val content = unwrap(root) ?: root

            // 1. Altyazıları Çek
            val subtitles = array(content, "subtitles") ?: array(root, "subtitles")
            subtitles?.forEach { subtitle ->
                val subtitleUrl = text(subtitle, "link", "url", "file", "src")
                val lang = text(subtitle, "language", "lang", "name") ?: "Türkçe"
                if (!subtitleUrl.isNullOrBlank()) {
                    subtitleCallback(SubtitleFile(lang, fixUrlNull(subtitleUrl) ?: subtitleUrl))
                }
            }

            // 2. Video Linklerini Çek (M3U8 / MP4)
            val playlistUrl = text(content, "file", "url", "stream", "link")
            if (!playlistUrl.isNullOrBlank()) {
                val streamUrl = fixUrlNull(playlistUrl) ?: playlistUrl

                if (streamUrl.contains(".m3u8")) {
                    offsetCallback(
                        ExtractorLink(
                            name = "Anizium VIP",
                            source = "Anizium HLS",
                            url = streamUrl,
                            referer = videoReferer,
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                    return true
                } else {
                    offsetCallback(
                        ExtractorLink(
                            name = "Anizium MP4",
                            source = "Anizium Direct",
                            url = streamUrl,
                            referer = videoReferer,
                            quality = Qualities.Unknown.value,
                            isM3u8 = false
                        )
                    )
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}
