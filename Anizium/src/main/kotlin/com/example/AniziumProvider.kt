package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class AniziumProvider : MainAPI() {
    override var mainUrl = "https://anizium.com"
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    // Cloudflare duvarını takılmadan geçmek için mobil tarayıcı kimliği
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<SearchResponse>()
        
        try {
            // İsteği özel header bilgileriyle gönderiyoruz
            val response = app.get(mainUrl, headers = headers)
            val document = response.document

            // Sitedeki tüm bağlantıları tara
            document.select("a[href*=/anime/], a[href*=/bolum/], div.anime-card a, article a").forEach { element ->
                val href = fixUrlNull(element.attr("href")) ?: return@forEach
                val title = element.selectFirst(".title, .name, h2, h3, h4")?.text()
                    ?: element.attr("title")
                    ?: element.text()

                val imgElement = element.selectFirst("img") ?: element.parent()?.selectFirst("img")
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
        val document = app.get(url, headers = headers).document
        val title = document.selectFirst("h1, .title")?.text()?.trim() ?: "Anime"
        val poster = fixUrlNull(document.selectFirst("img.poster, .cover img")?.attr("src"))

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
