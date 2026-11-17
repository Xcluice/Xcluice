package com.xcluice.app.data

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.size.Dimension

/**
 * Asks Supabase Storage for a right-sized copy of public images (avatars, thumbnails) instead of the full original.
 * If the project doesn't support image transformations, the first failure turns this off and originals are used.
 */
class SbResizeInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val req = chain.request
        val url = req.data as? String
        val w = (chain.size.width as? Dimension.Pixels)?.px
        if (!enabled || url == null || w == null || !url.startsWith(PUBLIC) || !isRaster(url)) return chain.proceed(req)
        val bucket = when {
            w <= 160 -> 160
            w <= 400 -> 400
            w <= 800 -> 800
            else -> return chain.proceed(req)
        }
        val fast = RENDER + url.removePrefix(PUBLIC).substringBefore('?') + "?width=$bucket&quality=75"
        val res = chain.proceed(req.newBuilder().data(fast).build())
        if (res is ErrorResult) { enabled = false; return chain.proceed(req) }
        return res
    }

    private fun isRaster(u: String): Boolean {
        val p = u.substringBefore('?').lowercase()
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".webp")
    }

    companion object {
        @Volatile var enabled = true
        private const val PUBLIC = Cfg.SB + "/storage/v1/object/public/"
        private const val RENDER = Cfg.SB + "/storage/v1/render/image/public/"
    }
}
