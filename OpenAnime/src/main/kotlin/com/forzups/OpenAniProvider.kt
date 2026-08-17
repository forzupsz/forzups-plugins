package com.forzups

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.jsoup.nodes.Document

class OpenAniProvider : MainAPI() {
    override var mainUrl = "https://openani.me"
    override var name = "OpenAni"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()
        try {
            val doc = app.get(mainUrl, headers = defaultHeaders).document

            val latestItems = doc.select("div.anime-card, div.poster-card, div.item").mapNotNull { element ->
                val title = element.selectFirst("h3, h2, .title, .name")?.text() ?: return@mapNotNull null
                val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val poster = element.selectFirst("img")?.attr("src") ?: element.selectFirst("img")?.attr("data-src")

                newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                }
            }

            if (latestItems.isNotEmpty()) {
                homePageList.add(HomePageList("Son Eklenenler", latestItems))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(list = homePageList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val searchUrl = "$mainUrl/ara?q=${query.trim().replace(" ", "+")}"
            val doc = app.get(searchUrl, headers = defaultHeaders).document

            doc.select("div.anime-card, div.poster-card, div.search-result").mapNotNull { element ->
                val title = element.selectFirst("h3, h2, .title, .name")?.text() ?: return@mapNotNull null
                val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val poster = element.selectFirst("img")?.attr("src") ?: element.selectFirst("img")?.attr("data-src")

                newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = defaultHeaders).document

        val title = doc.selectFirst("h1.title, h1, .anime-title")?.text()?.trim() ?: "Anime"
        val poster = doc.selectFirst("div.poster img, img.poster")?.attr("src") 
            ?: doc.selectFirst("div.poster img, img.poster")?.attr("data-src")
        val description = doc.selectFirst("div.description, div.overview, p.synopsis")?.text()?.trim()
        val genres = doc.select("div.genres a, .genre-item").map { it.text().trim() }

        val episodesList = ArrayList<Episode>()

        val epElements = doc.select("a.episode-item, div.episode-list a, ul.episodes-list a")
        if (epElements.isNotEmpty()) {
            epElements.forEachIndexed { index, ep ->
                val epHref = ep.attr("href")
                val epTitle = ep.text().trim().ifEmpty { "${index + 1}. Bölüm" }

                episodesList.add(newEpisode(fixUrl(epHref)) {
                    this.name = epTitle
                    this.episode = index + 1
                })
            }
        } else {
            episodesList.add(newEpisode(url) {
                this.name = "1. Bölüm"
                this.episode = 1
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = description
            this.tags = genres
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
            val doc = app.get(data, headers = defaultHeaders).document
            var found = false

            val iframes = doc.select("iframe")
            iframes.forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty()) {
                    val embedUrl = fixUrl(src)
                    loadExtractor(embedUrl, subtitleCallback, offsetCallback)
                    found = true
                }
            }

            val videoTags = doc.select("video source")
            videoTags.forEach { video ->
                val videoUrl = video.attr("src")
                if (videoUrl.isNotEmpty()) {
                    offsetCallback.invoke(
                        newExtractorLink(
                            name = this.name,
                            source = this.name,
                            url = fixUrl(videoUrl),
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "$mainUrl/"
                        }
                    )
                    found = true
                }
            }

            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
