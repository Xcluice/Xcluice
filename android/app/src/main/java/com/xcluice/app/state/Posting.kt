package com.xcluice.app.state

import android.graphics.Bitmap
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
    val thumbUri: Uri?, val thumbText: String?
)

class EditDraft(
    val title: String, val desc: String, val link: String?, val newThumb: Uri?,
    val thumbText: String?, val keepThumb: Boolean, val rawLinkBad: Boolean
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
        val w = 1280; val h = 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF171717.toInt())
        val t = text.trim().uppercase().take(90).ifBlank { "XCLUICE" }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F5F5.toInt(); typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        var size = 280f
        var lines: List<String> = emptyList()
        while (size >= 48f) {
            p.textSize = size
            lines = wrap(p, t, w * 0.86f)
            if (lines.size * size <= h * 0.72f && lines.all { p.measureText(it) <= w * 0.86f }) break
            size -= 4f
        }
        p.textSize = size
        val y0 = h / 2f - lines.size * size / 2f + size * 0.82f
        lines.forEachIndexed { i, l -> c.drawText(l, w / 2f, y0 + i * size, p) }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Blob(out.toByteArray(), "image/png", "png", "text-thumb.png")
    }

    private suspend fun upload(blob: Blob, prefix: String): String? {
        val safe = blob.name.replace(Regex("[^a-zA-Z0-9.]"), "")
        return Api.upload("$prefix${System.currentTimeMillis()}_$safe", blob.mime, blob.bytes)
    }

    private suspend fun thumbBlob(uri: Uri?, text: String?): Blob? {
        if (uri != null) return read(uri)
        if (!text.isNullOrBlank()) return textThumb(text)
        return null
    }

    suspend fun publish(d: Draft): Boolean {
        val u = App.user ?: return false
        var fileUrl: String? = null; var thumbUrl: String? = null; var linkOut = d.link
        var fileName = ""
        if (d.file != null) {
            val b = read(d.file) ?: run { App.toast("Couldn't read that file"); return false }
            fileName = b.name
            fileUrl = upload(b, "") ?: run { App.toast("Upload failed"); return false }
            thumbBlob(d.thumbUri, d.thumbText)?.let { tb -> thumbUrl = upload(tb, "thumb_") }
        }
        if (d.link != null && d.file == null) {
            val tb = thumbBlob(d.thumbUri, d.thumbText) ?: textThumb(d.title.ifBlank { d.desc.ifBlank { hostOf(d.link) } })
            thumbUrl = upload(tb, "thumb_")
            fileUrl = d.link; linkOut = null
        }
        val fallback = if (d.link != null) hostOf(d.link) else ""
        val ok = Api.createPost(
            u, d.title.ifBlank { fileName.ifBlank { fallback.ifBlank { "Post" } } },
            d.desc.ifBlank { d.title.ifBlank { fallback } }, fileUrl ?: "", linkOut, thumbUrl
        )
        if (ok) { App.toast("Published successfully"); App.loadPostsNow() } else App.toast("Publish failed")
        return ok
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
            val nb = thumbBlob(e.newThumb, e.thumbText)
            if (nb != null) thumbFinal = upload(nb, "thumb_") ?: run { App.toast("Upload failed"); return false }
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
