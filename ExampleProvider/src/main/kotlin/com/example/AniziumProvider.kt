package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class AniziumProvider : MainAPI() {
    override var mainUrl = "https://anizium.com"
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    // 1. ANA SAYFA
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<SearchResponse>()
        
        val document = app.get(mainUrl).document
        
        document.select("div.poster-card, div.anime-card, .episodes-list .item, .anime-item").forEach { element ->
            val title = element.selectFirst(".title, .anime-name, h3, .name")?.text() ?: return@forEach
            val href = fixUrlNull(element.selectFirst("a")?.attr("href")) ?: return@forEach
            val posterUrl = fixUrlNull(
                element.selectFirst("img")?.attr("src") 
                    ?: element.selectFirst("img")?.attr("data-src")
            )

            items.add(newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            })
        }

        return newHomePageResponse(
            list = HomePageList("Son Eklenenler", items),
            hasNext = false
        )
    }

    // 2. DETAY SAYFASI
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1, .anime-details .title")?.text() ?: "Bilinmeyen Anime"
        val poster = fixUrlNull(document.selectFirst(".poster img, .anime-cover img")?.attr("src"))

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    // 3. VİDEO LİNKLERİ
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        return true
    }
}
