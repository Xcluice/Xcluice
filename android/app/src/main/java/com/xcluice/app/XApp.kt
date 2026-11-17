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
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .crossfade(120)
            .build()
}
