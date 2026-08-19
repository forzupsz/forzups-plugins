package com.forzups

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

    // Sitenin CloudStream / Koruma engellerini aşmak için gerekli başlıklar
    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "Accept" to "application/json, text/plain, */*"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = ArrayList<HomePageList>()
        try {
            val doc = app.get(mainUrl, headers = defaultHeaders).document

            val items = ArrayList<SearchResponse>()
            doc.select("a[href*=/anime/], a[href*=/izle/], div.anime-card, div.card").forEach { element ->
                val linkNode = if (element.tagName() == "a") element else element.selectFirst("a[href]")
                val href = linkNode?.attr("href") ?: return@forEach
                
                val title = element.selectFirst("h1, h2, h3, h4, .title, .name")?.text()?.trim()
                    ?: linkNode.attr("title").ifEmpty { null }
                    ?: element.selectFirst("img")?.attr("alt")?.trim()
                    ?: return@forEach

                val poster = element.selectFirst("img")?.let { img ->
                    img.attr("src").ifEmpty { img.attr("data-src") }
                }

                items.add(newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                })
            }

            if (items.isNotEmpty()) {
                homePageList.add(HomePageList("Son Eklenenler", items.distinctBy { it.url }))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(list = homePageList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val doc = app.get("$mainUrl/ara?q=${query.trim().replace(" ", "+")}", headers = defaultHeaders).document
            doc.select("a[href*=/anime/], div.card").mapNotNull { element ->
                val linkNode = if (element.tagName() == "a") element else element.selectFirst("a[href]")
                val href = linkNode?.attr("href") ?: return@mapNotNull null
                val title = element.selectFirst("h1, h2, h3, .title")?.text()?.trim() ?: return@mapNotNull null
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

        val title = doc.selectFirst("h1, .title")?.text()?.trim() ?: "Anime"
        val poster = doc.selectFirst("img[src*=tmdb.org], img[src*=/poster/], .poster img")?.attr("src")
        val description = doc.selectFirst(".description, .overview, p")?.text()?.trim()

        // Bölüm bağlantılarını ve ID bilgilerini çek
        val episodesList = ArrayList<Episode>()
        val epElements = doc.select("a[href*=/bolum], a[href*=/izle/], .episode-item")

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

            // 1. Sayfa kaynağında doğrudan aniziumserver mp4 linki veya ID var mı kontrol et
            val pageHtml = doc.html()
            
            // "aniziumserver" geçen tüm mp4 / video bağlantılarını yakala
            val serverDomains = listOf("f.aniziumserver.sbs", "r.aniziumserver.sbs", "x.aniziumserver.site", "a.aniziumserver.site")
            
            // HTML içindeki iframe veya video kaynaklarını tara
            doc.select("iframe, video, source").forEach { element ->
                val src = element.attr("src").ifEmpty { element.attr("data-src") }
                if (src.isNotEmpty()) {
                    loadExtractor(fixUrl(src), subtitleCallback, offsetCallback)
                    found = true
                }
            }

            // 2. Anizium'un doğrudan mp4 sunucularını dinamik kalite seçenekleriyle ekle
            serverDomains.forEach { domain ->
                if (pageHtml.contains(domain)) {
                    // Sayfa içindeki mp4 yolunu ayıkla
                    val regex = """https://$domain/([0-9a-zA-Z/_.-]+)""".toRegex()
                    regex.findAll(pageHtml).forEach { match ->
                        val matchedUrl = match.value
                        val baseUrl = matchedUrl.substringBeforeLast("/")
                        
                        listOf(
                            "2160p.original.mp4" to Qualities.P2160.value,
                            "1080p.original.mp4" to Qualities.P1080.value,
                            "1080p.mp4" to Qualities.P1080.value,
                            "720p.mp4" to Qualities.P720.value
                        ).forEach { (fileName, quality) ->
                            offsetCallback.invoke(
                                newExtractorLink(
                                    name = "Anizium",
                                    source = "Anizium Server ($domain)",
                                    url = "$baseUrl/$fileName",
                                    quality = quality,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "$mainUrl/"
                                }
                            )
                            found = true
                        }
                    }
                }
            }

            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
