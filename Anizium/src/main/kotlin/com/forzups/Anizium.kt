package com.forzups

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.databind.JsonNode
import java.net.URLEncoder

class Anizium : MainAPI() {

    override var mainUrl = "https://anizium.co"
    private val apiUrl = "https://api.anizium.co"
    private val siteUrl = "https://x.anizium.co"

    override var name = "Anizium"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime)

    // Çalışan orijinal header & session konfigürasyonu
    private val apiHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Content-Type" to "application/json",
        "Origin" to siteUrl,
        "Referer" to "$siteUrl/",
        "cf-control" to "134e4c040840015655080906544d030f004b4c075b54500f",
        "site" to "main",
        "device" to "browser",
        "language" to "tr",
        "user-profile" to "null",
        "user-session" to "01570d01545f55510102040f525d390e00094f0556514e2d1d5201580100071e030350115a53534c05550f535143455c085c54101215",
        "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Chromium\";v=\"139\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\""
    )

    private fun text(node: JsonNode?, vararg keys: String): String? {
        if (node == null) return null
        for (key in keys) {
            val value = node.get(key)
            if (value != null && !value.isNull) {
                val result = value.asText()
                if (result.isNotBlank() && result != "null") {
                    return result
                }
            }
        }
        return null
    }

    private fun int(node: JsonNode?, vararg keys: String): Int? {
        if (node == null) return null
        for (key in keys) {
            val value = node.get(key)
            if (value != null && !value.isNull) {
                if (value.isInt || value.isLong || value.isNumber) {
                    return value.asInt()
                }
                value.asText().toIntOrNull()?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun array(node: JsonNode?, vararg keys: String): JsonNode? {
        if (node == null) return null
        for (key in keys) {
            val value = node.get(key)
            if (value != null && value.isArray) {
                return value
            }
        }
        return null
    }

    private fun unwrap(node: JsonNode?): JsonNode? {
        if (node == null) return null
        return if (node.has("data") && !node.get("data").isNull) {
            node.get("data")
        } else {
            node
        }
    }

    private fun parseAnimeList(list: JsonNode?): List<SearchResponse> {
        if (list == null || !list.isArray) {
            return emptyList()
        }

        return list.mapNotNull { anime ->
            val name = text(anime, "name_tr", "name", "title") ?: return@mapNotNull null
            val id = text(anime, "ID
