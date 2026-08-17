package com.forzups

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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

            val latestItems = doc.select("a[href*=/anime/], a[href*=/izle/], div.anime, div.card, .item, article").mapNotNull { element ->
                val linkElement = if (element.tagName() == "a") element else element.selectFirst("a")
                val href = linkElement?.attr("href") ?: return@mapNotNull null
                if (href == "/anime" || href == "/animeler") return@mapNotNull null

                val title = element.selectFirst("h1, h2, h3, h4, .title, .name, img")?.let {
                    if (it.tagName() == "img") it.attr("alt") else it.text()
                }?.trim() ?: return@mapNotNull null

                if (title.isEmpty()) return@mapNotNull null

                val poster = element.selectFirst("img")?.let { img ->
                    img.attr("src").ifEmpty { img.attr("data-src") }.ifEmpty { img.attr("srcset") }
                }

                newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                }
            }.distinctBy { it.url }

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

            doc.select("a[href*=/anime/], div.card, .search-result").mapNotNull { element ->
                val linkElement = if (element.tagName() == "a") element else element.selectFirst("a")
                val href = linkElement?.attr("href") ?: return@mapNotNull null

                val title = element.selectFirst("h1, h2, h3, .title, img")?.let {
                    if (it.tagName() == "img") it.attr("alt") else it.text()
                }?.trim() ?: return@mapNotNull null

                val poster = element.selectFirst("img")?.attr("src")

                newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                }
            }.distinctBy { it.url }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = defaultHeaders).document

        val title = doc.selectFirst("h1, .title, .anime-title")?.text()?.trim() ?: "Anime"
        val poster = doc.selectFirst("img[src*=/poster/], img[src*=/cover/], .poster img, img")?.attr("src")
        val description = doc.selectFirst(".description, .overview, .synopsis, p")?.text()?.trim()
        val genres = doc.select("a[href*=/tur/], a[href*=/genre/], .genre").map { it.text().trim() }

        val episodesList = ArrayList<Episode>()
        val epElements = doc.select("a[href*=/bolum], a[href*=-bolum-], .episode-item, .episodes a")

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

            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty()) {
                    loadExtractor(fixUrl(src), subtitleCallback, offsetCallback)
                    found = true
                }
            }

            found
        } catch (e: Exception) {
            false
        }
    }
}
