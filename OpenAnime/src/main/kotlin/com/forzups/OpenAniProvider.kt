package com.forzups

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

class OpenAniProvider : MainAPI() {
    override var mainUrl = "https://openani.me"
    private val apiUrl = "https://canvas.openani.me"
    
    override var name = "OpenAni"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    private val apiHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "Accept" to "application/json, text/plain, */*"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AnimeItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("image") val image: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EpisodeItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AnimeDetail(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("episodes") val episodes: List<EpisodeItem>? = null,
        @JsonProperty("genres") val genres: List<String>? = null
    )

    private fun parseAnimeList(list: List<AnimeItem>?): List<SearchResponse> {
        val items = ArrayList<SearchResponse>()
        list?.forEach { anime ->
            val animeTitle = anime.title ?: anime.name ?: return@forEach
            val animeSlug = anime.slug ?: anime.id ?: return@forEach
            val posterUrl = anime.poster ?: anime.image ?: anime.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

            items.add(newAnimeSearchResponse(animeTitle, "$mainUrl/anime/$animeSlug", TvType.Anime) {
                this.posterUrl = fixUrlNull(posterUrl)
            })
        }
        return items
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()

        try {
            val latestRes = app.get("$apiUrl/latest?limit=24", headers = apiHeaders).parsedSafe<List<AnimeItem>>()
            val latestItems = parseAnimeList(latestRes)
            if (latestItems.isNotEmpty()) {
                homePageList.add(HomePageList("Son Eklenenler", latestItems))
            }

            val popularRes = app.get("$apiUrl/populars?limit=24", headers = apiHeaders).parsedSafe<List<AnimeItem>>()
            val popularItems = parseAnimeList(popularRes)
            if (popularItems.isNotEmpty()) {
                homePageList.add(HomePageList("Popüler Animeler", popularItems))
            }

            val releasesRes = app.get("$apiUrl/4k-releases", headers = apiHeaders).parsedSafe<List<AnimeItem>>()
            val releasesItems = parseAnimeList(releasesRes)
            if (releasesItems.isNotEmpty()) {
                homePageList.add(HomePageList("4K İçerikler", releasesItems))
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(list = homePageList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val searchUrl = "$apiUrl/search?q=${query.trim().replace(" ", "+")}"
            val response = app.get(searchUrl, headers = apiHeaders).parsedSafe<List<AnimeItem>>()
            parseAnimeList(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.substringAfterLast("/").trim()
        val detailUrl = "$apiUrl/anime/$slug"
        val docRes = app.get(detailUrl, headers = apiHeaders).parsedSafe<AnimeDetail>()

        val title = docRes?.title ?: docRes?.name ?: "Anime"
        val poster = docRes?.poster ?: docRes?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val description = docRes?.overview ?: docRes?.description

        val episodesList = ArrayList<Episode>()
        docRes?.episodes?.forEachIndexed { index, ep ->
            val epNum = ep.number ?: (index + 1)
            val epName = ep.title ?: ep.name ?: "$epNum. Bölüm"
            val epId = ep.id ?: "$slug-$epNum"

            episodesList.add(newEpisode(epId) {
                this.name = epName
                this.episode = epNum
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = description
            this.tags = docRes?.genres
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
            val sourceUrl = "$apiUrl/source/$data"
            val resText = app.get(sourceUrl, headers = apiHeaders).text
            var found = false

            if (resText.contains("http")) {
                val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                val rootNode = mapper.readTree(resText)
                
                val embedUrl = rootNode.get("url")?.asText() ?: rootNode.get("file")?.asText() ?: ""
                if (embedUrl.isNotEmpty()) {
                    loadExtractor(fixUrl(embedUrl), subtitleCallback, offsetCallback)
                    found = true
                }
            }
            found
        } catch (e: Exception) {
            false
        }
    }
}
