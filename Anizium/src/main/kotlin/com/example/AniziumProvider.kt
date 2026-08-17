package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class AniziumProvider : MainAPI() {
    override var mainUrl = "https://anizium.com"
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<SearchResponse>()
        
        try {
            val webView = WebViewResolver()
            val requestUrl = mainUrl
            
            val response = app.get(requestUrl, interceptor = webView)
            val document = response.document

            document.select("a[href*=/anime/], a[href*=/bolum/], .anime-card, article").forEach { element ->
                val linkElement = if (element.tagName() == "a") element else element.selectFirst("a")
                val href = fixUrlNull(linkElement?.attr("href")) ?: return@forEach
                
                val title = element.selectFirst(".title, .name, h2, h3, h4")?.text()
                    ?: element.attr("title")
                    ?: linkElement?.text()
                    ?: ""

                val imgElement = element.selectFirst("img")
                val posterUrl = fixUrlNull(
                    imgElement?.attr("data-src")
                        ?: imgElement?.attr("src")
                )

                if (title.isNotBlank() && title.length > 2) {
                    items.add(newAnimeSearchResponse(title.trim(), href, TvType.Anime) {
                        this.posterUrl = posterUrl
                    })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(
            list = HomePageList("Son Eklenenler", items.distinctBy { it.url }),
            hasNext = false
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val webView = WebViewResolver()
        val document = app.get(url, interceptor = webView).document
        
        val title = document.selectFirst("h1, .title")?.text()?.trim() ?: "Anime"
        val poster = fixUrlNull(document.selectFirst("img.poster, .cover img, meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img.poster, .cover img")?.attr("src"))

        val episodes = ArrayList<Episode>()
        document.select("a[href*=/bolum/]").forEach { ep ->
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
