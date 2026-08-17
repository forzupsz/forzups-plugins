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
    data class SearchApiResponse(
        @JsonProperty("page") val page: PageData? = null,
        @JsonProperty("data") val data: List<AnimeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageData(
        @JsonProperty("data") val data: List<AnimeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AnimeItem(
        @JsonProperty("ID") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("overview") val overview: String? = null
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()

        try {
            val response = app.get("$apiUrl/page/home", headers = apiHeaders).parsedSafe<HomeApiResponse>()

            // 1. Vitrin / Öne Çıkanlar
            val topItems = response?.settlementTop?.toSearchResponses() ?: emptyList()
            if (topItems.isNotEmpty()) homePageList.add(HomePageList("Öne Çıkan Animeler", topItems))

            // 2. Haftanın En Çok İzlenenleri
            val middleItems = response?.settlementMiddle?.toSearchResponses() ?: emptyList()
            if (middleItems.isNotEmpty()) homePageList.add(HomePageList("Haftanın En Çok İzlenenleri", middleItems))

            // 3. Görselde Yakaladığımız 'special_list' İçindeki Tüm Özel Kategoriler (İsekai, Dublaj, vb.)
            response?.specialList?.forEach { category ->
                val categoryName = category.name ?: return@forEach
                val animeList = category.data?.toSearchResponses() ?: emptyList()
                if (animeList.isNotEmpty()) {
                    homePageList.add(HomePageList(categoryName, animeList))
                }
            }

            // 4. Alt Liste
            val lowerItems = response?.settlementLower?.toSearchResponses() ?: emptyList()
            if (lowerItems.isNotEmpty()) homePageList.add(HomePageList("Önerilen Animeler", lowerItems))

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(list = homePageList, hasNext = false)
    }

    private fun List<AnimeItem>.toSearchResponses(): List<SearchResponse> {
        val items = ArrayList<SearchResponse>()
        this.forEach { anime ->
            val animeName = anime.name ?: anime.title ?: return@forEach
            val animeId = anime.id ?: return@forEach
            items.add(newAnimeSearchResponse(animeName, animeId, TvType.Anime) {
                this.posterUrl = fixUrlNull(anime.poster)
            })
        }
        return items
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = ArrayList<SearchResponse>()
        try {
            val cleanQuery = query.trim().replace(" ", "+")
            val searchUrl = "$apiUrl/page/search?value=$cleanQuery&page=1"
            val response = app.get(searchUrl, headers = apiHeaders).parsedSafe<SearchApiResponse>()
            val listData = response?.page?.data ?: response?.data

            items.addAll(listData?.toSearchResponses() ?: emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse {
        val animeId = url
        val detailUrl = "$apiUrl/anime/get?id=$animeId"
        
        var title = "Anime"
        var poster: String? = null
        var description: String? = null
        val episodes = ArrayList<Episode>()

        try {
            val jsonText = app.get(detailUrl, headers = apiHeaders).text
            val mapper = mapper
            val node = mapper.readTree(jsonText)
            
            val dataNode = if (node.has("data")) node.get("data") else node
            
            title = dataNode.get("name")?.asText() ?: dataNode.get("title")?.asText() ?: "Anime"
            poster = fixUrlNull(dataNode.get("poster")?.asText())
            description = dataNode.get("overview")?.asText()

            val seriesNode = dataNode.get("series") ?: dataNode.get("episodes")
            if (seriesNode != null && seriesNode.isArray) {
                seriesNode.forEach { ep ->
                    val epId = ep.get("ID")?.asText() ?: ep.get("id")?.asText() ?: return@forEach
                    val epName = ep.get("name")?.asText() ?: ep.get("title")?.asText() ?: "Bölüm"
                    episodes.add(newEpisode(epId) {
                        this.name = epName
                    })
                }
            } else {
                val seasonsNode = dataNode.get("seasons")
                seasonsNode?.forEach { season ->
                    val epList = season.get("episodes") ?: season.get("series")
                    epList?.forEach { ep ->
                        val epId = ep.get("ID")?.asText() ?: ep.get("id")?.asText() ?: return@forEach
                        val epName = ep.get("name")?.asText() ?: ep.get("title")?.asText() ?: "Bölüm"
                        episodes.add(newEpisode(epId) {
                            this.name = epName
                        })
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newAnimeLoadResponse(title, animeId, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        return true
    }
}
