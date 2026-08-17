package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

class AniziumProvider : MainAPI() {
    override var mainUrl = "https://anizium.co"
    private val apiUrl = "https://api.anizium.co"
    
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    // Sitenin doğrulama başlıkları
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

    data class ApiResponse(
        @JsonProperty("data") val data: List<AnimeItem>? = null,
        @JsonProperty("animes") val animes: List<AnimeItem>? = null
    )

    data class AnimeItem(
        @JsonProperty("id") val id: Any? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("image") val image: String? = null,
        @JsonProperty("cover") val cover: String? = null
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<SearchResponse>()
        try {
            val jsonUrl = "$apiUrl/page/top?platform=favorite&page=1"
            val response = app.get(jsonUrl, headers = apiHeaders).parsedSafe<ApiResponse>()
            val list = response?.data ?: response?.animes

            list?.forEach { anime ->
                val animeTitle = anime.title ?: anime.name ?: return@forEach
                val animeSlug = anime.slug ?: anime.id?.toString() ?: return@forEach
                val posterUrl = fixUrlNull(anime.poster ?: anime.image ?: anime.cover)

                items.add(newAnimeSearchResponse(animeTitle, "$mainUrl/anime/$animeSlug", TvType.Anime) {
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
            val searchUrl = "$apiUrl/search?q=$query"
            val response = app.get(searchUrl, headers = apiHeaders).parsedSafe<ApiResponse>()
            val list = response?.data ?: response?.animes

            list?.forEach { anime ->
                val animeTitle = anime.title ?: anime.name ?: return@forEach
                val animeSlug = anime.slug ?: anime.id?.toString() ?: return@forEach
                val posterUrl = fixUrlNull(anime.poster ?: anime.image ?: anime.cover)

                items.add(newAnimeSearchResponse(animeTitle, "$mainUrl/anime/$animeSlug", TvType.Anime) {
                    this.posterUrl = posterUrl
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = apiHeaders).document
        
        val title = document.selectFirst("h1, .title")?.text()?.trim() ?: "Anime"
        val poster = fixUrlNull(
            document.selectFirst("img.poster, .cover img, meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("img.poster, .cover img, img")?.attr("src")
        )

        val episodes = ArrayList<Episode>()
        document.select("a[href*=/episode/], a[href*=/bolum/], a[href*=/watch/]").forEach { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
            val epName = ep.text().trim().ifEmpty { "Bölüm" }
            episodes.add(newEpisode(epHref) {
                this.name = epName
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
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
