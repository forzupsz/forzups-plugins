package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.net.URLEncoder

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
        "cf-control" to "134e08135d45075c55080906594e0d0400424c055f55560f",
        "site" to "main",
        "device" to "browser",
        "language" to "tr"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ApiResponse(
        @JsonProperty("page") val page: PageData? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageData(
        @JsonProperty("data") val data: List<AnimeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AnimeItem(
        @JsonProperty("ID") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("overview") val overview: String? = null
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<SearchResponse>()
        try {
            val jsonUrl = "$apiUrl/page/top?platform=favorite&page=1"
            val response = app.get(jsonUrl, headers = apiHeaders).parsedSafe<ApiResponse>()
            
            response?.page?.data?.forEach { anime ->
                val animeName = anime.name ?: return@forEach
                val animeId = anime.id ?: return@forEach
                val posterUrl = fixUrlNull(anime.poster)

                items.add(newAnimeSearchResponse(animeName, animeId, TvType.Anime) {
                    this.posterUrl = posterUrl
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(
            list = HomePageList("En Favori Animeler", items),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = ArrayList<SearchResponse>()
        try {
            val encodedQuery = URLEncoder.encode(query.lowercase().trim(), "UTF-8")
            val searchUrl = "$apiUrl/search?value=$encodedQuery&page=1"
            val response = app.get(searchUrl, headers = apiHeaders).parsedSafe<ApiResponse>()

            response?.page?.data?.forEach { anime ->
                val animeName = anime.name ?: return@forEach
                val animeId = anime.id ?: return@forEach
                val posterUrl = fixUrlNull(anime.poster)

                items.add(newAnimeSearchResponse(animeName, animeId, TvType.Anime) {
                    this.posterUrl = posterUrl
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse {
        val animeId = url
        // Kesinleştirdiğimiz gerçek detay API adresi
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
            
            title = dataNode.get("name")?.asText() ?: "Anime"
            poster = fixUrlNull(dataNode.get("poster")?.asText())
            description = dataNode.get("overview")?.asText()

            // Sezon veya doğrudan bölüm listesini tara
            val seasonsNode = dataNode.get("seasons")
            if (seasonsNode != null && seasonsNode.isArray) {
                seasonsNode.forEach { season ->
                    val episodesNode = season.get("episodes")
                    episodesNode?.forEach { ep ->
                        val epId = ep.get("ID")?.asText() ?: return@forEach
                        val epName = ep.get("name")?.asText() ?: "Bölüm"
                        episodes.add(newEpisode(epId) {
                            this.name = epName
                        })
                    }
                }
            } else {
                val episodesNode = dataNode.get("episodes")
                episodesNode?.forEach { ep ->
                    val epId = ep.get("ID")?.asText() ?: return@forEach
                    val epName = ep.get("name")?.asText() ?: "Bölüm"
                    episodes.add(newEpisode(epId) {
                        this.name = epName
                    })
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
