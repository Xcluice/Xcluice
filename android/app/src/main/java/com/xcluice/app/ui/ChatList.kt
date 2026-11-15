package com.xcluice.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.app.data.ChatUser
import com.xcluice.app.data.Msg
import com.xcluice.app.data.agoShort
import com.xcluice.app.data.parseMs
import com.xcluice.app.state.App

private data class ChatRow(
    val u: ChatUser, val last: Msg?, val lastTs: Long, val fromThem: Boolean,
    val unread: Boolean, val activeNow: Boolean, val act: Long
)

val Green = Color(0xFF2EE66B)

@Composable
fun ChatTab() {
    val me = App.user
    if (me == null) { ChatGate(); return }
    var last by remember { mutableStateOf<ChatUser?>(null) }
    val t = App.thread
    if (t != null) last = t
    BackHandler(t != null) { App.closeThread() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ChatList()
        AnimatedVisibility(
            visible = t != null,
            enter = slideInHorizontally(tween(360, easing = OutExpo)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(240))
        ) {
            last?.let { ChatThread(it) }
        }
    }
}

@Composable
private fun ChatGate() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.enter(0).padding(32.dp)) {
            Box(Modifier.size(84.dp).clip(CircleShape).border(1.5.dp, Hair, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(G.CHAT, 36.dp, Color.White)
            }
            Spacer(Modifier.height(20.dp))
            T("Chat lives behind a sign in", 20.sp, FontWeight.Bold, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            T("Create an account to talk with everyone on Xcluice.", 14.sp, color = Muted, align = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.height(52.dp).width(200.dp).pressable(true, 0.94f) { App.guest = false }
                    .clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { T("Sign in", 16.sp, FontWeight.Bold, Color.Black) }
        }
    }
}

@Composable
private fun ChatList() {
    val me = App.user ?: return
    var searchOpen by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(searchOpen) { if (searchOpen) runCatching { fr.requestFocus() } else q = "" }

    val users = App.chatUsers
    val msgs = App.messages
    val reads = App.readMap
    val bucket = App.tick / 15000L
    val rows = remember(users, msgs, reads, q, bucket) {
        val now = System.currentTimeMillis()
        users.filter { it.uid != me.id }.map { u ->
            var last: Msg? = null
            for (i in msgs.indices.reversed()) {
                val m = msgs[i]
                val to = m.toUid ?: continue
                if ((m.uid == me.id && to == u.uid) || (m.uid == u.uid && to == me.id)) { last = m; break }
            }
            val lastTs = last?.createdMs ?: 0L
            val fromThem = last != null && last.uid == u.uid
            val unread = fromThem && lastTs > (reads[u.uid] ?: 0L)
            val act = parseMs(u.lastActive)
            ChatRow(u, last, lastTs, fromThem, unread, u.lastActive != null && now - act < 60_000, act)
        }.filter { q.isBlank() || it.u.name.lowercase().contains(q.trim().lowercase()) }
            .sortedWith(compareByDescending<ChatRow> { it.unread }.thenByDescending { it.lastTs }.thenByDescending { it.act })
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // header
            Box(
                Modifier.fillMaxWidth().background(Ink.copy(alpha = 0.96f)).statusBarsPadding()
                    .drawBehind { drawLine(Hair, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(Modifier.align(Alignment.CenterStart).pressable(true, 0.88f) { App.tab = 2 }) {
                    Avatar(me.avatarUrl, me.email, 36.dp)
                }
                T("Chat", 19.sp, FontWeight.Bold, modifier = Modifier.align(Alignment.Center), spacing = (-0.2).sp)
                Box(
                    Modifier.align(Alignment.CenterEnd).size(36.dp).pressable(true, 0.86f) { searchOpen = !searchOpen }
                        .clip(CircleShape).background(Glass),
                    contentAlignment = Alignment.Center
                ) { Glyph(if (searchOpen) G.CLOSE else G.SEARCH, 19.dp, Color.White, sw = 2.3f) }
            }
            AnimatedVisibility(searchOpen, enter = expandVertically(tween(280)) + fadeIn(), exit = shrinkVertically(tween(220)) + fadeOut()) {
                Box(Modifier.fillMaxWidth().background(Ink.copy(alpha = 0.96f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    BasicTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr),
                        decorationBox = { inner ->
                            Row(
                                Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(19.dp)).background(Glass).padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Glyph(G.SEARCH, 17.dp, Muted)
                                Box(Modifier.weight(1f)) { if (q.isEmpty()) T("Search", 15.sp, color = Muted); inner() }
                            }
                        }
                    )
                }
            }

            if (!App.chatUsersLoaded) {
                val t = rememberInfiniteTransition(label = "sk")
                val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                Column(Modifier.graphicsLayer { alpha = a }) {
                    repeat(7) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(CircleShape).background(Glass))
                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Box(Modifier.height(14.dp).width(140.dp).clip(RoundedCornerShape(7.dp)).background(Glass))
                                Box(Modifier.height(11.dp).width(200.dp).clip(RoundedCornerShape(6.dp)).background(Glass2))
                            }
                        }
                    }
                }
            } else if (rows.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                    T(if (q.isNotBlank()) "No results for “$q”" else "No one else here yet.", 14.sp, color = Muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
                    items(rows, key = { it.u.uid }) { r -> ChatRowView(r, rows.indexOf(r)) }
                }
            }
        }

        // FAB
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 104.dp).enter(3)
        ) {
            Box(
                Modifier.size(54.dp).pressable(true, 0.88f) { searchOpen = true }.clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Glyph(G.CHATPLUS, 26.dp, Color.Black) }
        }
    }
}

@Composable
private fun ChatRowView(r: ChatRow, index: Int) {
    val u = r.u
    Row(
        Modifier.fillMaxWidth().enter(index.coerceAtMost(12), 18.dp)
            .pressable(true, 0.97f) { App.openThread(u) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            if (r.unread) {
                val t = rememberInfiniteTransition(label = "ring")
                val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ra")
                Box(Modifier.size(62.dp).graphicsLayer { alpha = a }.border(2.5.dp, Color.White, CircleShape))
            }
            Avatar(u.photo, u.email.ifBlank { u.name }, 54.dp)
            if (r.activeNow) {
                val t = rememberInfiniteTransition(label = "dot")
                val s by t.animateFloat(0.8f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ds")
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp)
                        .size(15.dp).graphicsLayer { scaleX = s; scaleY = s }
                        .border(2.5.dp, Color.Black, CircleShape).padding(2.5.dp).clip(CircleShape).background(Green)
                )
            }
        }
        Column(
            Modifier.weight(1f)
                .drawBehind { drawLine(Hair, Offset(0f, size.height + 8.dp.toPx()), Offset(size.width, size.height + 8.dp.toPx()), 1.dp.toPx()) }
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                T(u.name, 16.5f.sp, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (App.isVerified(u.uid, u.email)) Tick(14.dp)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val last = r.last
                if (last == null) {
                    if (r.activeNow) { Dot(7.dp, Green); T("Active now", 13.5f.sp, color = Muted, maxLines = 1) }
                    else T("Tap to chat", 13.5f.sp, color = Muted, maxLines = 1)
                } else {
                    val label = if (r.unread) "New Chat" else if (r.fromThem) "Received" else "Sent"
                    if (r.unread) Dot(8.dp, Color.White)
                    else Glyph(if (r.fromThem) G.CHAT else G.SEND, 14.dp, Muted, sw = 2.2f)
                    T(
                        label + if (last.isAudio) " · Voice" else "", 13.5f.sp,
                        if (r.unread) FontWeight.SemiBold else FontWeight.Normal,
                        if (r.unread) Color.White else Muted, maxLines = 1
                    )
                    T("·", 13.5f.sp, color = Muted)
                    T(agoShort(r.lastTs), 13.5f.sp, color = Muted, maxLines = 1)
                }
            }
        }
    }
}
