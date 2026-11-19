package com.xcluice.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xcluice.app.data.ChatUser
import com.xcluice.app.data.Msg
import com.xcluice.app.data.Recorder
import com.xcluice.app.data.VoicePlayer
import com.xcluice.app.data.parseMs
import com.xcluice.app.state.App
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.time.format.TextStyle as JTextStyle

private sealed class TItem {
    abstract val key: String
    class Day(val label: String) : TItem() { override val key = "d$label" }
    class Head(val m: Msg, val isMe: Boolean, val first: Boolean) : TItem() { override val key = "h${m.id}" }
    class Bubble(val m: Msg, val isMe: Boolean) : TItem() { override val key = "m${m.id}" }
}

private val QUICK = listOf("❤️", "😂", "😮", "😢", "👍", "🔥")
private val ZONE: ZoneId get() = ZoneId.systemDefault()
private val CLOCK = DateTimeFormatter.ofPattern("h:mm a")

private fun fmtClock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZONE).format(CLOCK)

private fun dayLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate()
    val today = LocalDate.now(ZONE)
    val diff = ChronoUnit.DAYS.between(d, today)
    return when {
        diff <= 0 -> "Today"
        diff == 1L -> "Yesterday"
        diff < 7 -> d.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault())
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year != today.year) "d MMM yyyy" else "d MMM"))
    }
}

private fun fmtDur(s: Float): String {
    val t = s.toInt().coerceAtLeast(0)
    return "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
}

private fun barHeights(id: Long, n: Int = 30): List<Float> {
    var h = id.toString().fold(0L) { a, c -> (a * 31 + c.code) and 0xFFFFFFFFL }
    return List(n) {
        h = (h * 1664525L + 1013904223L) and 0xFFFFFFFFL
        (28 + ((h shr 8) % 72)).toFloat() / 100f
    }
}

@Composable
fun ChatThread(person: ChatUser) {
    val me = App.user ?: return
    remember(person.uid) { App.threadMessages().forEach { App.renderedIds.add(it.id) }; 0 }

    val msgs = App.threadMessages()
    val rx = App.reactions
    val items = remember(msgs) {
        val out = ArrayList<TItem>()
        var prev: Msg? = null
        var prevDay = ""
        for (m in msgs) {
            val day = Instant.ofEpochMilli(m.createdMs).atZone(ZONE).toLocalDate().toString()
            val newDay = day != prevDay
            val newGroup = prev == null || newDay || prev.uid != m.uid || (m.createdMs - prev.createdMs) > 5 * 60_000
            if (newDay) out.add(TItem.Day(dayLabel(m.createdMs)))
            val isMe = m.uid == me.id
            if (newGroup) out.add(TItem.Head(m, isMe, prev == null || newDay))
            out.add(TItem.Bubble(m, isMe))
            prev = m; prevDay = day
        }
        out
    }

    Box(Modifier.fillMaxSize().background(Color.Black).blockTouches()) {
        Column(Modifier.fillMaxSize()) {
            ThreadHeader(person)
            ThreadMessages(items, rx, msgs, Modifier.weight(1f))
            if (person.uid in App.blocked) {
                Row(
                    Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    T("You blocked ${person.name}", 14.sp, color = Muted, modifier = Modifier.weight(1f))
                    Box(Modifier.pressable(true, 0.92f) { App.unblockContact(person.uid, person.name) }.clip(CircleShape).background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp)) {
                        T("Unblock", 13.sp, FontWeight.Bold, Color.Black)
                    }
                }
            } else Composer(person)
        }
        MsgSheet()
    }
}

@Composable
private fun ThreadHeader(person: ChatUser) {
    val now = App.tick
    val u = App.chatUsers.firstOrNull { it.uid == person.uid }
    val age = if (u?.lastActive != null) System.currentTimeMillis() - parseMs(u.lastActive) else Long.MAX_VALUE
    val typing = System.currentTimeMillis() - (App.typingFrom[person.uid] ?: 0L) < 3200L
    val active = age < 60_000

    Row(
        Modifier.fillMaxWidth().background(Ink.copy(alpha = 0.96f)).statusBarsPadding()
            .drawBehind { drawLine(Hair, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).pressable(true, 0.86f) { App.closeThread() }.clip(CircleShape).background(Glass),
            contentAlignment = Alignment.Center
        ) { Glyph(G.BACK, 22.dp, Color.White, sw = 2.4f) }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Avatar(person.photo, person.email.ifBlank { person.name }, 38.dp, Modifier.pressable(true, 0.88f) { App.openProfile(com.xcluice.app.data.Who(person.name, person.email, person.photo, person.uid)) })
            if (active) {
                val t = rememberInfiniteTransition(label = "hd")
                val s by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s")
                Box(
                    Modifier.align(Alignment.BottomEnd).size(13.dp).graphicsLayer { scaleX = s; scaleY = s }
                        .border(2.dp, Color.Black, CircleShape).padding(2.dp).clip(CircleShape).background(Green)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                T(person.name, 17.sp, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false), spacing = (-0.1).sp)
                if (App.isVerified(person.uid, person.email)) Tick(14.dp)
            }
            Box(Modifier.height(16.dp)) {
                when {
                    typing -> TypingDots()
                    active -> T("Active now", 12.sp, color = Green)
                    age < 86_400_000L -> T("Active ${com.xcluice.app.data.agoShort(parseMs(u?.lastActive))} ago", 12.sp, color = Muted)
                }
            }
        }
        val isB = person.uid in App.blocked
        Box(
            Modifier.pressable(true, 0.9f) {
                if (isB) App.unblockContact(person.uid, person.name)
                else App.ask("Block ${person.name}? You won't get their messages or notifications.", "Block", true) { App.blockContact(person.uid, person.name) }
            }.clip(CircleShape).background(Glass).padding(horizontal = 12.dp, vertical = 8.dp)
        ) { T(if (isB) "Unblock" else "Block", 12.5f.sp, FontWeight.SemiBold, if (isB) Color.White else Muted) }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "ty")
    Row(verticalAlignment = Alignment.CenterVertically) {
        T("typing", 12.sp, FontWeight.SemiBold, Color(0xFF0FADFF))
        Spacer(Modifier.width(3.dp))
        for (i in 0 until 3) {
            val y by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1100, delayMillis = i * 150, easing = LinearEasing), RepeatMode.Restart), label = "d$i"
            )
            Box(
                Modifier.padding(start = 2.dp).size(4.dp)
                    .graphicsLayer {
                        val k = if (y < 0.3f) sin(y / 0.3f * PI.toFloat()) else 0f
                        translationY = -3.dp.toPx() * k
                        alpha = 0.4f + 0.6f * k
                    }
                    .clip(CircleShape).background(Color(0xFF0FADFF))
            )
        }
    }
}

@Composable
private fun ThreadMessages(
    items: List<TItem>, rx: List<com.xcluice.app.data.Reaction>, msgs: List<Msg>, modifier: Modifier
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var prevSize by remember { mutableIntStateOf(0) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val wasAtBottom = prevSize == 0 || lastVisible >= prevSize - 2
            val lastIsMine = (items.last() as? TItem.Bubble)?.isMe == true
            if (prevSize == 0) state.scrollToItem(items.lastIndex)
            else if (wasAtBottom || lastIsMine) state.animateScrollToItem(items.lastIndex)
        }
        prevSize = items.size
    }
    LaunchedEffect(imeBottom > 0) { if (items.isNotEmpty()) state.scrollToItem(items.lastIndex) }

    fun jumpTo(id: Long) {
        val idx = items.indexOfFirst { it is TItem.Bubble && it.m.id == id }
        if (idx < 0) { App.toast("Original message deleted"); return }
        scope.launch {
            state.animateScrollToItem(idx)
            App.flashId = id
            delay(1200)
            if (App.flashId == id) App.flashId = null
        }
    }

    val byId = remember(App.messages) { App.messages.associateBy { it.id } }
    val rxBy = remember(rx) { rx.groupBy { it.messageId } }

    if (items.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            T("No chats yet — say hi 👋", 14.sp, color = Muted, modifier = Modifier.enter(0))
        }
        return
    }

    LazyColumn(modifier.fillMaxWidth(), state = state) {
        itemsIndexed(items, key = { _, it -> it.key }) { _, it ->
            when (it) {
                is TItem.Day -> Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.clip(CircleShape).background(Glass).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        T(it.label, 12.sp, FontWeight.SemiBold, Muted)
                    }
                }
                is TItem.Head -> Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 3.dp),
                    horizontalArrangement = if (it.isMe) Arrangement.End else Arrangement.Start
                ) {
                    if (!it.isMe) {
                        T(it.m.name.uppercase(), 11.5f.sp, FontWeight.ExtraBold, Color(0xFF0FADFF), spacing = 0.6.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    T(fmtClock(it.m.createdMs), 11.sp, color = Color(0xFF6B6B70))
                }
                is TItem.Bubble -> BubbleRow(it.m, it.isMe, byId, rxBy[it.m.id].orEmpty(), me = App.user?.id ?: "") { jumpTo(it) }
            }
        }
        item(key = "pad") { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun BubbleRow(
    m: Msg, isMe: Boolean, byId: Map<Long, Msg>,
    rs: List<com.xcluice.app.data.Reaction>, me: String, onJump: (Long) -> Unit
) {
    val pop = remember { !App.renderedIds.contains(m.id) }
    val a = remember { Animatable(if (pop) 0f else 1f) }
    LaunchedEffect(Unit) {
        App.renderedIds.add(m.id)
        if (pop) a.animateTo(1f, spring(0.62f, 380f))
    }
    val haptic = LocalHapticFeedback.current
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
    val flash by animateColorAsState(if (App.flashId == m.id) Color(0x33FFFFFF) else Color.Transparent, tween(450), label = "fl")

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)
            .graphicsLayer {
                alpha = a.value.coerceIn(0f, 1f)
                val v = a.value
                translationY = (1f - v) * 14.dp.toPx()
                scaleX = 0.95f + 0.05f * v; scaleY = 0.95f + 0.05f * v
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (isMe) 1f else 0f, 1f)
            },
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Box(Modifier.widthIn(max = maxW)) {
                val shape = RoundedCornerShape(20.dp, 20.dp, if (isMe) 6.dp else 20.dp, if (isMe) 20.dp else 6.dp)
                Box(
                    Modifier
                        .clip(shape)
                        .background(if (isMe) Color.White else Glass)
                        .drawBehind { drawRect(flash) }
                        .alpha05(m.temp)
                        .pointerInput(m.id) {
                            detectTapGestures(
                                onDoubleTap = { App.react(m.id, QUICK[0]) },
                                onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); App.sheetMsg = m }
                            )
                        }
                        .padding(if (m.isAudio) PaddingForAudio else PaddingForText)
                ) {
                    Column {
                        val ro = m.replyTo
                        if (ro != null) {
                            val o = byId[ro]
                            Row(
                                Modifier.padding(bottom = 4.dp).height(IntrinsicSize.Min)
                                    .clip(RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp))
                                    .background(if (isMe) Color(0x29000000) else Glass2)
                                    .pointerInput(ro) { detectTapGestures(onTap = { onJump(ro) }) }
                            ) {
                                Box(Modifier.width(2.dp).fillMaxHeight().background(if (isMe) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)))
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    T(
                                        if (o == null) "" else if (o.uid == me) "ME" else o.name.uppercase(), 11.sp, FontWeight.Bold,
                                        if (isMe) Color(0xAA000000) else if (o?.uid == me) Color(0xFFF23C57) else Color(0xFF0FADFF), spacing = 0.4.sp, maxLines = 1
                                    )
                                    T(
                                        o?.preview?.take(120) ?: "Original message deleted", 13.sp,
                                        color = if (isMe) Color(0xCC000000) else Soft, maxLines = 1
                                    )
                                }
                            }
                        }
                        if (m.isAudio) VoiceBubble(m, isMe)
                        else if (firstUrl(m.text) != null) LinkText(m.text, 16.sp, if (isMe) Color.Black else Color.White, lineHeight = 22.sp, linkColor = if (isMe) Color.Black else Color.White)
                        else T(m.text, 16.sp, color = if (isMe) Color.Black else Color.White, lineHeight = 22.sp)
                    }
                }
                BurstLayer(m.id, Modifier.align(Alignment.Center))
            }
            if (rs.isNotEmpty()) {
                val counts = rs.groupingBy { it.emoji }.eachCount()
                val mine = rs.any { it.uid == me }
                Row(
                    Modifier.padding(horizontal = 8.dp).offset(y = (-6).dp).popIn()
                        .clip(CircleShape).background(if (mine) Color(0x38FFFFFF) else Glass)
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    counts.forEach { (e, c) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(e, 13.sp)
                            if (c > 1) T(c.toString(), 11.sp, color = Soft, modifier = Modifier.padding(start = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

private val PaddingForText = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 9.dp)
private val PaddingForAudio = androidx.compose.foundation.layout.PaddingValues(5.dp)

private fun Modifier.alpha05(temp: Boolean): Modifier = if (temp) this.graphicsLayer { alpha = 0.6f } else this

private fun Modifier.popIn(): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(0.45f, 520f)) }
    this.graphicsLayer { scaleX = a.value; scaleY = a.value; alpha = a.value.coerceIn(0f, 1f) }
}

@Composable
private fun BurstLayer(id: Long, modifier: Modifier) {
    val b = App.bursts[id] ?: return
    key(b.first) {
        val p = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            p.animateTo(1f, tween(900, easing = LinearEasing))
            if (App.bursts[id]?.first == b.first) App.bursts.remove(id)
        }
        Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val v = p.value
                for (i in 0 until 10) {
                    val ang = i / 10f * 2f * PI.toFloat()
                    val d = size.minDimension * 0.46f * (1f - (1f - v) * (1f - v))
                    drawCircle(
                        Confetti[i % Confetti.size].copy(alpha = (1f - v).coerceIn(0f, 1f)),
                        (4f * (1f - v) + 1f).dp.toPx(),
                        Offset(center.x + cos(ang) * d, center.y + sin(ang) * d)
                    )
                }
            }
            T(
                b.second, 46.sp,
                modifier = Modifier.graphicsLayer {
                    val v = p.value
                    val up = if (v < 0.25f) v / 0.25f else 1f
                    scaleX = 0.2f + 1.05f * up - 0.25f * ((v - 0.25f).coerceAtLeast(0f) / 0.75f)
                    scaleY = scaleX
                    translationY = -v * 80.dp.toPx()
                    alpha = if (v < 0.1f) v / 0.1f else (1f - ((v - 0.25f).coerceAtLeast(0f) / 0.75f))
                }
            )
        }
    }
}

@Composable
private fun VoiceBubble(m: Msg, isMe: Boolean) {
    val playing = VoicePlayer.playingId == m.id
    val bars = remember(m.id) { barHeights(m.id) }
    val shown = if (playing && VoicePlayer.seconds > 0f) fmtDur(VoicePlayer.seconds) else fmtDur(m.audioDur.toFloat())
    val fg = if (isMe) Color.Black else Color.White
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 10.dp)) {
        Box(
            Modifier.size(36.dp).pressable(true, 0.86f) { VoicePlayer.toggle(m.id, m.audioUrl) }
                .clip(CircleShape).background(if (isMe) Color.Black else Color.White),
            contentAlignment = Alignment.Center
        ) { Glyph(if (playing) G.PAUSE else G.PLAY, 16.dp, if (isMe) Color.White else Color.Black) }
        val phase = if (playing) {
            val t = rememberInfiniteTransition(label = "wv")
            val ph by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(700, easing = LinearEasing)), label = "ph")
            ph
        } else 0f
        Canvas(Modifier.width(150.dp).height(30.dp)) {
            val n = bars.size
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            val prog = if (playing) VoicePlayer.progress else 0f
            val k = (prog * n).toInt()
            for (i in 0 until n) {
                val pulse = if (playing) 0.82f + 0.18f * sin(phase + i * 0.7f) else 1f
                val h = bars[i] * size.height * pulse
                drawRoundRect(
                    if (i < k) fg else fg.copy(alpha = 0.32f),
                    Offset(i * (bw + gap), (size.height - h) / 2f), Size(bw, h), CornerRadius(bw / 2f, bw / 2f)
                )
            }
        }
        T(shown, 12.sp, color = if (isMe) Color(0xCC000000) else Soft, modifier = Modifier.width(34.dp), align = TextAlign.End)
    }
}

@Composable
private fun Composer(person: ChatUser) {
    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var secs by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val recorder = remember { Recorder(ctx) }
    DisposableEffect(Unit) { onDispose { recorder.stop(true) } }

    fun startRec() {
        if (recorder.start()) { recording = true; secs = 0 } else App.toast("Couldn't start recording")
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec() else App.toast("Microphone access denied")
    }
    LaunchedEffect(recording) {
        if (recording) while (true) { delay(1000); secs++ }
    }
    fun stopRec(cancel: Boolean) {
        recording = false
        val r = recorder.stop(cancel)
        if (r != null) App.sendVoice(r.first, r.second)
    }
    fun send() { App.sendText(text); text = "" }

    val ring by animateColorAsState(if (focused) Color(0x73FFFFFF) else Color.Transparent, tween(200), label = "ring")

    Column(
        Modifier.fillMaxWidth().background(Ink.copy(alpha = 0.96f))
            .drawBehind { drawLine(Hair, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding().imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
    ) {
        AnimatedVisibility(App.reply != null, enter = expandVertically(tween(220)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
            val r = App.reply
            Row(
                Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp).fillMaxWidth().height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)).background(Glass2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Color.White))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    T("Replying to " + (if (r?.uid == App.user?.id) "ME" else (r?.name ?: "")), 11.sp, FontWeight.Bold, Color.White, maxLines = 1)
                    T(r?.preview?.take(90) ?: "", 13.sp, color = Soft, maxLines = 1)
                }
                Box(
                    Modifier.padding(end = 8.dp).size(30.dp).pressable(true, 0.85f) { App.reply = null }.clip(CircleShape).background(Glass),
                    contentAlignment = Alignment.Center
                ) { Glyph(G.CLOSE, 14.dp, Color.White, sw = 2.6f) }
            }
        }
        AnimatedContent(
            targetState = recording,
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(120)) },
            label = "composer"
        ) { rec ->
            if (!rec) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it; App.onTypingInput() },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        maxLines = 5,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Default),
                        modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            Box(
                                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(Glass)
                                    .border(2.dp, ring, RoundedCornerShape(21.dp)).padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (text.isEmpty()) T("Send a chat", 16.sp, color = Muted)
                                inner()
                            }
                        }
                    )
                    AnimatedContent(
                        targetState = text.isNotBlank(),
                        transitionSpec = { (scaleIn(spring(0.5f, Spring.StiffnessMedium), 0.3f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.3f) + fadeOut(tween(100))) },
                        label = "action"
                    ) { has ->
                        if (has) {
                            Box(
                                Modifier.size(42.dp).pressable(true, 0.86f) { send() }.clip(CircleShape).background(Color.White),
                                contentAlignment = Alignment.Center
                            ) { Glyph(G.SEND, 19.dp, Color.Black, sw = 2.4f) }
                        } else {
                            Box(
                                Modifier.size(42.dp).pressable(true, 0.86f) {
                                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec()
                                    else perm.launch(Manifest.permission.RECORD_AUDIO)
                                }.clip(CircleShape).background(Glass),
                                contentAlignment = Alignment.Center
                            ) { Glyph(G.MIC, 20.dp, Color.White) }
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(Glass2).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val t = rememberInfiniteTransition(label = "rec")
                    val s by t.animateFloat(0.7f, 1.25f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "rs")
                    Box(Modifier.size(10.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(Color(0xFFFF3B4E)))
                    Spacer(Modifier.width(12.dp))
                    T(fmtDur(secs.toFloat()), 15.sp, FontWeight.SemiBold, Color.White, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(38.dp).pressable(true, 0.85f) { stopRec(true) }.clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Glyph(G.TRASH, 19.dp, Soft) }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.size(38.dp).pressable(true, 0.85f) { stopRec(false) }.clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Glyph(G.SEND, 18.dp, Color.Black, sw = 2.4f) }
                }
            }
        }
    }
}

@Composable
private fun MsgSheet() {
    val m = App.sheetMsg
    var shown by remember { mutableStateOf<Msg?>(null) }
    if (m != null) shown = m
    val clip = LocalClipboardManager.current
    AnimatedVisibility(m != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
        val s = shown
        if (s != null) {
            val mine = s.uid == App.user?.id
            val my = App.reactions.firstOrNull { it.messageId == s.id && it.uid == App.user?.id }?.emoji
            val p = remember(s.id) { Animatable(0f) }
            LaunchedEffect(s.id) { p.animateTo(1f, spring(0.78f, 420f)) }
            Box(
                Modifier.fillMaxSize().background(Color(0x8C000000)).pointerInput(Unit) { detectTapGestures { App.sheetMsg = null } },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)
                        .widthIn(max = 380.dp).fillMaxWidth()
                        .graphicsLayer {
                            translationY = (1f - p.value) * 50.dp.toPx()
                            alpha = p.value.coerceIn(0f, 1f)
                            scaleX = 0.96f + 0.04f * p.value; scaleY = scaleX
                        }
                        .clip(RoundedCornerShape(24.dp)).background(Panel2.copy(alpha = 0.96f))
                        .border(1.dp, Hair, RoundedCornerShape(24.dp))
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        QUICK.forEach { e ->
                            Box(
                                Modifier.size(46.dp).pressable(true, 0.8f) { App.sheetMsg = null; App.react(s.id, e) }
                                    .clip(CircleShape).background(if (e == my) Color(0x38FFFFFF) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) { T(e, 28.sp) }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
                    SheetAction(G.REPLY, "Reply") { App.sheetMsg = null; App.reply = s }
                    if (!s.isAudio) SheetAction(G.COPY, "Copy") { clip.setText(AnnotatedString(s.text)); App.sheetMsg = null; App.toast("Copied") }
                    if (mine) SheetAction(G.TRASH, "Delete for everyone") { App.sheetMsg = null; App.ask("Delete this message for everyone? It disappears from every chat and can't be undone.", "Delete", true) { App.deleteMessage(s) } }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(g: G, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(true, 0.97f, onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Glyph(g, 21.dp, Color.White)
        T(label, 16.sp, FontWeight.SemiBold)
    }
}
