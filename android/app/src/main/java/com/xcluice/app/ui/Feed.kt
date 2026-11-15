package com.xcluice.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xcluice.app.data.FileType
import com.xcluice.app.data.Post
import com.xcluice.app.data.agoShort
import com.xcluice.app.data.defAvatar
import com.xcluice.app.data.isImageUrl
import com.xcluice.app.state.App
import kotlinx.coroutines.launch

private val FILTERS = listOf("All", "Previews", "Assets", "Demos")

@Composable
fun FeedTab() {
    val list = App.visiblePosts()
    val state = rememberLazyListState()
    val near by remember { derivedNear(state) }
    LaunchedEffect(near) { if (near) App.loadMore() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().background(Ink.copy(alpha = 0.96f)).statusBarsPadding()
                    .drawBehind { drawLine(Hair, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(top = 12.dp, bottom = 10.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Glyph(G.CLOSE, 15.dp, Color.Black, sw = 3.6f)
                    }
                    Spacer(Modifier.width(10.dp))
                    T("Xcluice", 22.sp, FontWeight.Black, modifier = Modifier.weight(1f), spacing = (-0.5).sp)
                    Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.refresh() }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) {
                        if (App.postsLoading) Spinner(20.dp) else Glyph(G.REFRESH, 18.dp, Color.White)
                    }
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (App.postsLoading) Spinner(34.dp) else T("No posts here yet.", 15.sp, color = Muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(list, key = { _, p -> p.id }) { i, p -> PostCard(p, i) }
                    if (App.postsMore) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Spinner(24.dp) } }
                }
            }
        }
        if (App.user != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 104.dp).enter(3)) {
                Box(Modifier.size(56.dp).pressable(true, 0.86f) { App.composing = true }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Glyph(G.PLUS, 28.dp, Color.Black, sw = 2.8f)
                }
            }
        }
    }
}

private fun derivedNear(state: androidx.compose.foundation.lazy.LazyListState) =
    androidx.compose.runtime.derivedStateOf {
        val li = state.layoutInfo
        val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
        li.totalItemsCount > 0 && last >= li.totalItemsCount - 3
    }

@Composable
private fun PostCard(p: Post, index: Int) {
    val liked = p.id in App.liked
    val heart = remember { Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().enter(index.coerceAtMost(6), 40.dp)
            .pressable(true, 0.97f) { App.openPost(p) }
            .clip(RoundedCornerShape(22.dp)).background(Glass2).border(1.dp, Hair, RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Panel)) {
            if (p.type == FileType.MEDIA && isImageUrl(p.fileUrl)) {
                AsyncImage(p.fileUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (p.fileUrl.isBlank()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    T(p.headline, 15.sp, FontWeight.Medium, Soft, maxLines = 4, align = TextAlign.Center, lineHeight = 21.sp)
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    T(when (p.type) { FileType.DOCS -> "ASSET"; FileType.HTML -> "DEMO"; FileType.APK -> "APK"; FileType.ARCHIVE -> "ARCHIVE"; else -> "FILE" }, 26.sp, FontWeight.Black, Color.White.copy(alpha = 0.22f), spacing = 4.sp)
                }
            }
            if (p.pinned) Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape).background(Color(0xCC000000)).padding(horizontal = 9.dp, vertical = 3.dp)) { T("PINNED", 9.sp, FontWeight.Bold, spacing = 1.sp) }
        }
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Avatar(p.authorPhoto, p.authorEmail.ifBlank { p.authorName }, 36.dp, Modifier.pressable(true, 0.88f) { App.openProfile(com.xcluice.app.data.Who(p.authorName, p.authorEmail, p.authorPhoto, null)) })
            Column(Modifier.weight(1f)) {
                T(p.headline, 15.sp, FontWeight.SemiBold, maxLines = 2, lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    T(p.authorName, 12.sp, FontWeight.Medium, Muted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (App.isVerified(null, p.authorEmail)) Tick(12.dp)
                    T("· " + agoShort(p.createdMs), 12.sp, color = Muted)
                }
            }
            Column(
                Modifier.pressable(true, 0.8f) {
                    App.toggleLike(p)
                    if (App.user != null) scope.launch { heart.snapTo(0.4f); heart.animateTo(1f, spring(0.3f, Spring.StiffnessMedium)) }
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Glyph(G.HEART, 22.dp, if (liked) Color(0xFFFF4D6D) else Muted, filled = liked, modifier = Modifier.graphicsLayer { scaleX = heart.value; scaleY = heart.value })
                if (p.likes > 0) T(p.likes.toString(), 11.sp, color = Muted)
            }
        }
    }
}

@Composable
fun PostViewer() {
    val p = App.viewing
    var last by remember { mutableStateOf<Post?>(null) }
    if (p != null) last = p
    BackHandler(p != null) { App.viewing = null }
    AnimatedVisibility(p != null, enter = slideInVertically(tween(380, easing = OutExpo)) { it / 3 } + fadeIn(tween(240)), exit = slideOutVertically(tween(240)) { it / 3 } + fadeOut(tween(200))) {
        val v = last ?: return@AnimatedVisibility
        val ctx = LocalContext.current
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.viewing = null }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.CLOSE, 18.dp, Color.White) }
                Avatar(v.authorPhoto, v.authorEmail.ifBlank { v.authorName }, 34.dp, Modifier.pressable(true, 0.88f) { App.viewing = null; App.openProfile(com.xcluice.app.data.Who(v.authorName, v.authorEmail, v.authorPhoto, null)) })
                Column(Modifier.weight(1f)) {
                    T(v.authorName, 15.sp, FontWeight.SemiBold, maxLines = 1)
                    T(agoShort(v.createdMs) + " ago", 12.sp, color = Muted)
                }
                if (App.user?.email?.equals(v.authorEmail, true) == true) {
                    Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.editing = v }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.EDIT, 18.dp, Color.White) }
                    Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.deletePost(v) }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.TRASH, 18.dp, Color.White) }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (v.type == FileType.MEDIA && isImageUrl(v.fileUrl)) {
                    AsyncImage(v.fileUrl, null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(16.dp))
                }
                T(v.headline, 20.sp, FontWeight.Bold, lineHeight = 27.sp)
                if (v.fileUrl.isNotBlank() && !(v.type == FileType.MEDIA && isImageUrl(v.fileUrl))) {
                    Spacer(Modifier.height(22.dp))
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).pressable(true, 0.96f) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.fileUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }.clip(CircleShape).background(Color.White),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Glyph(G.OPEN, 20.dp, Color.Black, sw = 2.6f); Spacer(Modifier.width(8.dp)); T("Open", 16.sp, FontWeight.Bold, Color.Black)
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun Composer() {
    val open = App.composing || App.editing != null
    BackHandler(open) { App.composing = false; App.editing = null }
    AnimatedVisibility(open, enter = slideInVertically(tween(360, easing = OutExpo)) { it } + fadeIn(), exit = slideOutVertically(tween(220)) { it } + fadeOut()) {
        val ed = remember { App.editing }
        var text by remember { mutableStateOf(ed?.subject ?: "") }
        var uri by remember { mutableStateOf<Uri?>(null) }
        val ctx = LocalContext.current
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) { detectTapGestures { App.composing = false; App.editing = null } }, contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().clip(RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp)).background(Panel2)
                    .pointerInput(Unit) { detectTapGestures { } }.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(if (ed != null) "Edit post" else "New post", 19.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(34.dp).pressable(true, 0.86f) { App.composing = false; App.editing = null }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.CLOSE, 16.dp, Color.White) }
                }
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    text, { text = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Glass2).padding(14.dp)) {
                            if (text.isEmpty()) T("What's the hook?", 16.sp, color = Muted)
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ed == null) Row(
                        Modifier.pressable(true, 0.92f) { pick.launch("*/*") }.clip(CircleShape).background(Glass).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) { Glyph(G.IMAGE, 18.dp, Color.White); T(if (uri == null) "Attach file" else "File attached", 14.sp, FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.height(46.dp).width(110.dp).pressable(text.isNotBlank() || uri != null, 0.94f) {
                            if (ed != null) { App.editPost(ed, text); App.editing = null; return@pressable }
                            var bytes: ByteArray? = null; var mime: String? = null; var ext: String? = null
                            uri?.let { u ->
                                bytes = runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull()
                                mime = ctx.contentResolver.getType(u) ?: "application/octet-stream"
                                ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
                            }
                            App.publish(text, bytes, mime, ext)
                            text = ""; uri = null; App.composing = false
                        }.clip(CircleShape).background(if (text.isNotBlank() || uri != null) Color.White else Glass),
                        contentAlignment = Alignment.Center
                    ) { T(if (ed != null) "Save" else "Post", 15.sp, FontWeight.Bold, if (text.isNotBlank() || uri != null) Color.Black else Muted) }
                }
            }
        }
    }
}
