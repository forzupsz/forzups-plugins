package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

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

    // --- JSON MODEL YAPILARI ---

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

    // --- YARDIMCI FONKSİYONLAR ---

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

    // --- 1. ANA SAYFA ---
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

    // --- 2. ARAMA ---
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

    // --- 3. DETAY VE BÖLÜMLER ---
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
                ?: cleanId

            val seriesResText = try {
                app.get("$apiUrl/anime/series?id=$targetSeriesId", headers = apiHeaders).text
            } catch (e: Exception) {
                mainResText
            }

            val seriesNode = mapper.readTree(seriesResText)
            
            val seriesDataArray = when {
                seriesNode.has("data") && seriesNode.get("data").isArray -> seriesNode.get("data")
                seriesNode.isArray -> seriesNode
                else -> null
            }

            if (seriesDataArray != null && seriesDataArray.isArray) {
                seriesDataArray.forEach { animeSeriesObj ->
                    val seasonsNode = animeSeriesObj.get("seasons")
                    if (seasonsNode != null && seasonsNode.isArray) {
                        seasonsNode.forEach { season ->
                            val seasonNumber = season.get("number")?.asInt() ?: season.get("season_number")?.asInt() ?: 1
                            val epList = season.get("episodes") ?: season.get("series")
                            
                            epList?.forEach { ep ->
                                val epId = ep.get("id")?.asText() ?: ep.get("ID")?.asText() ?: return@forEach
                                val epName = ep.get("name")?.asText() ?: ep.get("title")?.asText() ?: "Bölüm"
                                val epNum = ep.get("number")?.asInt() ?: ep.get("episode_number")?.asInt()

                                episodesList.add(newEpisode(epId) {
                                    this.name = epName
                                    this.season = seasonNumber
                                    this.episode = epNum
                                })
                            }
                        }
                    } else {
                        val directEpList = animeSeriesObj.get("episodes") ?: animeSeriesObj.get("series")
                        directEpList?.forEach { ep ->
                            val epId = ep.get("id")?.asText() ?: ep.get("ID")?.asText() ?: return@forEach
                            val epName = ep.get("name")?.asText() ?: ep.get("title")?.asText() ?: "Bölüm"
                            val epNum = ep.get("number")?.asInt() ?: ep.get("episode_number")?.asInt()

                            episodesList.add(newEpisode(epId) {
                                this.name = epName
                                this.episode = epNum
                            })
                        }
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

    // --- 4. VİDEO LINKLERİ ---
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
            val contentNode = rootNode.get("content")
            val groupsNode = contentNode?.get("groups")

            if (groupsNode != null && groupsNode.isArray) {
                groupsNode.forEach { group ->
                    val groupName = group.get("name")?.asText() ?: "Video"
                    val itemsNode = group.get("items")

                    if (itemsNode != null && itemsNode.isArray) {
                        itemsNode.forEach { item ->
                            val rawLink = item.get("link")?.asText() ?: return@forEach
                            val qualityVal = item.get("quality")?.asInt() ?: Qualities.Unknown.value

                            val streamUrl = fixUrl(rawLink)

                            offsetCallback.invoke(
                                newExtractorLink(
                                    name = "${this.name} - $groupName",
                                    source = this.name,
                                    url = streamUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://anizium.co/"
                                    this.quality = qualityVal
                                }
                            )
                            found = true
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
