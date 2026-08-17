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
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Sec-Ch-Ua" to "\"Not-A.Brand\";v=\"99\", \"Chromium\";v=\"124\"",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\""
    )

    private fun extractAnimeFromDoc(doc: Document): List<SearchResponse> {
        val items = ArrayList<SearchResponse>()
        
        // Sitedeki tüm anime kartı ve bağlantı elemanlarını tara
        val elements = doc.select("a[href*=/anime/], a[href*=/dizi/], div.anime-card, div.poster-card, article, div[class*=card]")
        
        elements.forEach { element ->
            val linkNode = if (element.tagName() == "a") element else element.selectFirst("a[href]")
            val href = linkNode?.attr("href") ?: return@forEach
            
            if (href == "/anime" || href == "/animeler" || href.endsWith("/anime/")) return@forEach

            // Başlık ayıklama
            val title = element.selectFirst("h1, h2, h3, h4, .title, .name, [class*=title]")?.text()?.trim()
                ?: linkNode.attr("title").ifEmpty { null }
                ?: element.selectFirst("img")?.attr("alt")?.trim()
                ?: return@forEach

            if (title.length < 2) return@forEach

            // Görsel/Poster ayıklama (TMDB linkleri dahil)
            val imgNode = element.selectFirst("img")
            var poster = imgNode?.attr("src")?.ifEmpty { null }
                ?: imgNode?.attr("data-src")?.ifEmpty { null }
                ?: imgNode?.attr("srcset")?.substringBefore(" ")

            if (poster != null && poster.contains("canvas.openani.me/animecard?src=")) {
                poster = poster.substringAfter("src=").substringBefore("&")
            }

            items.add(newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrlNull(poster)
            })
        }
        
        return items.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()
        try {
            val res = app.get(mainUrl, headers = defaultHeaders)
            val doc = res.document

            val latestItems = extractAnimeFromDoc(doc)

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
            extractAnimeFromDoc(doc)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = defaultHeaders).document

        val title = doc.selectFirst("h1, .title, [class*=title]")?.text()?.trim() ?: "Anime"
        
        var poster = doc.selectFirst("img[src*=tmdb.org], img[src*=/poster/], .poster img")?.attr("src")
        if (poster != null && poster.contains("canvas.openani.me/animecard?src=")) {
            poster = poster.substringAfter("src=").substringBefore("&")
        }

        val description = doc.selectFirst(".description, .overview, .synopsis, p")?.text()?.trim()
        val genres = doc.select("a[href*=/tur/], a[href*=/genre/], .genre").map { it.text().trim() }

        val episodesList = ArrayList<Episode>()
        val epElements = doc.select("a[href*=/bolum], a[href*=-bolum-], a[href*=/izle/], .episode-item")

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
