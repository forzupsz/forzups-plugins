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
            val document = app.get(mainUrl).document
            
            // Sitedeki tüm bağlantıları kapsayacak geniş tarama
            document.select("a").forEach { element ->
                val href = fixUrlNull(element.attr("href")) ?: return@forEach
                
                // Sadece anime detay veya bölüm sayfalarına giden linkleri yakala
                if (href.contains("/anime/") || href.contains("/bolum/")) {
                    val title = element.selectFirst(".title, .name, h2, h3, h4, span")?.text()
                        ?: element.attr("title")
                        ?: element.selectFirst("img")?.attr("alt")
                        ?: element.text()

                    val imgElement = element.selectFirst("img") ?: element.parent()?.selectFirst("img")
                    val posterUrl = fixUrlNull(
                        imgElement?.attr("data-src")
                            ?: imgElement?.attr("src")
                            ?: imgElement?.attr("srcset")?.substringBefore(" ")
                    )

                    if (!title.isNullOrBlank() && title.length > 2) {
                        items.add(newAnimeSearchResponse(title.trim(), href, TvType.Anime) {
                            this.posterUrl = posterUrl
                        })
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val distinctItems = items.distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList("Son Eklenenler", distinctItems),
            hasNext = false
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1, .title, .anime-title")?.text()?.trim() ?: "Anime"
        val poster = fixUrlNull(
            document.selectFirst("img.poster, .cover img, meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("img.poster, .cover img, img")?.attr("src")
        )

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
