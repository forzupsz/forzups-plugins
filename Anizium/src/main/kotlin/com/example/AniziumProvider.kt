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
    data class EpisodeItem(
        @JsonProperty("ID") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("video") val video: String? = null,
        @JsonProperty("file") val file: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SeasonItem(
        @JsonProperty("ID") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("episodes") val episodes: List<EpisodeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GenreItem(
        @JsonProperty("ID") val id: String? = null,
        @JsonProperty("name") val name: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AnimeItem(
        @JsonProperty("ID") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("name_tr") val nameTr: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("banner") val banner: String? = null,
        @JsonProperty("details_banner") val detailsBanner: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("overview_short") val overviewShort: String? = null,
        @JsonProperty("genre") val genre: List<GenreItem>? = null,
        @JsonProperty("seasons") val seasons: List<SeasonItem>? = null,
        @JsonProperty("episodes") val episodes: List<EpisodeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DetailApiResponse(
        @JsonProperty("data") val data: AnimeItem? = null
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
            val animeId = anime.id ?: return@forEach
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
        val animeId = url
        val detailUrl = "$apiUrl/anime/get?id=$animeId"
        
        var title = "Anime"
        var poster: String? = null
        var bannerUrl: String? = null
        var description: String? = null
        val genreTags = ArrayList<String>()
        val episodesList = ArrayList<Episode>()

        try {
            val res = app.get(detailUrl, headers = apiHeaders).parsedSafe<DetailApiResponse>()
            val anime = res?.data

            if (anime != null) {
                title = anime.nameTr ?: anime.name ?: anime.title ?: "Anime"
                poster = fixUrlNull(anime.poster)
                bannerUrl = fixUrlNull(anime.detailsBanner ?: anime.banner)
                description = anime.overview ?: anime.overviewShort

                anime.genre?.forEach { g ->
                    g.name?.let { genreTags.add(it) }
                }

                // GÖRSELDEKİ 'seasons' -> 'episodes' HİYERARŞİSİ
                anime.seasons?.forEach { season ->
                    val seasonNumber = season.number ?: 1
                    
                    season.episodes?.forEach { ep ->
                        val epId = ep.id ?: return@forEach
                        val epName = ep.name ?: "Bölüm"
                        val epNum = ep.number
                        val videoData = ep.video ?: ep.file ?: epId

                        episodesList.add(newEpisode(videoData) {
                            this.name = epName
                            this.season = seasonNumber
                            this.episode = epNum
                        })
                    }
                }

                // Eğer 'seasons' yoksa düz 'episodes' dizisini kontrol et
                if (episodesList.isEmpty()) {
                    anime.episodes?.forEach { ep ->
                        val epId = ep.id ?: return@forEach
                        val epName = ep.name ?: "Bölüm"
                        val videoData = ep.video ?: ep.file ?: epId

                        episodesList.add(newEpisode(videoData) {
                            this.name = epName
                            this.episode = ep.number
                        })
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newAnimeLoadResponse(title, animeId, TvType.Anime) {
            this.posterUrl = poster
            this.backgroundPosterUrl = bannerUrl
            this.plot = description
            this.tags = genreTags
            addEpisodes(DubStatus.Subbed, episodesList)
        }
    }

    // --- 4. VİDEO OYNATICI ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val videoUrl = if (data.startsWith("http")) data else "$apiUrl/episode/get?id=$data"

            offsetCallback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = videoUrl,
                    referer = "$mainUrl/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = videoUrl.contains(".m3u8")
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
