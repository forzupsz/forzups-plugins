package com.forzups

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AniziumPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Anizium())
    }
}
