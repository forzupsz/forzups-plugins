package com.kerimmkirac

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * ------------------------------------------------------------------
 *  DİKKAT — bu dosya elle yazılmadı.
 *  Anizium.cs3 içindeki classes.dex dosyası, doğrudan bir Python
 *  DEX parser'ı ile "decompile" edilerek (tam bytecode->kaynak kod
 *  çevirisi değil; string sabitleri, sınıf/alan/metot imzaları ve
 *  SourceDebugExtension (SMAP) satır haritası okunarak) yeniden
 *  kurulmuştur. SMAP kaydı orijinal dosyanın adının "Anizium.kt"
 *  olduğunu ve orijinal dosyanın 475 satır olduğunu doğruluyor.
 *
 *  Aşağıdaki her şey doğrulanmış (dex string tablosundan direkt
 *  okunmuş) veriler:
 *    - mainUrl, name, lang, mainPage girişleri (URL + başlık)
 *    - tüm HTTP header'ları (User-Agent, Origin, Referer, vs.)
 *    - tüm API endpoint'leri (/anime/get, /anime/similar,
 *      /anime/source, /page/catalog, /page/search, /page/last-
 *      added-episodes)
 *    - tüm JSON veri sınıflarının alan adları (AnimeData,
 *      AnimeDetails, Season, EpisodeData, SourceGroup, SourceItem,
 *      SubtitleData, PageInfo, vs.)
 *
 *  Bytecode'un TAM akışı (if/else dallanmaları, hangi alanın hangi
 *  sırayla okunduğu) satır satır geri kazanılmadı — o kısımlar
 *  CloudStream'in tipik "MainAPI" kalıbına göre YENİDEN YAZILDI.
 *  Yani mantık %95 doğru olmalı ama derleyip test etmeden
 *  güvenme; özellikle loadLinks() içindeki quality/type eşleştirmesi
 *  ve "cf-control" / "user-session" header değerleri (bunlar sabit
 *  kodlanmış, muhtemelen zaman aşımına uğrayan Cloudflare/oturum
 *  tokenleri) gözden geçirilmeli.
 * ------------------------------------------------------------------
 */
class Anizium : MainAPI() {
    override var mainUrl = "https://api.anizium.co"
    override var name = "Anizium"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    // Sitenin web arayüzü (Origin/Referer için)
    private val siteUrl = "https://x.anizium.co"

    override var headers = mapOf(
        "cf-control" to "134e4c040840015655080906544d030f004b4c075b54500f", // dex'te sabit kodlanmış — muhtemelen eski/zaman aşımlı, çalışmazsa siteden güncel değerle değiştir
        "sec-ch-ua-platform" to "\"Windows\"",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
        "accept" to "application/json, text/plain, */*",
        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Brave\";v=\"139\", \"Chromium\";v=\"139\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-gpc" to "1",
        "accept-language" to "tr-TR,tr;q=0.5",
        "origin" to siteUrl,
        "sec-fetch-site" to "same-site",
        "sec-fetch-mode" to "cors",
        "sec-fetch-dest" to "empty",
        "referer" to "$siteUrl/",
        "accept-encoding" to "identity",
        "priority" to "u=1, i",
        "user-profile" to "null",
        "user-session" to "01570d01545f55510102040f525d390e00094f0556514e2d1d5201580100071e030350115a53534c05550f535143455c085c54101215" // dex'te sabit kodlanmış — muhtemelen eski/zaman aşımlı, çalışmazsa siteden güncel değerle değiştir
    )

    override val mainPage = mainPageOf(
        "/page/last-added-episodes?page=" to "Yeni Bölümler",
        "/page/catalog?id=series&type=type&page=" to "Tüm Animeler",
        "/page/catalog?id=movie&type=type&page=" to "Tüm Filmler",
        "/page/catalog?id=trdub&type=sound_group&page=" to "Türkçe Dublaj",
    )

    // ---------------------------------------------------------------
    //  JSON MODELLERİ  (alan adları dex'ten doğrulandı)
    // ---------------------------------------------------------------

    @Serializable
    data class ApiResponse(
        val success: Boolean,
        val page: PageInfo? = null,
    )

    @Serializable
    data class PageInfo(
        val page: Int,
        val perPageItems: Int,
        val previousPage: Int? = null,
        val nextPage: Int? = null,
        val totalPages: Int,
        val data: List<AnimeData> = emptyList(),
    )

    @Serializable
    data class AnimeData(
        val id: String,
        val type: String? = null,
        val poster: String? = null,
        val name: String,
        val overview: String? = null,
        val imdbPoint: Double? = null,
        val genre: List<Genre>? = null,
    )

    @Serializable
    data class Genre(
        val id: String,
        val name: String,
    )

    // "Yeni Bölümler" sayfası ayrı bir response şeması kullanıyor
    @Serializable
    data class LatestEpisodeResponse(
        val success: Boolean,
        val page: LatestEpisodePage? = null,
    )

    @Serializable
    data class LatestEpisodePage(
        val data: List<LatestEpisodeData> = emptyList(),
    )

    @Serializable
    data class LatestEpisodeData(
        val id: String,
        val name: String,
        val poster: String? = null,
        val season: Int,
        val episode: Int,
    )

    // /anime/get?id=  -> detay sayfası
    @Serializable
    data class AnimeDetailsResponse(
        val success: Boolean,
        val data: AnimeDetails? = null,
    )

    @Serializable
    data class AnimeDetails(
        val id: String,
        val type: String? = null,
        val detailsBanner: String? = null,
        val name: String,
        val overview: String? = null,
        val releaseYear: Int? = null,
        val genre: List<Genre>? = null,
        val imdbPoint: Double? = null,
        val seasons: List<Season>? = null,
    )

    @Serializable
    data class Season(
        val id: String,
        val name: String,
        val number: Int,
        val episodes: List<EpisodeData> = emptyList(),
    )

    @Serializable
    data class EpisodeData(
        val id: String,
        val name: String? = null,
        val number: Int,
        val overview: String? = null,
        val bannerLink: String? = null,
    )

    // /anime/similar?id=
    @Serializable
    data class SimilarResponse(
        val success: Boolean,
        val data: List<AnimeData> = emptyList(),
    )

    // /anime/source?id=  -> izleme kaynakları
    @Serializable
    data class SourceResponse(
        val success: Boolean,
        val content: SourceContent? = null,
        val subtitles: List<SubtitleData> = emptyList(),
        val groups: List<SourceGroup> = emptyList(),
    )

    @Serializable
    data class SourceContent(
        val name: String? = null,
        val type: String? = null,
    )

    @Serializable
    data class SourceGroup(
        val platform: String? = null,
        val group: String? = null,
        val name: String? = null,
        val type: String? = null,
        val items: List<SourceItem> = emptyList(),
        val row: Int? = null,
    )

    @Serializable
    data class SourceItem(
        val link: String,
        val quality: Int? = null,
        val type: String? = null,
    )

    @Serializable
    data class SubtitleData(
        val group: String? = null,
        val name: String? = null,
        val link: String,
        val row: Int? = null,
    )

    // ---------------------------------------------------------------
    //  YARDIMCI: AnimeData -> SearchResponse
    // ---------------------------------------------------------------
    private fun AnimeData.toSearchResponse(): SearchResponse {
        val tvType = if (this.type == "movie") TvType.AnimeMovie else TvType.Anime
        return newAnimeSearchResponse(this.name, "$mainUrl/anime/get?id=${this.id}", tvType) {
            this.posterUrl = this@toSearchResponse.poster
        }
    }

    private fun LatestEpisodeData.toSearchResponse(): SearchResponse {
        return newAnimeSearchResponse(this.name, "$mainUrl/anime/get?id=${this.id}", TvType.Anime) {
            this.posterUrl = this@toSearchResponse.poster
        }
    }

    // ---------------------------------------------------------------
    //  ANA SAYFA
    // ---------------------------------------------------------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl${request.data}$page"
        val res = app.get(url, headers = headers).text

        return if (request.data.contains("last-added-episodes")) {
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<LatestEpisodeResponse>(res)
            val items = parsed.page?.data?.map { it.toSearchResponse() } ?: emptyList()
            newHomePageResponse(request.name, items, hasNext = false)
        } else {
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<ApiResponse>(res)
            val items = parsed.page?.data?.map { it.toSearchResponse() } ?: emptyList()
            val hasNext = parsed.page?.nextPage != null
            newHomePageResponse(request.name, items, hasNext = hasNext)
        }
    }

    // ---------------------------------------------------------------
    //  ARAMA
    // ---------------------------------------------------------------
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/page/search?value=$query"
        val res = app.get(url, headers = headers).text
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<ApiResponse>(res)
        return parsed.page?.data?.map { it.toSearchResponse() } ?: emptyList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // ---------------------------------------------------------------
    //  DETAY SAYFASI
    // ---------------------------------------------------------------
    override suspend fun load(url: String): LoadResponse {
        // url = "$mainUrl/anime/get?id=xxx" formatında geliyor (search/mainpage'den)
        val id = url.substringAfter("id=")
        val res = app.get("$mainUrl/anime/get?id=$id", headers = headers).text
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<AnimeDetailsResponse>(res)
        val details = parsed.data ?: throw ErrorLoadingException("Anime bulunamadı")

        val episodes = details.seasons?.flatMap { season ->
            season.episodes.map { ep ->
                newEpisode(data = "$mainUrl/anime/source?id=${ep.id}") {
                    this.name = ep.name
                    this.season = season.number
                    this.episode = ep.number
                    this.posterUrl = ep.bannerLink
                    this.description = ep.overview
                }
            }
        } ?: emptyList()

        return if (details.type == "movie") {
            newMovieLoadResponse(details.name, url, TvType.AnimeMovie, "$mainUrl/anime/source?id=${details.id}") {
                this.posterUrl = details.detailsBanner
                this.plot = details.overview
                this.year = details.releaseYear
                this.tags = details.genre?.map { it.name }
                this.rating = details.imdbPoint?.let { (it * 1000).toInt() }
            }
        } else {
            newAnimeLoadResponse(details.name, url, TvType.Anime) {
                this.posterUrl = details.detailsBanner
                this.plot = details.overview
                this.year = details.releaseYear
                this.tags = details.genre?.map { it.name }
                this.rating = details.imdbPoint?.let { (it * 1000).toInt() }
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }

    // ---------------------------------------------------------------
    //  İZLEME LİNKLERİ
    // ---------------------------------------------------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data = "$mainUrl/anime/source?id=xxx"
        val res = app.get(data, headers = headers).text
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<SourceResponse>(res)
        if (!parsed.success) return false

        parsed.groups.forEach { group ->
            group.items.forEach { item ->
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${group.platform ?: group.name ?: this.name}",
                        url = item.link,
                        type = if (item.link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$siteUrl/"
                        this.quality = item.quality ?: Qualities.Unknown.value
                    }
                )
            }
        }

        parsed.subtitles.forEach { sub ->
            subtitleCallback(
                SubtitleFile(
                    lang = sub.name ?: sub.group ?: "Türkçe",
                    url = sub.link
                )
            )
        }

        return parsed.groups.isNotEmpty()
    }
}
