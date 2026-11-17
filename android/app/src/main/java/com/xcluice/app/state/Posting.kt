package com.xcluice.app.state

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.xcluice.app.data.Api
import com.xcluice.app.data.Post
import com.xcluice.app.data.hostOf
import com.xcluice.app.data.isImageUrl
import com.xcluice.app.data.isLinkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class Blob(val bytes: ByteArray, val mime: String, val ext: String, val name: String)

class Draft(
    val title: String, val desc: String, val link: String?, val file: Uri?,
    val thumbUri: Uri?, val thumbText: String?, val thumbLink: String? = null
)

class EditDraft(
    val title: String, val desc: String, val link: String?, val newThumb: Uri?,
    val thumbText: String?, val keepThumb: Boolean, val rawLinkBad: Boolean, val thumbLink: String? = null
)

object Posting {
    suspend fun read(uri: Uri): Blob? = withContext(Dispatchers.IO) {
        try {
            val cr = App.appCtx.contentResolver
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val mime = cr.getType(uri) ?: "application/octet-stream"
            var name = ""
            cr.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) name = c.getString(i) ?: ""
            }
            val ext = name.substringAfterLast('.', "").ifBlank { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin" }
            Blob(bytes, mime, ext, name.ifBlank { "file.$ext" })
        } catch (e: Exception) { null }
    }

    fun nameOf(uri: Uri): String {
        var name = ""
        runCatching {
            App.appCtx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) name = c.getString(i) ?: ""
            }
        }
        return name
    }

    fun mimeOf(uri: Uri): String = App.appCtx.contentResolver.getType(uri) ?: ""

    private fun wrap(p: Paint, text: String, maxW: Float): List<String> {
        val lines = ArrayList<String>(); var line = ""
        for (w in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val t = if (line.isEmpty()) w else "$line $w"
            if (line.isNotEmpty() && p.measureText(t) > maxW) { lines.add(line); line = w } else line = t
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines
    }

    /** Bold condensed caps on a dark card, same look as the website's text thumbnail. */
    fun textThumb(text: String): Blob {
        val w = 960; val h = 540
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF171717.toInt())
        val t = text.trim().uppercase().take(90).ifBlank { "XCLUICE" }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F5F5.toInt(); typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        var size = 210f
        var lines: List<String> = emptyList()
        while (size >= 36f) {
            p.textSize = size
            lines = wrap(p, t, w * 0.86f)
            if (lines.size * size <= h * 0.72f && lines.all { p.measureText(it) <= w * 0.86f }) break
            size -= 3f
        }
        p.textSize = size
        val y0 = h / 2f - lines.size * size / 2f + size * 0.82f
        lines.forEachIndexed { i, l -> c.drawText(l, w / 2f, y0 + i * size, p) }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Blob(out.toByteArray(), "image/jpeg", "jpg", "text-thumb.jpg")
    }

    private suspend fun upload(blob: Blob, prefix: String): String? {
        val safe = blob.name.replace(Regex("[^a-zA-Z0-9.]"), "")
        return Api.upload("$prefix${System.currentTimeMillis()}_$safe", blob.mime, blob.bytes)
    }

    private suspend fun thumbBlob(uri: Uri?, text: String?): Blob? {
        if (uri != null) return compressImage(uri, 960, 75) ?: read(uri)
        if (!text.isNullOrBlank()) return textThumb(text)
        return null
    }

    class Prepared(val file: File, val mime: String, val name: String)

    private fun sizeOf(uri: Uri): Long {
        var n = -1L
        runCatching {
            App.appCtx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst() && i >= 0) n = c.getLong(i)
            }
        }
        return n
    }

    /** Downscale + re-encode a picture. Returns null if it can't be decoded or isn't smaller. */
    fun compressImage(uri: Uri, maxSide: Int, quality: Int): Blob? {
        val mime = mimeOf(uri)
        if (mime !in listOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) return null
        return try {
            val cr = App.appCtx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val rot = runCatching { cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } }.getOrNull() ?: 1
            val deg = when (rot) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            val big = maxOf(bmp.width, bmp.height)
            val sc = if (big > maxSide) maxSide.toFloat() / big else 1f
            if (deg != 0f || sc < 1f) {
                val m = Matrix().apply { postScale(sc, sc); postRotate(deg) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val alpha = mime == "image/png" && bmp.hasAlpha()
            val out = ByteArrayOutputStream()
            bmp.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, out)
            val orig = sizeOf(uri)
            if (orig in 1 until out.size().toLong() && sc >= 1f && deg == 0f) return null
            val ext = if (alpha) "png" else "jpg"
            Blob(out.toByteArray(), if (alpha) "image/png" else "image/jpeg", ext, nameOf(uri).substringBeforeLast('.', "image") + "." + ext)
        } catch (e: Throwable) { null }
    }

    private suspend fun copyToCache(uri: Uri, name: String): File? = withContext(Dispatchers.IO) {
        try {
            val f = File(App.appCtx.cacheDir, "up_${System.nanoTime()}_" + name.replace(Regex("[^a-zA-Z0-9.]"), ""))
            App.appCtx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o, 64 * 1024) } } ?: return@withContext null
            f
        } catch (e: Exception) { null }
    }

    /** H.264 720p re-encode for big videos. Falls back to null (use the original) on any problem. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun compressVideo(uri: Uri, out: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.Main) {
            kotlinx.coroutines.withTimeoutOrNull(10 * 60 * 1000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                    try {
                        val ctx = App.appCtx
                        val edited = androidx.media3.transformer.EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(uri))
                            .setEffects(androidx.media3.transformer.Effects(emptyList(), listOf(androidx.media3.effect.Presentation.createForHeight(720))))
                            .build()
                        val enc = androidx.media3.transformer.DefaultEncoderFactory.Builder(ctx)
                            .setRequestedVideoEncoderSettings(androidx.media3.transformer.VideoEncoderSettings.Builder().setBitrate(2_000_000).build())
                            .build()
                        var poll: kotlinx.coroutines.Job? = null
                        val t = androidx.media3.transformer.Transformer.Builder(ctx)
                            .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                            .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                            .setEncoderFactory(enc)
                            .addListener(object : androidx.media3.transformer.Transformer.Listener {
                                override fun onCompleted(c: androidx.media3.transformer.Composition, r: androidx.media3.transformer.ExportResult) { poll?.cancel(); if (cont.isActive) cont.resumeWith(Result.success(true)) }
                                override fun onError(c: androidx.media3.transformer.Composition, r: androidx.media3.transformer.ExportResult, e: androidx.media3.transformer.ExportException) { poll?.cancel(); if (cont.isActive) cont.resumeWith(Result.success(false)) }
                            }).build()
                        t.start(edited, out.absolutePath)
                        poll = App.scope.launch(Dispatchers.Main) {
                            val h = androidx.media3.transformer.ProgressHolder()
                            while (true) {
                                if (t.getProgress(h) != androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED) onProgress(h.progress / 100f)
                                kotlinx.coroutines.delay(250)
                            }
                        }
                        cont.invokeOnCancellation { poll.cancel(); runCatching { t.cancel() } }
                    } catch (e: Throwable) { if (cont.isActive) cont.resumeWith(Result.success(false)) }
                }
            } ?: false
        }

    private suspend fun prepare(uri: Uri, onProgress: (Float) -> Unit): Prepared? {
        val mime = mimeOf(uri)
        val name = nameOf(uri).ifBlank { "file" }
        if (mime.startsWith("image/")) {
            val b = withContext(Dispatchers.Default) { compressImage(uri, 1920, 82) }
            if (b != null) {
                val f = File(App.appCtx.cacheDir, "up_${System.nanoTime()}.${b.ext}")
                withContext(Dispatchers.IO) { f.writeBytes(b.bytes) }
                return Prepared(f, b.mime, b.name)
            }
        }
        if (mime.startsWith("video/") && sizeOf(uri) > 6L * 1024 * 1024) {
            val out = File(App.appCtx.cacheDir, "up_${System.nanoTime()}.mp4")
            if (compressVideo(uri, out, onProgress) && out.length() > 0 && out.length() < sizeOf(uri)) {
                return Prepared(out, "video/mp4", name.substringBeforeLast('.', name) + ".mp4")
            }
            runCatching { out.delete() }
        }
        val f = copyToCache(uri, name) ?: return null
        return Prepared(f, mime.ifBlank { "application/octet-stream" }, name)
    }

    private fun blobToFile(b: Blob): Prepared {
        val f = File(App.appCtx.cacheDir, "up_${System.nanoTime()}.${b.ext}")
        f.writeBytes(b.bytes)
        return Prepared(f, b.mime, b.name)
    }

    suspend fun publish(d: Draft): Boolean {
        val u = App.user ?: return false
        val files = ArrayList<File>()
        try {
            App.upStart("Preparing\u2026")
            var fileP: Prepared? = null; var thumbP: Prepared? = null
            var linkOut = d.link; var fileUrl: String? = null; var thumbUrl: String? = null
            var fileName = ""
            if (d.file != null) {
                fileP = prepare(d.file) { App.upProgress(it * 0.25f) } ?: run { App.upFail("Couldn't read that file"); return false }
                files.add(fileP.file); fileName = fileP.name
                if (d.thumbLink == null) thumbBlob(d.thumbUri, d.thumbText)?.let { thumbP = blobToFile(it).also { t -> files.add(t.file) } }
            } else if (d.link != null) {
                if (d.thumbLink == null) {
                    val tb = thumbBlob(d.thumbUri, d.thumbText) ?: textThumb(d.title.ifBlank { d.desc.ifBlank { hostOf(d.link) } })
                    thumbP = blobToFile(tb).also { files.add(it.file) }
                }
            }
            val total = ((fileP?.file?.length() ?: 0L) + (thumbP?.file?.length() ?: 0L)).coerceAtLeast(1L)
            var fSent = 0L; var tSent = 0L
            fun report() { App.upLabel = "Uploading\u2026"; App.upProgress(0.25f + 0.7f * ((fSent + tSent).toFloat() / total)) }
            report()
            val stamp = System.currentTimeMillis()
            val safe = { n: String -> n.replace(Regex("[^a-zA-Z0-9.]"), "") }
            val (fu, tu) = coroutineScope {
                val fj = async { fileP?.let { Api.uploadFile("${stamp}_${safe(it.name)}", it.mime, it.file) { n -> fSent = n; report() } } }
                val tj = async { thumbP?.let { Api.uploadFile("thumb_${stamp}_${safe(it.name)}", it.mime, it.file) { n -> tSent = n; report() } } }
                fj.await() to tj.await()
            }
            if (fileP != null && fu == null) { App.upFail("Upload failed. Check your connection"); return false }
            fileUrl = fu; thumbUrl = d.thumbLink ?: tu
            if (d.file == null && d.link != null) { fileUrl = d.link; linkOut = null }
            App.upProgress(0.97f)
            val fallback = if (d.link != null) hostOf(d.link) else ""
            val ok = Api.createPost(
                u, d.title.ifBlank { fileName.ifBlank { fallback.ifBlank { "Post" } } },
                d.desc.ifBlank { d.title.ifBlank { fallback } }, fileUrl ?: "", linkOut, thumbUrl
            )
            if (ok) { App.upDone("Published successfully"); App.loadPostsNow() } else App.upFail("Publish failed")
            return ok
        } catch (e: Throwable) {
            App.upFail("Upload failed"); return false
        } finally {
            files.forEach { runCatching { it.delete() } }
        }
    }

    private fun oldLinkOf(p: Post): String? = p.linkUrl?.takeIf { it.isNotBlank() } ?: p.fileUrl.takeIf { isLinkUrl(it) }

    suspend fun saveEdit(p: Post, e: EditDraft): Boolean {
        if (e.rawLinkBad) { App.toast("That link doesn't look right"); return false }
        if (e.title.isBlank() && e.desc.isBlank() && p.fileUrl.isBlank() && e.link == null) { App.toast("Add a title or description"); return false }
        val fileLike = p.hasRealFile
        val linkOnlyPost = !fileLike && isLinkUrl(p.fileUrl)
        val canThumb = (fileLike && !isImageUrl(p.fileUrl)) || linkOnlyPost
        val linkChanged = e.link != oldLinkOf(p)
        val patch = JSONObject()
            .put("title", e.title.ifBlank { p.title.ifBlank { "Post" } })
            .put("subject", e.desc.ifBlank { e.title.ifBlank { if (fileLike) "" else p.subject } })
        var thumbFinal: String? = null
        if (canThumb) {
            val nb = if (e.thumbLink != null) null else thumbBlob(e.newThumb, e.thumbText)
            if (e.thumbLink != null) thumbFinal = e.thumbLink
            else if (nb != null) thumbFinal = upload(nb, "thumb_") ?: run { App.toast("Upload failed"); return false }
            else {
                val ex = p.thumbUrl
                if (e.keepThumb && ex != null && !(linkChanged && Regex("/link_\\d+_").containsMatchIn(ex))) thumbFinal = ex
            }
        }
        if (linkChanged) {
            if (e.link != null) {
                if (!fileLike && thumbFinal == null) {
                    thumbFinal = upload(textThumb(e.title.ifBlank { e.desc.ifBlank { hostOf(e.link) } }), "thumb_")
                }
                if (!fileLike) { patch.put("file_url", e.link); patch.put("link_url", JSONObject.NULL) } else patch.put("link_url", e.link)
            } else {
                patch.put("link_url", JSONObject.NULL)
                if (!fileLike && isLinkUrl(p.fileUrl)) patch.put("file_url", "")
            }
        }
        if (canThumb) patch.put("thumb_url", thumbFinal ?: JSONObject.NULL)
        val ok = Api.patchPost(p.id, patch)
        if (ok) { App.toast("Post updated"); App.loadPostsNow() } else App.toast("Save failed: not allowed")
        return ok
    }
}
