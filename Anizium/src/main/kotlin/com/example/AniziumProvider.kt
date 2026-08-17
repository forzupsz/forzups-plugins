package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.databind.JsonNode
import java.net.URLEncoder

class AniziumProvider : MainAPI() {

    override var mainUrl = "https://anizium.co"
    private val apiUrl = "https://api.anizium.co"
    private val sourceUrl = "https://x.anizium.co"

    override var name = "Anizium"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    /*
     * Eski çalışan plugin'in header yapısına mümkün olduğunca yakın tutuldu.
     * API değişirse aşağıdaki header'lar tek noktadan değiştirilebilir.
     */
    private val apiHeaders = mapOf(
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",

        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Content-Type" to "application/json",
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/",
        "Cf-Control" to "134e1a5e0909175c55080906594e0d040b4440075851560f",
        "Site" to "main",
        "Device" to "browser",
        "Language" to "tr",
        "User-Profile" to "null",
        "User-Session" to "null",
        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Chromium\";v=\"139\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\""
    )

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

                value.asText().toIntOrNull()?.let {
                    return it
                }
            }
        }

        return null
    }

    private fun double(node: JsonNode?, vararg keys: String): Double? {
        if (node == null) return null

        for (key in keys) {
            val value = node.get(key)

            if (value != null && !value.isNull) {
                if (value.isNumber) {
                    return value.asDouble()
                }

                value.asText().toDoubleOrNull()?.let {
                    return it
                }
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
        if (list == null || !list.isArray) {
            return emptyList()
        }

        return list.mapNotNull { anime ->
            val name = text(
                anime,
                "name_tr",
                "name",
                "title"
            ) ?: return@mapNotNull null

            val id = text(
                anime,
                "ID",
                "id",
                "series_id",
                "slug"
            ) ?: return@mapNotNull null

            newAnimeSearchResponse(
                name,
                id,
                TvType.Anime
            ) {
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

    private fun findList(node: JsonNode?): JsonNode? {
        if (node == null) return null

        if (node.isArray) return node

        return array(
            node,
            "data",
            "page",
            "episodes",
            "series"
        )
    }

    private fun addEpisodesFromNode(
        container: JsonNode?,
        episodes: MutableList<Episode>,
        defaultSeason: Int = 1
    ) {
        if (container == null) return

        /*
         * seasons -> [
         *   {
         *     number: 1,
         *     episodes: [...]
         *   }
         * ]
         */
        val seasons = array(
            container,
            "seasons"
        )

        if (seasons != null) {
            seasons.forEach { seasonNode ->

                val seasonNumber =
                    int(
                        seasonNode,
                        "number",
                        "season_number",
                        "season"
                    ) ?: defaultSeason

                val seasonEpisodes = array(
                    seasonNode,
                    "episodes",
                    "series",
                    "data"
                )

                seasonEpisodes?.forEach { episode ->
                    addSingleEpisode(
                        episode,
                        episodes,
                        seasonNumber
                    )
                }
            }

            return
        }

        /*
         * Doğrudan episodes / series / data array'i.
         */
        val directEpisodes = array(
            container,
            "episodes",
            "series",
            "data"
        )

        directEpisodes?.forEach { episode ->
            addSingleEpisode(
                episode,
                episodes,
                defaultSeason
            )
        }
    }

    private fun addSingleEpisode(
        episodeNode: JsonNode?,
        episodes: MutableList<Episode>,
        defaultSeason: Int
    ) {
        if (episodeNode == null) return

        val id = text(
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

        /*
         * loadLinks() eski source endpoint'inin istediği
         * episode/season bilgisine de erişebilsin.
         *
         * Format:
         * episodeId|season|episode
         */
        val data = "$id|$seasonNumber|$episodeNumber"

        episodes.add(
            newEpisode(data) {
                name = episodeName
                season = seasonNumber
                episode = episodeNumber
            }
        )
    }

    private fun extractPageList(node: JsonNode?): JsonNode? {
        if (node == null) return null

        if (node.isArray) {
            return node
        }

        val page = node.get("page")

        if (page != null) {
            if (page.isArray) {
                return page
            }

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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val homePageList = ArrayList<HomePageList>()

        try {
            /*
             * Son eklenen bölümler
             */
            val latestText = app.get(
                "$apiUrl/page/last-added-episodes?page=1",
                headers = apiHeaders
            ).text

            val latestNode = mapper.readTree(latestText)

            val latestList = extractPageList(
                latestNode
            )

            val latestItems = parseAnimeList(
                latestList
            )

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
            /*
             * Ana sayfa
             */
            val homeText = app.get(
                "$apiUrl/page/home",
                headers = apiHeaders
            ).text

            val home = mapper.readTree(homeText)

            val homeData =
                if (home.has("data") && !home.get("data").isNull)
                    home.get("data")
                else
                    home

            val top = parseAnimeList(
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

            val middle = parseAnimeList(
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

            val special = homeData.get("special_list")

            if (special != null && special.isArray) {
                special.forEach { category ->

                    val categoryName =
                        text(category, "name")
                            ?: return@forEach

                    val categoryData =
                        parseAnimeList(
                            category.get("data")
                        )

                    if (categoryData.isNotEmpty()) {
                        homePageList.add(
                            HomePageList(
                                categoryName,
                                categoryData
                            )
                        )
                    }
                }
            }

            val lower = parseAnimeList(
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

        /*
         * Eski plugin'de bulunan kataloglar.
         *
         * Bunlardan biri çalışıyorsa ekrana eklenir.
         * Endpoint değişmişse hata diğer bölümleri engellemez.
         */
        val catalogRequests = listOf(
            "Seriler" to "$apiUrl/page/catalog?id=series&type=type&page=$page",
            "Filmler" to "$apiUrl/page/catalog?id=movie&type=type&page=$page",
            "Türkçe Dublaj" to
                "$apiUrl/page/catalog?id=trdub&type=sound_group&page=$page"
        )

        for ((title, url) in catalogRequests) {
            try {
                val response = app.get(
                    url,
                    headers = apiHeaders
                ).text

                val node = mapper.readTree(response)

                val list = extractPageList(node)
                val parsed = parseAnimeList(list)

                if (parsed.isNotEmpty()) {
                    homePageList.add(
                        HomePageList(
                            title,
                            parsed
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return newHomePageResponse(
            list = homePageList,
            hasNext = false
        )
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        return try {

            val encodedQuery =
                URLEncoder.encode(
                    query.trim(),
                    "UTF-8"
                )

            val searchUrl =
                "$apiUrl/page/search?value=$encodedQuery&page=1"

            val response = app.get(
                searchUrl,
                headers = apiHeaders
            ).text

            val node = mapper.readTree(response)

            val list = extractPageList(node)

            parseAnimeList(list)

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun load(
        url: String
    ): LoadResponse {

        val rawAnimeId =
            url.substringAfterLast("/")
                .substringBefore("?")
                .trim()

        val cleanAnimeId =
            rawAnimeId.ifBlank {
                url.trim()
            }

        var title = "Anime"
        var poster: String? = null
        var banner: String? = null
        var description: String? = null

        val tags = ArrayList<String>()
        val episodes = ArrayList<Episode>()

        var year: Int? = null
        var score: Double? = null

        try {

            val detailUrl =
                "$apiUrl/anime/get?id=$cleanAnimeId"

            val response = app.get(
                detailUrl,
                headers = apiHeaders
            ).text

            val root = mapper.readTree(response)

            val data =
                unwrap(root)
                    ?: root

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

            year =
                int(
                    data,
                    "releaseYear",
                    "release_year",
                    "year"
                )

            score =
                double(
                    data,
                    "imdbPoint",
                    "imdb_point",
                    "rating"
                )

            /*
             * Genre
             */
            val genreNode =
                data.get("genre")
                    ?: data.get("genres")

            if (genreNode != null && genreNode.isArray) {
                genreNode.forEach { genre ->

                    val genreName =
                        text(
                            genre,
                            "name",
                            "title"
                        )

                    if (!genreName.isNullOrBlank()) {
                        tags.add(genreName)
                    }
                }
            }

            /*
             * Anime detayının kendi episode listesi.
             */
            addEpisodesFromNode(
                data,
                episodes
            )

            /*
             * Eğer detay endpoint'i episode vermediyse
             * eski plugin'in series endpoint'ini deniyoruz.
             */
            if (episodes.isEmpty()) {

                val seriesId =
                    text(
                        data,
                        "series_id"
                    )
                        ?: data.get("series")
                            ?.let {
                                if (it.isObject)
                                    text(it, "id", "ID")
                                else
                                    it.asText()
                            }
                        ?: text(
                            data,
                            "ID",
                            "id"
                        )
                        ?: cleanAnimeId

                try {

                    val seriesUrl =
                        "$apiUrl/anime/series?id=$seriesId"

                    val seriesResponse =
                        app.get(
                            seriesUrl,
                            headers = apiHeaders
                        ).text

                    val seriesRoot =
                        mapper.readTree(seriesResponse)

                    val seriesData =
                        unwrap(seriesRoot)
                            ?: seriesRoot

                    addEpisodesFromNode(
                        seriesData,
                        episodes
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            /*
             * Benzer animeler.
             */
            try {

                val similarUrl =
                    "$apiUrl/anime/similar?id=$cleanAnimeId"

                val similarResponse =
                    app.get(
                        similarUrl,
                        headers = apiHeaders
                    ).text

                val similarRoot =
                    mapper.readTree(similarResponse)

                val similarList =
                    extractPageList(
                        similarRoot
                    )

                val recommendations =
                    parseAnimeList(
                        similarList
                    )

                /*
                 * recommendations CloudStream tarafında
                 * kullanılabilir ancak zorunlu değil.
                 */
                if (recommendations.isNotEmpty()) {
                    // LoadResponse builder içinde ayrıca atanabilir.
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newAnimeLoadResponse(
            title,
            "$mainUrl/anime/$cleanAnimeId",
            TvType.Anime
        ) {

            posterUrl = poster
            backgroundPosterUrl = banner
            plot = description
            tags = tags

            if (year != null) {
                this.year = year
            }

            if (score != null) {
                this.score = score
            }

            addEpisodes(
                DubStatus.Subbed,
                episodes
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {

        var episodeId = data
        var season = 1
        var episode = 1

        /*
         * Yeni format:
         *
         * episodeId|season|episode
         */
        val parts = data.split("|")

        if (parts.isNotEmpty()) {
            episodeId = parts[0]
        }

        if (parts.size >= 2) {
            season = parts[1].toIntOrNull() ?: 1
        }

        if (parts.size >= 3) {
            episode = parts[2].toIntOrNull() ?: 1
        }

        /*
         * Önce eski çalışan source endpoint formatını deniyoruz.
         */
        val oldSourceUrl =
            "$apiUrl/anime/source?id=${
                URLEncoder.encode(
                    episodeId,
                    "UTF-8"
                )
            }&plan=premium&season=$season&episode=$episode&server="

        /*
         * Yeni endpoint fallback.
         */
        val newSourceUrl =
            "$apiUrl/source?id=${
                URLEncoder.encode(
                    episodeId,
                    "UTF-8"
                )
            }&site=main&plan=standart"

        var found = false

        /*
         * İlk olarak eski endpoint.
         */
        try {

            val response =
                app.get(
                    oldSourceUrl,
                    headers = apiHeaders
                ).text

            val root =
                mapper.readTree(response)

            val content =
                root.get("content")
                    ?: root

            /*
             * Subtitles
             */
            val subtitles =
                content.get("subtitles")
                    ?: root.get("subtitles")

            if (subtitles != null && subtitles.isArray) {

                subtitles.forEach { subtitle ->

                    val url =
                        text(
                            subtitle,
                            "link",
                            "url"
                        ) ?: return@forEach

                    val language =
                        text(
                            subtitle,
                            "name",
                            "language",
                            "label",
                            "lang"
                        ) ?: "Turkish"

                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = language,
                            url = fixUrl(url)
                        )
                    )
                }
            }

            /*
             * groups
             */
            val groups =
                content.get("groups")
                    ?: root.get("groups")

            if (groups != null && groups.isArray) {

                var serverCounter = 1

                groups.forEach { group ->

                    val groupName =
                        text(
                            group,
                            "name",
                            "title",
                            "group"
                        ) ?: "Server"

                    val items =
                        group.get("items")

                    if (items != null && items.isArray) {

                        items.forEach { item ->

                            val rawLink =
                                text(
                                    item,
                                    "link",
                                    "url",
                                    "sourceUrl",
                                    "source_url"
                                ) ?: return@forEach

                            val linkName =
                                text(
                                    item,
                                    "linkName",
                                    "name",
                                    "server"
                                ) ?: "Server $serverCounter"

                            val quality =
                                int(
                                    item,
                                    "quality"
                                ) ?: Qualities.Unknown.value

                            val displayName =
                                "$groupName $linkName".trim()

                            val finalUrl =
                                if (
                                    rawLink.startsWith("http://") ||
                                    rawLink.startsWith("https://")
                                ) {
                                    rawLink
                                } else {
                                    "$sourceUrl/$rawLink".replace(
                                        "//",
                                        "/"
                                    ).replace(
                                        "https:/",
                                        "https://"
                                    )
                                }

                            offsetCallback.invoke(
                                newExtractorLink(
                                    name = displayName,
                                    source = this.name,
                                    url = finalUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    referer = "$mainUrl/"
                                    this.quality = quality
                                }
                            )

                            found = true
                            serverCounter++
                        }
                    }
                }
            }

            /*
             * Eski response doğrudan sourceUrl döndürüyor olabilir.
             */
            if (!found) {

                val directSource =
                    text(
                        content,
                        "sourceUrl",
                        "source_url"
                    )

                if (!directSource.isNullOrBlank()) {

                    val finalUrl =
                        if (
                            directSource.startsWith("http://") ||
                            directSource.startsWith("https://")
                        ) {
                            directSource
                        } else {
                            "$sourceUrl/$directSource"
                                .replace("//", "/")
                                .replace("https:/", "https://")
                        }

                    offsetCallback.invoke(
                        newExtractorLink(
                            name = "Anizium",
                            source = this.name,
                            url = finalUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            referer = "$mainUrl/"
                            quality = Qualities.Unknown.value
                        }
                    )

                    found = true
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        /*
         * Eski endpoint başarısız olduysa mevcut yeni endpoint.
         */
        if (!found) {

            try {

                val response =
                    app.get(
                        newSourceUrl,
                        headers = apiHeaders
                    ).text

                val root =
                    mapper.readTree(response)

                val content =
                    root.get("content")
                        ?: root

                /*
                 * Subtitles
                 */
                val subtitles =
                    content.get("subtitles")
                        ?: root.get("subtitles")

                if (subtitles != null && subtitles.isArray) {

                    subtitles.forEach { subtitle ->

                        val url =
                            text(
                                subtitle,
                                "link",
                                "url"
                            ) ?: return@forEach

                        val language =
                            text(
                                subtitle,
                                "name",
                                "language",
                                "label",
                                "lang"
                            ) ?: "Turkish"

                        subtitleCallback.invoke(
                            SubtitleFile(
                                lang = language,
                                url = fixUrl(url)
                            )
                        )
                    }
                }

                /*
                 * Groups
                 */
                val groups =
                    content.get("groups")
                        ?: root.get("groups")

                if (groups != null && groups.isArray) {

                    var serverCounter = 1

                    groups.forEach { group ->

                        val groupName =
                            text(
                                group,
                                "name",
                                "title",
                                "group"
                            ) ?: "Server"

                        val items =
                            group.get("items")

                        if (items != null && items.isArray) {

                            items.forEach { item ->

                                val rawLink =
                                    text(
                                        item,
                                        "link",
                                        "url",
                                        "sourceUrl",
                                        "source_url"
                                    ) ?: return@forEach

                                val quality =
                                    int(
                                        item,
                                        "quality"
                                    ) ?: Qualities.Unknown.value

                                val serverName =
                                    text(
                                        item,
                                        "name",
                                        "server",
                                        "linkName"
                                    ) ?: "Server $serverCounter"

                                val finalUrl =
                                    if (
                                        rawLink.startsWith("http://") ||
                                        rawLink.startsWith("https://")
                                    ) {
                                        rawLink
                                    } else {
                                        fixUrl(rawLink)
                                    }

                                offsetCallback.invoke(
                                    newExtractorLink(
                                        name = "$groupName $serverName",
                                        source = this.name,
                                        url = finalUrl,
                                        type = ExtractorLinkType.VIDEO
                                    ) {
                                        referer = "$mainUrl/"
                                        this.quality = quality
                                    }
                                )

                                found = true
                                serverCounter++
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return found
    }
}
