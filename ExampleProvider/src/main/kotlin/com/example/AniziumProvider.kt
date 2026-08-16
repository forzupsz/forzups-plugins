package com.example

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI

class AniziumProvider : MainAPI() {
    override var mainUrl = "https://anizium.com"
    override var name = "Anizium"
    override var hasMainPage = true
    override var lang = "tr"
    override var supportedTypes = setOf(TvType.Anime)
}
