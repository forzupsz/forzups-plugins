package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class AniziumProvider : MainAPI() {
    override var mainUrl = "https://anizium.com" // Sitenin güncel adresi (değiştiyse burayı güncelle)
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    // 1. ADIM: Ana sayfadaki son eklenen animeleri çekme
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<SearchResponse>()
        
        // Siteye istek atıp kaynak kodlarını (HTML) alıyoruz
        val document = app.get(mainUrl).document
        
        // JSoup ile HTML içinden animeleri seçeceğimiz kodlar buraya gelecek
        
        return newHomePageResponse(request.name, items)
    }
}
