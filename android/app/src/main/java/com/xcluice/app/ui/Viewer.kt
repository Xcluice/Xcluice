package com.xcluice.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.xcluice.app.data.Api
import com.xcluice.app.data.FileType
import com.xcluice.app.data.Post
import com.xcluice.app.data.Who
import com.xcluice.app.data.agoShort
import com.xcluice.app.data.hostOf
import com.xcluice.app.data.isImageUrl
import com.xcluice.app.state.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PostViewer() {
    val p = App.viewing
    var last by remember { mutableStateOf<Post?>(null) }
    if (p != null) last = p
    BackHandler(p != null) { App.viewing = null }
    AnimatedVisibility(
        p != null,
        enter = slideInVertically(tween(380, easing = OutExpo)) { it / 3 } + fadeIn(tween(240)),
        exit = slideOutVertically(tween(240)) { it / 3 } + fadeOut(tween(200))
    ) {
        val v = last ?: return@AnimatedVisibility
        val ctx = LocalContext.current
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.viewing = null }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.CLOSE, 18.dp, Color.White) }
                Avatar(v.authorPhoto, v.authorEmail.ifBlank { v.authorName }, 34.dp, Modifier.pressable(true, 0.88f) { App.viewing = null; App.openProfile(Who(v.authorName, v.authorEmail, v.authorPhoto, null)) })
                Column(Modifier.weight(1f)) {
                    T(v.authorName, 15.sp, FontWeight.SemiBold, maxLines = 1)
                    T(agoShort(v.createdMs) + " ago", 12.sp, color = Muted)
                }
                if (App.canEdit(v)) {
                    Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.editing = v }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.EDIT, 18.dp, Color.White) }
                    Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.ask("Delete this post?", "Delete", true) { App.deletePost(v) } }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.TRASH, 18.dp, Color.White) }
                }
            }
            val isImg = isImageUrl(v.fileUrl)
            val isPdf = v.fileUrl.substringBefore('?').lowercase().endsWith(".pdf")
            when {
                v.hasRealFile && v.type == FileType.HTML -> {
                    Header(v, ctx)
                    HtmlFrame(v.fileUrl, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(16.dp)))
                }
                v.hasRealFile && isPdf -> {
                    Header(v, ctx)
                    PdfView(v.fileUrl, Modifier.weight(1f).fillMaxWidth())
                }
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    if (isImg) {
                        AsyncImage(v.fileUrl, null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)))
                        Spacer(Modifier.height(16.dp))
                    } else if (v.fileUrl.isNotBlank() && !v.thumbUrl.isNullOrBlank()) {
                        AsyncImage(v.thumbUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)))
                        Spacer(Modifier.height(16.dp))
                    }
                    if (v.title.isNotBlank() && v.title != v.subject) { T(v.title, 20.sp, FontWeight.Bold, lineHeight = 27.sp); Spacer(Modifier.height(8.dp)) }
                    if (v.subject.isNotBlank()) LinkText(v.subject, 16.sp, if (v.title.isNotBlank() && v.title != v.subject) Soft else Color.White, lineHeight = 24.sp)
                    v.linkUrl?.takeIf { it.isNotBlank() }?.let { l ->
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.pressable(true, 0.94f) { openUrl(ctx, l) }.clip(CircleShape).background(Glass).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Glyph(G.LINK, 16.dp, Color.White); T("Open link · " + hostOf(l), 13.sp, FontWeight.SemiBold)
                        }
                    }
                    if (v.hasRealFile && !isImg) {
                        Spacer(Modifier.height(22.dp))
                        BtnWhite("Download", Modifier.fillMaxWidth(), g = G.DOWNLOAD) { openUrl(ctx, v.fileUrl) }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(v: Post, ctx: android.content.Context) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        T(v.headline, 17.sp, FontWeight.Bold, maxLines = 2)
        v.linkUrl?.takeIf { it.isNotBlank() }?.let { l ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.pressable(true, 0.94f) { openUrl(ctx, l) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph(G.LINK, 14.dp, Soft); T("Open link · " + hostOf(l), 12.sp, FontWeight.SemiBold, Soft)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlFrame(url: String, modifier: Modifier) {
    val html by produceState<String?>(null, url) { value = Api.fetchText(url) ?: "" }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val h = html
        if (h == null) Spinner(30.dp, Color.Black)
        else AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    loadDataWithBaseURL(null, h, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.destroy() }
        )
    }
}

@Composable
private fun PdfView(url: String, modifier: Modifier) {
    val ctx = LocalContext.current
    var renderer by remember(url) { mutableStateOf<PdfRenderer?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val f = File(ctx.cacheDir, "pdf_${url.hashCode()}.pdf")
                if (!f.exists() || f.length() == 0L) { if (!Api.download(url, f)) { f.delete(); failed = true; return@withContext } }
                renderer = PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY))
            } catch (e: Exception) { failed = true }
        }
    }
    DisposableEffect(url) { onDispose { runCatching { renderer?.close() } } }
    val r = renderer
    Box(modifier) {
        when {
            failed -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                T("Couldn't show this PDF here.", 14.sp, color = Muted)
                Spacer(Modifier.height(14.dp))
                BtnWhite("Open", Modifier.padding(horizontal = 40.dp).fillMaxWidth(), g = G.OPEN) { openUrl(ctx, url) }
            }
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(30.dp) }
            else -> {
                val wPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp - 20.dp).roundToPx() }.coerceAtMost(1400)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items((0 until r.pageCount).toList(), key = { it }) { i -> PdfPage(r, i, wPx) }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(r: PdfRenderer, i: Int, wPx: Int) {
    val bmp by produceState<Bitmap?>(null, i) {
        value = withContext(Dispatchers.IO) {
            try {
                synchronized(r) {
                    val page = r.openPage(i)
                    val h = (wPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val b = Bitmap.createBitmap(wPx, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(android.graphics.Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    b
                }
            } catch (e: Exception) { null }
        }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
    else Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).background(Panel), contentAlignment = Alignment.Center) { Spinner(24.dp) }
}
