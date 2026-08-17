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
        "Cf-Control" to "134e1d060e5b505855080906594e0d040b414d035751510f",
        "Site" to "main",
        "Device" to "browser",
        "Language" to "tr",
        "User-Profile" to "null",
        "User-Session" to "null"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ApiResponse(
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

    // Çoklu sayfa çekip 10 anime sınırını aşan yardımcı fonksiyon
    private suspend fun fetchAnimeList(urlBuilder: (Int) -> String, maxPages: Int = 3): List<SearchResponse> {
        val items = ArrayList<SearchResponse>()
        for (p in 1..maxPages) {
            try {
                val response = app.get(urlBuilder(p), headers = apiHeaders).parsedSafe<ApiResponse>()
                val listData = response?.page?.data ?: response?.data ?: break
                if (listData.isEmpty()) break
                
                listData.forEach { anime ->
                    val animeName = anime.name ?: anime.title ?: return@forEach
                    val animeId = anime.id ?: return@forEach
                    items.add(newAnimeSearchResponse(animeName, animeId, TvType.Anime) {
                        this.posterUrl = fixUrlNull(anime.poster)
                    })
                }
            } catch (e: Exception) {
                break
            }
        }
        return items
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()

        // 1. En Favori Animeler (3 Sayfa = 30+ Anime)
        val topList = fetchAnimeList({ p -> "$apiUrl/page/top?platform=favorite&page=$p" }, maxPages = 3)
        if (topList.isNotEmpty()) homePageList.add(HomePageList("En Favori Animeler", topList))

        // 2. Son Eklenenler
        val lastList = fetchAnimeList({ p -> "$apiUrl/page/last-added-episodes?page=$p" }, maxPages = 2)
        if (lastList.isNotEmpty()) homePageList.add(HomePageList("Son Eklenenler", lastList))

        // 3. Türkçe Dublaj (Tür ID: 79741)
        val dubList = fetchAnimeList({ p -> "$apiUrl/page/catalog?id=79741&type=genre&page=$p" }, maxPages = 2)
        if (dubList.isNotEmpty()) homePageList.add(HomePageList("Türkçe Dublaj Animeler", dubList))

        // 4. Komedi (Tür ID: 47450)
        val comedyList = fetchAnimeList({ p -> "$apiUrl/page/catalog?id=47450&type=genre&page=$p" }, maxPages = 2)
        if (comedyList.isNotEmpty()) homePageList.add(HomePageList("Komedi Animeleri", comedyList))

        // 5. Fantastik (Tür ID: 43261)
        val fantasyList = fetchAnimeList({ p -> "$apiUrl/page/catalog?id=43261&type=genre&page=$p" }, maxPages = 2)
        if (fantasyList.isNotEmpty()) homePageList.add(HomePageList("Fantastik Animeler", fantasyList))

        // 6. Aksiyon (Tür ID: 62263)
        val actionList = fetchAnimeList({ p -> "$apiUrl/page/catalog?id=62263&type=genre&page=$p" }, maxPages = 2)
        if (actionList.isNotEmpty()) homePageList.add(HomePageList("Aksiyon Animeleri", actionList))

        return newHomePageResponse(list = homePageList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Yakaladığımız GERÇEK ARAMA ADRESİ: /page/search?value=...
        val cleanQuery = query.trim().replace(" ", "+")
        return fetchAnimeList({ p -> "$apiUrl/page/search?value=$cleanQuery&page=$p" }, maxPages = 2)
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
