package com.kerimmkirac

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

// manifest.json içinden doğrulandı:
// "pluginClassName": "com.kerimmkirac.AniziumPlugin", "name": "Anizium"
@CloudstreamPlugin
class AniziumPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Anizium())
    }
}
