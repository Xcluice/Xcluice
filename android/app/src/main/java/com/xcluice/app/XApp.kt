package com.xcluice.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder

class XApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        com.xcluice.app.state.App.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()); add(com.xcluice.app.data.SbResizeInterceptor()) }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
