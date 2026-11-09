package com.liferpg

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.liferpg.data.net.purgeLegacyClaudeKey
import com.liferpg.notif.Notificacoes

class LifeRpgApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        purgeLegacyClaudeKey(this)
        Graph.provide(this)
        Notificacoes.criarCanais(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .build()
}
