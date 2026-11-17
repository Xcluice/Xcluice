package com.xcluice.app.ui

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.xcluice.app.data.Story
import com.xcluice.app.data.Who
import com.xcluice.app.data.agoShort
import com.xcluice.app.state.App
import com.xcluice.app.state.Posting

/** Returns a function that opens the system photo/video picker and starts the story composer. */
@Composable
fun rememberStoryPicker(): () -> Unit {
    val l = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) App.storyDraft = u }
    return { if (App.user == null) App.toast("Sign in to add a story") else l.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

/** White ring = unseen story, grey ring = seen, none = no story. */
@Composable
fun StoryRing(uid: String?, size: Dp, content: @Composable () -> Unit) {
    val has = uid != null && App.storiesOf(uid).isNotEmpty()
    val unseen = has && App.hasUnseen(uid!!)
    val t = rememberInfiniteTransition(label = "ring")
    val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "a")
    Box(
        Modifier.size(size).then(
            if (has) Modifier.graphicsLayer { alpha = if (unseen) a else 1f }.border(if (unseen) 3.dp else 2.dp, if (unseen) Color.White else Color(0x66FFFFFF), CircleShape)
            else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.graphicsLayer { alpha = 1f }) { content() }
    }
}

@Composable
fun StoryTray() {
    val me = App.user ?: return
    val order = App.trayOrder()
    val pick = rememberStoryPicker()
    val others = order.filter { it != me.id }
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "me") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp).enter(0, 16.dp)) {
                Box(Modifier.size(66.dp).pressable(true, 0.9f) {
                    if (App.storiesOf(me.id).isNotEmpty()) App.openStories(order, 0, 0) else pick()
                }, contentAlignment = Alignment.Center) {
                    StoryRing(me.id, 66.dp) { Avatar(me.avatarUrl, me.email, if (App.storiesOf(me.id).isEmpty()) 62.dp else 54.dp) }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).pressable(true, 0.8f) { pick() }.clip(CircleShape).background(Color.White).border(2.dp, Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Glyph(G.PLUS, 12.dp, Color.Black, sw = 3.2f) }
                }
                Spacer(Modifier.height(5.dp))
                T("Your story", 11.5f.sp, FontWeight.Medium, Soft, maxLines = 1)
            }
        }
        itemsIndexed(others, key = { _, u -> u }) { i, uid ->
            val pr = App.resolve(Who("", "", null, uid))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp).enter(i + 1, 16.dp)) {
                Box(Modifier.size(66.dp).pressable(true, 0.9f) { App.openStories(order, order.indexOf(uid), 0) }, contentAlignment = Alignment.Center) {
                    StoryRing(uid, 66.dp) { Avatar(pr.photo, pr.email.ifBlank { pr.name }, 54.dp) }
                }
                Spacer(Modifier.height(5.dp))
                T(pr.name.substringBefore(' '), 11.5f.sp, FontWeight.Medium, Soft, maxLines = 1)
            }
        }
    }
}

@Composable
fun StoryViewer() {
    val q = App.storyQueue
    BackHandler(q != null) { App.closeStories() }
    AnimatedVisibility(q != null, enter = fadeIn(tween(200)) + scaleIn(tween(300, easing = OutExpo), 0.92f), exit = fadeOut(tween(160)) + scaleOut(tween(200), 0.95f)) {
        val queue = App.storyQueue ?: return@AnimatedVisibility
        val uid = queue.getOrNull(App.storyU) ?: return@AnimatedVisibility
        val list = App.storiesOf(uid)
        if (list.isEmpty()) { LaunchedEffect(uid) { nextUser() }; return@AnimatedVisibility }
        val idx = App.storyI.coerceIn(0, list.size - 1)
        val s = list[idx]
        key(s.id) { StoryPage(uid, list, idx, s) }
    }
}

private fun nextUser() {
    val q = App.storyQueue ?: return
    if (App.storyU < q.size - 1) { App.storyU++; App.storyI = 0 } else App.closeStories()
}

private fun next(count: Int) { if (App.storyI < count - 1) App.storyI++ else nextUser() }
private fun prev() { if (App.storyI > 0) App.storyI-- else if (App.storyU > 0) { App.storyU--; App.storyI = 0 } }

@Composable
private fun StoryPage(uid: String, list: List<Story>, idx: Int, s: Story) {
    val me = App.user
    val pr = App.resolve(Who("", "", null, uid))
    var paused by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var vv by remember { mutableStateOf<VideoView?>(null) }
    val isVideo = s.kind == "video"
    LaunchedEffect(s.id) { App.markSeen(s.id) }
    LaunchedEffect(paused, vv) { if (isVideo) { if (paused) vv?.pause() else if (ready) vv?.start() } }
    LaunchedEffect(s.id, ready) {
        if (!ready) return@LaunchedEffect
        var acc = 0L
        var lastT = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            if (!paused) acc += (now - lastT) / 1_000_000
            lastT = now
            val v = vv
            val frac = if (isVideo && v != null && v.duration > 0) v.currentPosition.toFloat() / v.duration else acc / 5000f
            progress = frac.coerceIn(0f, 1f)
            if (frac >= 1f) { next(list.size); break }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag > 160f) App.closeStories() }, onVerticalDrag = { _, d -> drag += d })
            }
            .pointerInput(s.id) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o -> if (o.x < size.width * 0.34f) prev() else next(list.size) }
                )
            }
    ) {
        if (isVideo) {
            AndroidView(
                factory = { c ->
                    VideoView(c).apply {
                        setVideoURI(Uri.parse(s.mediaUrl))
                        setOnPreparedListener { it.isLooping = false; ready = true; start() }
                        setOnCompletionListener { next(list.size) }
                        setOnErrorListener { _, _, _ -> App.toast("Couldn't play this story"); next(list.size); true }
                        vv = this
                    }
                },
                modifier = Modifier.fillMaxSize().align(Alignment.Center),
                onRelease = { it.stopPlayback() }
            )
        } else {
            AsyncImage(s.mediaUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), onSuccess = { ready = true }, onError = { App.toast("Couldn't load this story"); next(list.size) })
        }
        if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(34.dp) }
        Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0x66000000)).align(Alignment.TopCenter))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                list.forEachIndexed { i, _ ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color(0x55FFFFFF))) {
                        val f = if (i < idx) 1f else if (i == idx) progress else 0f
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.weight(1f).pressable(true, 0.95f) { App.closeStories(); App.openProfile(Who(pr.name, pr.email, pr.photo, pr.id.ifBlank { uid })) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Avatar(pr.photo, pr.email.ifBlank { pr.name }, 36.dp)
                    Column {
                        T(if (me != null && uid == me.id) "Your story" else pr.name, 15.sp, FontWeight.SemiBold, maxLines = 1)
                        T(agoShort(s.createdMs), 12.sp, color = Soft)
                    }
                }
                if (me != null && uid == me.id) {
                    Box(Modifier.size(38.dp).pressable(true, 0.86f) {
                        paused = true
                        App.ask("Delete this story?", "Delete", true) {
                            App.deleteStory(s) { if (App.storiesOf(uid).isEmpty()) nextUser() }
                        }
                    }.clip(CircleShape).background(Color(0x55000000)), contentAlignment = Alignment.Center) { Glyph(G.TRASH, 19.dp, Color.White) }
                }
                Box(Modifier.size(38.dp).pressable(true, 0.86f) { App.closeStories() }.clip(CircleShape).background(Color(0x55000000)), contentAlignment = Alignment.Center) { Glyph(G.CLOSE, 19.dp, Color.White) }
            }
        }
        s.caption?.takeIf { it.isNotBlank() }?.let { c ->
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x88000000)).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
                T(c, 16.sp, FontWeight.Medium, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 22.sp)
            }
        }
    }
}

@Composable
fun StoryComposer() {
    val u = App.storyDraft
    var last by remember { mutableStateOf<Uri?>(null) }
    if (u != null) last = u
    BackHandler(u != null) { App.storyDraft = null }
    AnimatedVisibility(u != null, enter = fadeIn(tween(200)) + scaleIn(tween(300, easing = OutExpo), 0.94f), exit = fadeOut(tween(150))) {
        val uri = last ?: return@AnimatedVisibility
        var caption by remember(uri) { mutableStateOf("") }
        var busy by remember(uri) { mutableStateOf(false) }
        val isVideo = remember(uri) { Posting.mimeOf(uri).startsWith("video/") }
        Column(Modifier.fillMaxSize().background(Color.Black).blockTouches().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("New story", 19.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.storyDraft = null }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.CLOSE, 17.dp, Color.White) }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel), contentAlignment = Alignment.Center) {
                if (isVideo) AndroidView(
                    factory = { c -> VideoView(c).apply { setVideoURI(uri); setOnPreparedListener { it.isLooping = true; start() } } },
                    modifier = Modifier.fillMaxSize(), onRelease = { it.stopPlayback() }
                ) else AsyncImage(uri, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            InputBox(caption, { caption = it }, "Add a caption…", maxLen = 200, minHeight = 48.dp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BtnGhost("Cancel", Modifier.weight(1f)) { App.storyDraft = null }
                BtnWhite(if (busy) "Sharing…" else "Share story", Modifier.weight(1f), enabled = !busy) {
                    busy = true
                    App.shareStory(uri, caption) { ok -> busy = false; if (ok) App.storyDraft = null }
                }
            }
        }
    }
}
