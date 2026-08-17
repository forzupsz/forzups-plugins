package com.forzups

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class OpenAniPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(OpenAniProvider())
    }
}
