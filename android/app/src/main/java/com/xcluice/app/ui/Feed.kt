package com.xcluice.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xcluice.app.data.FileType
import com.xcluice.app.data.Post
import com.xcluice.app.data.Who
import com.xcluice.app.data.agoShort
import com.xcluice.app.data.hostOf
import com.xcluice.app.data.isImageUrl
import com.xcluice.app.data.isLinkUrl
import com.xcluice.app.state.App
import kotlinx.coroutines.launch

fun num(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1f", n / 1_000_000.0).removeSuffix(".0") + "M"
    n >= 1_000 -> String.format("%.1f", n / 1_000.0).removeSuffix(".0") + "K"
    else -> n.toString()
}

fun kindLabel(p: Post): String = when {
    p.isShot -> "LINK"
    p.fileUrl.isBlank() -> if (p.link != null) "LINK" else "POST"
    isLinkUrl(p.fileUrl) -> "LINK"
    else -> when (p.type) {
        FileType.MEDIA -> "PREVIEW"; FileType.DOCS -> "ASSET"; FileType.HTML -> "DEMO"
        FileType.APK -> "APK"; FileType.ARCHIVE -> "ARCHIVE"; else -> "FILE"
    }
}

@Composable
fun PostThumb(p: Post, modifier: Modifier = Modifier, small: Boolean = false) {
    Box(modifier.background(Panel)) {
        val img = p.thumbUrl?.takeIf { it.isNotBlank() } ?: p.fileUrl.takeIf { isImageUrl(it) }
        when {
            img != null -> AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            p.fileUrl.isBlank() -> Box(Modifier.fillMaxSize().padding(if (small) 8.dp else 20.dp), contentAlignment = Alignment.Center) {
                T(p.headline, if (small) 11.sp else 15.sp, FontWeight.Medium, Soft, maxLines = if (small) 4 else 4, align = TextAlign.Center, lineHeight = if (small) 14.sp else 21.sp)
            }
            isLinkUrl(p.fileUrl) -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Glyph(G.LINK, if (small) 22.dp else 40.dp, Color.White.copy(alpha = 0.7f))
                if (!small) { Spacer(Modifier.height(8.dp)); T(hostOf(p.fileUrl), 12.sp, FontWeight.SemiBold, Muted, maxLines = 1) }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                T(kindLabel(p), if (small) 12.sp else 26.sp, FontWeight.Black, Color.White.copy(alpha = 0.22f), spacing = if (small) 1.sp else 4.sp)
            }
        }
    }
}

@Composable
fun FeedTab() {
    val list = App.visiblePosts()
    val state = rememberLazyListState()
    val near by remember { nearEnd(state) }
    LaunchedEffect(near) { if (near) App.loadMore() }
    var searchOpen by remember { androidx.compose.runtime.mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().background(Ink.copy(alpha = 0.96f)).statusBarsPadding()
                    .drawBehind { drawLine(Hair, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(vertical = 12.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Glyph(G.CLOSE, 15.dp, Color.Black, sw = 3.6f)
                    }
                    Spacer(Modifier.size(10.dp))
                    T("Xcluice", 22.sp, FontWeight.Black, modifier = Modifier.weight(1f), spacing = (-0.5).sp)
                    Box(Modifier.size(36.dp).pressable(true, 0.86f) { searchOpen = !searchOpen; if (!searchOpen) App.search = "" }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) {
                        Glyph(if (searchOpen) G.CLOSE else G.SEARCH, 18.dp, Color.White)
                    }
                }
                AnimatedVisibility(searchOpen, enter = expandVertically(tween(260)) + fadeIn(), exit = shrinkVertically(tween(200)) + fadeOut()) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) { InputBox(App.search, { App.search = it }, "Search posts", minHeight = 44.dp) }
                }
            }
            PullRefresh(App.showSkeleton, { App.refresh() }, Modifier.fillMaxSize()) {
                if (App.showSkeleton || (list.isEmpty() && App.postsLoading)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(4) { SkeletonCard(it) }
                    }
                } else if (list.isEmpty()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val err = App.postsError
                        if (err != null && App.search.isBlank()) {
                            T(err, 17.sp, FontWeight.Bold, align = TextAlign.Center)
                            Spacer(Modifier.size(8.dp))
                            T("Couldn't load posts. Swipe down to try again.", 14.sp, color = Muted, align = TextAlign.Center)
                        } else T(if (App.search.isNotBlank()) "No matches." else "No posts here yet.", 15.sp, color = Muted)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        App.postsError?.let { e -> item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Glass).padding(12.dp)) { T("$e \u00B7 showing saved posts. Swipe down to retry.", 13.sp, color = Soft) } } }
                        itemsIndexed(list, key = { _, p -> p.id }) { i, p -> PostCard(p, i) }
                        if (App.postsMore && App.search.isBlank()) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Spinner(24.dp) } }
                    }
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

private fun nearEnd(state: LazyListState) = derivedStateOf {
    val li = state.layoutInfo
    val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
    li.totalItemsCount > 0 && last >= li.totalItemsCount - 3
}

@Composable
private fun PostCard(p: Post, index: Int) {
    val ctx = LocalContext.current
    val liked = p.id in App.liked
    val heart = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().enter(index.coerceAtMost(6), 40.dp)
            .clip(RoundedCornerShape(22.dp)).background(Glass2).border(1.dp, Hair, RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).pressable(true, 0.98f) { App.open(p, ctx) }) {
            PostThumb(p, Modifier.fillMaxSize())
            if (p.pinned) Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape).background(Color(0xCC000000)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Glyph(G.PIN, 11.dp, Color.White, filled = true); T("PINNED", 9.sp, FontWeight.Bold, spacing = 1.sp) }
            }
            val l = p.linkUrl?.takeIf { it.isNotBlank() }
            if (l != null) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).widthIn(max = 200.dp).clip(CircleShape).background(Color(0xB3000000)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Glyph(G.LINK, 11.dp, Color.White); T(hostOf(l), 10.sp, FontWeight.SemiBold, maxLines = 1) }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(CircleShape).background(Color(0xB3000000)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                T(kindLabel(p), 9.sp, FontWeight.Bold, spacing = 1.sp)
            }
        }
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Avatar(p.authorPhoto, p.authorEmail.ifBlank { p.authorName }, 36.dp, Modifier.pressable(true, 0.88f) { App.openProfile(Who(p.authorName, p.authorEmail, p.authorPhoto, null)) })
            Column(Modifier.weight(1f).pressable(true, 0.98f) { App.open(p, ctx) }) {
                T(p.headline, 15.sp, FontWeight.SemiBold, maxLines = 2, lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    T(p.authorName, 12.sp, FontWeight.Medium, Muted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (App.isVerified(null, p.authorEmail)) Tick(12.dp)
                    T("· " + agoShort(p.createdMs), 12.sp, color = Muted)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(num(p.likes), G.HEART, {
                App.toggleLike(p)
                if (App.user != null) scope.launch { heart.snapTo(0.4f); heart.animateTo(1f, spring(0.3f, Spring.StiffnessMedium)) }
            }, on = liked, modifier = Modifier.graphicsLayer { scaleX = heart.value; scaleY = heart.value })
            Pill(num(p.taps), G.EYE, null)
            p.link?.let { l -> Pill("Open link", G.LINK, { openUrl(ctx, l) }) }
            if (p.hasRealFile) Pill("Download", G.DOWNLOAD, { openUrl(ctx, p.fileUrl) })
            if (App.canEdit(p)) Pill("Edit", G.EDIT, { App.editing = p })
            if (App.isAdmin()) Pill(if (p.pinned) "Unpin" else "Pin", G.PIN, { App.togglePin(p) })
            if (App.canEdit(p)) Pill("Delete", G.TRASH, { App.ask("Delete this post?", "Delete", true) { App.deletePost(p) } })
        }
    }
}


@Composable
private fun SkeletonCard(index: Int) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.35f, 0.9f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(850, delayMillis = index * 90), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass2).border(1.dp, Hair, RoundedCornerShape(22.dp)).graphicsLayer { alpha = a }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Glass))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Glass))
                Box(Modifier.height(13.dp).width(120.dp).clip(RoundedCornerShape(7.dp)).background(Glass))
            }
            Box(Modifier.height(15.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(7.dp)).background(Glass))
            Box(Modifier.height(11.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(6.dp)).background(Glass2))
        }
    }
}

/** Swipe-down-to-refresh for any scrollable content (no extra dependency). */
@Composable
fun PullRefresh(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var pull by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    val busy by androidx.compose.runtime.rememberUpdatedState(refreshing)
    val onRef by androidx.compose.runtime.rememberUpdatedState(onRefresh)
    val conn = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (pull > 0f && available.y < 0f) { val c = maxOf(available.y, -pull); pull += c; return Offset(0f, c) }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.Drag && available.y > 0f && !busy) {
                    dragging = true
                    pull = (pull + available.y * 0.5f).coerceAtMost(threshold * 1.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val had = pull > 0f
                if (pull >= threshold && !busy) onRef()
                dragging = false; pull = 0f
                return if (had) available else androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    val shown by androidx.compose.animation.core.animateFloatAsState(if (dragging) pull else if (busy) threshold * 0.55f else 0f, androidx.compose.animation.core.spring(stiffness = 500f), label = "pull")
    Box(modifier.androidx_nested(conn)) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).graphicsLayer { alpha = (shown / threshold).coerceIn(0f, 1f); scaleX = 0.6f + 0.4f * (shown / threshold).coerceIn(0f, 1f); scaleY = scaleX }) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) {
                if (busy || pull >= threshold) Spinner(18.dp) else Glyph(G.REFRESH, 16.dp, Color.White)
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = shown }) { content() }
    }
}

private fun Modifier.androidx_nested(c: androidx.compose.ui.input.nestedscroll.NestedScrollConnection): Modifier =
    this.then(Modifier.nestedScroll(c))
