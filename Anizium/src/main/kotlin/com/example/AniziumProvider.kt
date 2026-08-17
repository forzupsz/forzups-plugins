package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

class AniziumProvider : MainAPI() {
    override var mainUrl = "https://anizium.co"
    private val apiUrl = "https://api.anizium.co"
    
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    private val apiHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Content-Type" to "application/json",
        "Origin" to "https://anizium.co",
        "Referer" to "https://anizium.co/",
        "Cf-Control" to "134e1a5e0909175c55080906594e0d040b4440075851560f",
        "Site" to "main",
        "Device" to "browser",
        "Language" to "tr",
        "User-Profile" to "null",
        "User-Session" to "null"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AnimeItem(
        @JsonProperty("ID") val id: String? = null,
        @JsonProperty("series_id") val seriesId: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("name_tr") val nameTr: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster") val poster: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class HomeApiResponse(
        @JsonProperty("settlement_top") val settlementTop: List<AnimeItem>? = null,
        @JsonProperty("settlement_middle") val settlementMiddle: List<AnimeItem>? = null,
        @JsonProperty("settlement_lower") val settlementLower: List<AnimeItem>? = null,
        @JsonProperty("special_list") val specialList: List<SpecialItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SpecialItem(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("data") val data: List<AnimeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageApiResponse(
        @JsonProperty("page") val page: PageData? = null,
        @JsonProperty("data") val data: List<AnimeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageData(
        @JsonProperty("data") val data: List<AnimeItem>? = null
    )

    private fun parseAnimeList(list: List<AnimeItem>?): List<SearchResponse> {
        val items = ArrayList<SearchResponse>()
        list?.forEach { anime ->
            val animeName = anime.nameTr ?: anime.name ?: anime.title ?: return@forEach
            val animeId = anime.id ?: anime.slug ?: return@forEach
            items.add(newAnimeSearchResponse(animeName, animeId, TvType.Anime) {
                this.posterUrl = fixUrlNull(anime.poster)
            })
        }
        return items
    }

    private fun parseEpisodesFromNode(
        containerNode: JsonNode?,
        episodesList: ArrayList<Episode>
    ) {
        if (containerNode == null) return

        val seasonsArray = when {
            containerNode.has("seasons") && containerNode.get("seasons").isArray -> containerNode.get("seasons")
            containerNode.isArray -> containerNode
            else -> null
        }

        if (seasonsArray != null) {
            seasonsArray.forEach { seasonObj ->
                val seasonNumber = seasonObj.get("number")?.asInt() 
                    ?: seasonObj.get("season_number")?.asInt() 
                    ?: 1
                
                val epList = seasonObj.get("episodes") ?: seasonObj.get("series") ?: seasonObj.get("data")
                epList?.forEach { ep ->
                    val epId = ep.get("id")?.asText() 
                        ?: ep.get("ID")?.asText() 
                        ?: ep.get("episode_id")?.asText() 
                        ?: return@forEach

                    val epName = ep.get("name")?.asText() ?: ep.get("title")?.asText() ?: "Bölüm"
                    val epNum = ep.get("number")?.asInt() ?: ep.get("episode_number")?.asInt() ?: 1

                    episodesList.add(newEpisode(epId) {
                        this.name = epName
                        this.season = seasonNumber
                        this.episode = epNum
                    })
                }
            }
        } else {
            val directEpList = containerNode.get("episodes") ?: containerNode.get("series") ?: containerNode.get("data")
            if (directEpList != null && directEpList.isArray) {
                directEpList.forEach { ep ->
                    val epId = ep.get("id")?.asText() 
                        ?: ep.get("ID")?.asText() 
                        ?: ep.get("episode_id")?.asText() 
                        ?: return@forEach

                    val epName = ep.get("name")?.asText() ?: ep.get("title")?.asText() ?: "Bölüm"
                    val epNum = ep.get("number")?.asInt() ?: ep.get("episode_number")?.asInt() ?: 1

                    episodesList.add(newEpisode(epId) {
                        this.name = epName
                        this.season = 1
                        this.episode = epNum
                    })
                }
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()

        try {
            val lastAddedRes = app.get("$apiUrl/page/last-added-episodes?page=1", headers = apiHeaders).parsedSafe<PageApiResponse>()
            val lastAddedItems = parseAnimeList(lastAddedRes?.page?.data ?: lastAddedRes?.data)
            if (lastAddedItems.isNotEmpty()) {
                homePageList.add(HomePageList("Son Eklenen Bölümler", lastAddedItems))
            }

            val homeRes = app.get("$apiUrl/page/home", headers = apiHeaders).parsedSafe<HomeApiResponse>()

            val topItems = parseAnimeList(homeRes?.settlementTop)
            if (topItems.isNotEmpty()) homePageList.add(HomePageList("Öne Çıkan Animeler", topItems))

            val middleItems = parseAnimeList(homeRes?.settlementMiddle)
            if (middleItems.isNotEmpty()) homePageList.add(HomePageList("Haftanın En Çok İzlenenleri", middleItems))

            homeRes?.specialList?.forEach { category ->
                val categoryName = category.name ?: return@forEach
                val animeList = parseAnimeList(category.data)
                if (animeList.isNotEmpty()) {
                    homePageList.add(HomePageList(categoryName, animeList))
                }
            }

            val lowerItems = parseAnimeList(homeRes?.settlementLower)
            if (lowerItems.isNotEmpty()) homePageList.add(HomePageList("Önerilen Animeler", lowerItems))

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(list = homePageList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val cleanQuery = query.trim().replace(" ", "+")
            val searchUrl = "$apiUrl/page/search?value=$cleanQuery&page=1"
            val response = app.get(searchUrl, headers = apiHeaders).parsedSafe<PageApiResponse>()
            val listData = response?.page?.data ?: response?.data
            parseAnimeList(listData)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanId = url.substringAfterLast("/").substringBefore("?").trim()
        val mainDetailUrl = "$apiUrl/anime/get?id=$cleanId"

        var title = ""
        var poster: String? = null
        var bannerUrl: String? = null
        var description: String? = null
        val genreTags = ArrayList<String>()
        val episodesList = ArrayList<Episode>()

        try {
            val mainResText = app.get(mainDetailUrl, headers = apiHeaders).text
            val mainNode = mapper.readTree(mainResText)
            val dataNode = if (mainNode.has("data") && !mainNode.get("data").isNull) mainNode.get("data") else mainNode

            title = dataNode.get("name")?.asText() 
                ?: dataNode.get("name_tr")?.asText() 
                ?: dataNode.get("title")?.asText() 
                ?: "Anime"

            poster = fixUrlNull(dataNode.get("poster")?.asText() ?: dataNode.get("mobile_poster_link")?.asText())
            bannerUrl = fixUrlNull(dataNode.get("details_banner")?.asText() ?: dataNode.get("banner")?.asText())
            description = dataNode.get("overview")?.asText() ?: dataNode.get("overview_short")?.asText()

            val genreNode = dataNode.get("genre") ?: dataNode.get("genres")
            if (genreNode != null && genreNode.isArray) {
                genreNode.forEach { g ->
                    val gName = g.get("name")?.asText()
                    if (!gName.isNullOrEmpty()) genreTags.add(gName)
                }
            }

            val targetSeriesId = dataNode.get("series_id")?.asText() 
                ?: dataNode.get("series")?.asText() 
                ?: dataNode.get("series")?.get("id")?.asText() 
                ?: dataNode.get("ID")?.asText()
                ?: dataNode.get("id")?.asText()
                ?: cleanId

            parseEpisodesFromNode(dataNode, episodesList)

            if (episodesList.isEmpty()) {
                val seriesResText = try {
                    app.get("$apiUrl/anime/series?id=$targetSeriesId", headers = apiHeaders).text
                } catch (e: Exception) {
                    ""
                }

                if (seriesResText.isNotEmpty()) {
                    val seriesNode = mapper.readTree(seriesResText)
                    val seriesDataArray = when {
                        seriesNode.has("data") -> seriesNode.get("data")
                        seriesNode.isArray -> seriesNode
                        else -> seriesNode
                    }

                    if (seriesDataArray.isArray) {
                        seriesDataArray.forEach { animeSeriesObj ->
                            parseEpisodesFromNode(animeSeriesObj, episodesList)
                        }
                    } else {
                        parseEpisodesFromNode(seriesDataArray, episodesList)
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newAnimeLoadResponse(if (title.isEmpty()) "Anime" else title, "$mainUrl/anime/$cleanId", TvType.Anime) {
            this.posterUrl = poster
            this.backgroundPosterUrl = bannerUrl
            this.plot = description
            this.tags = genreTags
            addEpisodes(DubStatus.Subbed, episodesList)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val sourceApiUrl = "$apiUrl/source?id=$data&site=main&plan=standart"
            val resText = app.get(sourceApiUrl, headers = apiHeaders).text
            val rootNode = mapper.readTree(resText)

            var found = false
            val contentNode = if (rootNode.has("content")) rootNode.get("content") else rootNode

            val subtitlesNode = contentNode?.get("subtitles") ?: rootNode.get("subtitles")
            if (subtitlesNode != null && subtitlesNode.isArray) {
                subtitlesNode.forEach { sub ->
                    val subUrl = sub.get("link")?.asText() ?: sub.get("url")?.asText() ?: return@forEach
                    val subLang = sub.get("name")?.asText() 
                        ?: sub.get("language")?.asText() 
                        ?: sub.get("label")?.asText() 
                        ?: "Turkish"
                    
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = subLang,
                            url = fixUrl(subUrl)
                        )
                    )
                }
            }

            val groupsNode = contentNode?.get("groups") ?: rootNode.get("groups")
            if (groupsNode != null && groupsNode.isArray) {
                groupsNode.forEach { group ->
                    val groupLang = group.get("name")?.asText() 
                        ?: group.get("title")?.asText() 
                        ?: "Japonca"
                    
                    val itemsNode = group.get("items")
                    if (itemsNode != null && itemsNode.isArray) {
                        var serverCounter = 1
                        itemsNode.forEach { item ->
                            val rawLink = item.get("link")?.asText() ?: return@forEach
                            val qualityVal = item.get("quality")?.asInt() ?: Qualities.Unknown.value
                            val qualityText = if (qualityVal > 0) "${qualityVal}p" else ""
                            
                            val serverName = item.get("name")?.asText() 
                                ?: item.get("server")?.asText() 
                                ?: "Server $serverCounter"

                            val displayName = "$groupLang $qualityText $serverName".trim()
                            val streamUrl = fixUrl(rawLink)

                            offsetCallback.invoke(
                                newExtractorLink(
                                    name = displayName,
                                    source = this.name,
                                    url = streamUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://anizium.co/"
                                    this.quality = qualityVal
                                }
                            )
                            found = true
                            serverCounter++
                        }
                    }
                }
            }

            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
