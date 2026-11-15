package com.xcluice.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xcluice.app.data.Api
import com.xcluice.app.data.Post
import com.xcluice.app.data.Who
import com.xcluice.app.data.agoShort
import com.xcluice.app.data.isImageUrl
import com.xcluice.app.state.App

@Composable
fun ProfileTab() {
    val u = App.user
    if (u == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(Modifier.enter(0), horizontalAlignment = Alignment.CenterHorizontally) {
                T("You're browsing as a guest", 20.sp, FontWeight.Bold, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                T("Sign in to post, like and chat.", 14.sp, color = Muted)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.height(52.dp).width(200.dp).pressable(true, 0.94f) { App.guest = false }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    T("Sign in", 16.sp, FontWeight.Bold, Color.Black)
                }
            }
        }
    } else {
        ProfileBody(Who(u.displayName, u.email, u.avatarUrl, u.id), own = true, onClose = null)
    }
}

@Composable
fun ProfileOverlay() {
    val w = App.profile
    var last by remember { mutableStateOf<Who?>(null) }
    if (w != null) last = w
    BackHandler(w != null) { App.profile = null }
    AnimatedVisibility(w != null, enter = slideInHorizontally(tween(360, easing = OutExpo)) { it } + fadeIn(tween(200)), exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))) {
        last?.let { ProfileBody(it, own = it.email.equals(App.user?.email, true), onClose = { App.profile = null }) }
    }
}

@Composable
private fun ProfileBody(who: Who, own: Boolean, onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    var posts by remember(who.email) { mutableStateOf<List<Post>?>(null) }
    LaunchedEffect(who.email, App.posts) { posts = Api.postsByAuthor(who.email) ?: posts ?: emptyList() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            if (bytes != null) App.changeAvatar(bytes, mime, ext)
        }
    }
    val photo = if (own) (App.user?.avatarUrl ?: who.photo) else who.photo
    val list = posts
    val likes = list?.sumOf { it.likes } ?: 0L
    val verified = App.isVerified(who.uid, who.email) || (own && App.user?.verified == true)
    val chatUser = App.chatUsers.firstOrNull { it.email.equals(who.email, true) || (who.uid != null && it.uid == who.uid) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        DotField(Modifier.fillMaxSize(), strength = 0.55f)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.enter(0).then(if (own) Modifier.pressable(true, 0.94f) { pick.launch("image/*") } else Modifier)) {
                        Avatar(photo, who.email.ifBlank { who.name }, 116.dp, Modifier.border(2.dp, Color.White, CircleShape))
                        if (own) Box(Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Glyph(G.IMAGE, 18.dp, Color.Black) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.enter(1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        T(who.name, 26.sp, FontWeight.Black, spacing = (-0.5).sp, maxLines = 1)
                        if (verified) Tick(20.dp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.enter(2)) { T("@" + who.email.substringBefore("@"), 14.sp, color = Muted) }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.enter(3), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        Stat((list?.size ?: 0).toString(), "Posts")
                        Stat(likes.toString(), "Likes")
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.enter(4), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (own) {
                            Box(Modifier.height(48.dp).width(150.dp).pressable(true, 0.94f) { pick.launch("image/*") }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { T("Edit photo", 15.sp, FontWeight.Bold, Color.Black) }
                            Box(Modifier.height(48.dp).width(130.dp).pressable(true, 0.94f) { App.signOut() }.clip(CircleShape).border(1.5.dp, Hair, CircleShape), contentAlignment = Alignment.Center) { T("Sign out", 15.sp, FontWeight.SemiBold) }
                        } else if (chatUser != null && App.user != null) {
                            Box(Modifier.height(48.dp).width(170.dp).pressable(true, 0.94f) { App.profile = null; App.tab = 1; App.openThread(chatUser) }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Glyph(G.CHAT, 18.dp, Color.Black); T("Message", 15.sp, FontWeight.Bold, Color.Black) }
                            }
                        }
                    }
                }
            }
            if (list == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(26.dp) } }
            else if (list.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { T("No posts yet.", 14.sp, color = Muted) } }
            else itemsIndexed(list, key = { _, p -> p.id }) { i, p -> PostRow(p, i) }
        }
        if (onClose != null) {
            Box(Modifier.statusBarsPadding().padding(12.dp).size(38.dp).pressable(true, 0.86f) { onClose() }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.BACK, 22.dp, Color.White, sw = 2.4f) }
        }
    }
}

@Composable
private fun Stat(n: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T(n, 22.sp, FontWeight.Black)
        T(label, 12.sp, color = Muted)
    }
}

@Composable
private fun PostRow(p: Post, index: Int) {
    Row(
        Modifier.fillMaxWidth().enter(index.coerceAtMost(8), 24.dp).pressable(true, 0.97f) { App.openPost(p) }
            .clip(RoundedCornerShape(18.dp)).background(Glass2).border(1.dp, Hair, RoundedCornerShape(18.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
            if (isImageUrl(p.fileUrl)) AsyncImage(p.fileUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else T(p.headline.take(1).uppercase(), 22.sp, FontWeight.Black, Color.White.copy(alpha = 0.3f))
        }
        Column(Modifier.weight(1f)) {
            T(p.headline, 15.sp, FontWeight.SemiBold, maxLines = 2, lineHeight = 20.sp)
            Spacer(Modifier.height(3.dp))
            T(agoShort(p.createdMs) + " ago", 12.sp, color = Muted)
        }
        if (p.likes > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Glyph(G.HEART, 16.dp, Muted, filled = true); T(p.likes.toString(), 12.sp, color = Muted)
        }
    }
}
