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
import com.xcluice.app.data.hostOf
import com.xcluice.app.data.normalizeLink
import androidx.compose.ui.graphics.graphicsLayer
import com.xcluice.app.state.App
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItems
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Intent

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
    val pr = App.resolve(who)
    val id = pr.id.ifBlank { who.uid ?: "" }
    val email = pr.email.ifBlank { who.email }
    var posts by remember(email) { mutableStateOf<List<Post>?>(null) }
    LaunchedEffect(email, App.posts) { posts = Api.postsByAuthor(email) ?: posts ?: emptyList() }
    LaunchedEffect(id) { if (id.isNotBlank()) App.loadCounts(id) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) App.changeAvatarFromUri(uri)
    }
    val addStory = rememberStoryPicker()
    val photo = if (own) (App.user?.avatarUrl ?: pr.photo ?: who.photo) else (pr.photo ?: who.photo)
    val name = if (own) (App.user?.displayName ?: pr.name) else pr.name.ifBlank { who.name }
    val username = pr.username.ifBlank { email.substringBefore("@") }
    val list = posts
    val verified = pr.verified || App.isVerified(id.ifBlank { null }, email) || (own && App.user?.verified == true)
    val role = pr.role.ifBlank { if (own) App.user?.role ?: "" else "" }
    val isAdminRole = role.equals("admin", true)
    val chatUser = App.chatUsers.firstOrNull { it.email.equals(email, true) || (id.isNotBlank() && it.uid == id) }
    val following = id.isNotBlank() && App.isFollowing(id)
    val hasStory = id.isNotBlank() && App.storiesOf(id).isNotEmpty()
    val joined = remember(pr.joined) { pr.joined?.let { runCatching { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(com.xcluice.app.data.parseMs(it))) }.getOrNull() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        DotField(Modifier.fillMaxSize(), strength = 0.55f)
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.enter(0).pressable(true, 0.94f) {
                        if (hasStory) App.openStories(listOf(id), 0, 0) else if (own) addStory()
                    }, contentAlignment = Alignment.Center) {
                        StoryRing(id.ifBlank { null }, 128.dp) {
                            Avatar(photo, email.ifBlank { name }, if (hasStory) 114.dp else 124.dp, if (hasStory) Modifier else Modifier.border(2.dp, Color.White, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.enter(1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        T(name, 26.sp, FontWeight.Black, spacing = (-0.5).sp, maxLines = 1)
                        if (verified) Tick(20.dp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.enter(2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("@$username" + (joined?.let { " \u00B7 Joined $it" } ?: ""), 13.sp, color = Muted, maxLines = 1)
                        if (role.isNotBlank() && !role.equals("user", true) && !role.equals("member", true)) {
                            Box(Modifier.clip(CircleShape).background(if (isAdminRole) Color.White else Glass).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                T(role.uppercase(), 10.sp, FontWeight.Black, if (isAdminRole) Color.Black else Soft, spacing = 0.8.sp)
                            }
                        }
                    }
                    if (pr.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.enter(3).padding(horizontal = 28.dp)) { LinkText(pr.bio, 14.sp, Soft, lineHeight = 20.sp) }
                    }
                    if (pr.website.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.enter(3).pressable(true, 0.95f) { openUrl(ctx, pr.website) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Glyph(G.LINK, 14.dp, Color.White)
                            T(hostOf(pr.website), 13.sp, FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    val fc = App.counts[id]
                    Row(Modifier.enter(4), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Stat((list?.size ?: 0).toString(), "Posts")
                        Stat(if (id.isBlank()) "-" else num(App.followersOf(id)), "Followers")
                        Stat(if (id.isBlank()) "-" else num(fc?.second ?: App.followingOf(id)), "Following")
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.enter(5).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (own) {
                            BtnWhite("Edit profile", Modifier.weight(1f)) { App.editingProfile = true }
                            BtnGhost("Add story", Modifier.weight(1f), G.PLUS) { addStory() }
                        } else {
                            if (App.user != null && id.isNotBlank()) {
                                if (following) BtnGhost("Following", Modifier.weight(1f)) { App.toggleFollow(id) }
                                else BtnWhite("Follow", Modifier.weight(1f)) { App.toggleFollow(id) }
                            }
                            if (chatUser != null && App.user != null) BtnGhost("Message", Modifier.weight(1f), G.CHAT) { App.profile = null; App.tab = 1; App.openThread(chatUser) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.enter(6).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (id.isNotBlank()) BtnGhost("Share", Modifier.weight(1f), G.SHARE) {
                            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "https://xcluice.vercel.app/#profile-$id") }
                            ctx.startActivity(Intent.createChooser(i, "Share profile").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        if (own) {
                            BtnGhost("Photo", Modifier.weight(1f), G.CAMERA) { pickPhoto.launch("image/*") }
                            BtnGhost("Sign out", Modifier.weight(1f)) { App.ask("Sign out of Xcluice?", "Sign out", true) { App.signOut() } }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
                }
            }
            if (list == null) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(26.dp) } }
            else if (list.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { T("No posts yet.", 14.sp, color = Muted) } }
            else gridItems(list, key = { _, p -> p.id }) { i, p -> GridPost(p, i) }
        }
        if (onClose != null) {
            Box(Modifier.statusBarsPadding().padding(12.dp).size(38.dp).pressable(true, 0.86f) { onClose() }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.BACK, 22.dp, Color.White, sw = 2.4f) }
        }
    }
}

@Composable
private fun Stat(n: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T(n, 20.sp, FontWeight.Black)
        T(label, 12.sp, color = Muted)
    }
}

@Composable
private fun GridPost(p: Post, index: Int) {
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).enter(index.coerceAtMost(9), 20.dp).pressable(true, 0.95f) { App.open(p, ctx) }
            .clip(RoundedCornerShape(14.dp)).border(1.dp, Hair, RoundedCornerShape(14.dp))
    ) {
        PostThumb(p, Modifier.fillMaxSize(), small = true)
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Color(0xAA000000)).padding(horizontal = 7.dp, vertical = 2.dp)) {
            T(kindLabel(p), 9.sp, FontWeight.Black, Color.White, spacing = 0.6.sp)
        }
    }
}

@Composable
fun EditProfileSheet() {
    val on = App.editingProfile
    val u = App.user
    BackHandler(on) { App.editingProfile = false }
    AnimatedVisibility(on && u != null, enter = fadeIn(tween(180)) + scaleIn(tween(300, easing = OutExpo), 0.94f), exit = fadeOut(tween(160)) + scaleOut(tween(180), 0.96f)) {
        val me = App.user ?: return@AnimatedVisibility
        val pr = App.resolve(Who(me.displayName, me.email, me.avatarUrl, me.id))
        var name by remember { mutableStateOf(me.displayName) }
        var username by remember { mutableStateOf(me.username) }
        var bio by remember { mutableStateOf(pr.bio) }
        var site by remember { mutableStateOf(pr.website) }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var pwOpen by remember { mutableStateOf(false) }
        var cur by remember { mutableStateOf("") }
        var nw by remember { mutableStateOf("") }
        var cf by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize().background(Color(0xF2000000)).pressable(false) {}) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Edit profile", 22.sp, FontWeight.Black, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).pressable(true, 0.86f) { App.editingProfile = false }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.CLOSE, 17.dp, Color.White) }
                }
                Lbl("Display name"); InputBox(name, { name = it }, "Your name", maxLen = 40)
                Lbl("Username"); InputBox(username, { username = it.lowercase().filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '_' } }, "username", maxLen = 20)
                Lbl("Bio  (${bio.length}/160)"); InputBox(bio, { bio = it }, "Tell people about you", singleLine = false, minHeight = 80.dp, maxLen = 160)
                Lbl("Website"); InputBox(site, { site = it }, "example.com", maxLen = 100, keyboard = androidx.compose.ui.text.input.KeyboardType.Uri)
                err?.let { T(it, 13.sp, FontWeight.SemiBold, Soft) }
                BtnWhite(if (busy) "Saving\u2026" else "Save changes", Modifier.fillMaxWidth(), enabled = !busy) {
                    val n = name.trim(); val un = username.trim()
                    when {
                        n.isEmpty() -> err = "Enter a display name."
                        un.length < 3 -> err = "Username must be 3-20 letters, numbers or _."
                        else -> {
                            val w = site.trim().let { if (it.isBlank()) "" else normalizeLink(it) ?: "" }
                            if (site.isNotBlank() && w.isBlank()) { err = "That website doesn't look right."; return@BtnWhite }
                            busy = true; err = null
                            App.saveProfile(n, un, bio.trim(), w) { e -> busy = false; err = e; if (e == null) App.editingProfile = false }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
                Row(Modifier.fillMaxWidth().pressable(true, 0.97f) { pwOpen = !pwOpen }, verticalAlignment = Alignment.CenterVertically) {
                    T("Change password", 15.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(Modifier.graphicsLayerRot(if (pwOpen) 270f else 180f)) { Glyph(G.BACK, 18.dp, Muted) }
                }
                AnimatedVisibility(pwOpen) {
                    var pwErr by remember { mutableStateOf<String?>(null) }
                    var pwBusy by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InputBox(cur, { cur = it }, "Current password", password = true)
                        InputBox(nw, { nw = it }, "New password (min 6)", password = true)
                        InputBox(cf, { cf = it }, "Confirm new password", password = true)
                        pwErr?.let { T(it, 13.sp, FontWeight.SemiBold, Soft) }
                        BtnGhost(if (pwBusy) "Updating\u2026" else "Update password", Modifier.fillMaxWidth()) {
                            when {
                                cur.isEmpty() -> pwErr = "Enter your current password."
                                nw.length < 6 -> pwErr = "New password must be at least 6 characters."
                                nw != cf -> pwErr = "Passwords don't match."
                                else -> { pwBusy = true; pwErr = null
                                    scope.launch {
                                        val r = Api.changePassword(me.email, cur, nw)
                                        pwBusy = false
                                        if (r == null) { cur = ""; nw = ""; cf = ""; pwOpen = false; App.toast("Password updated") } else pwErr = r
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Lbl(t: String) { T(t, 12.sp, FontWeight.SemiBold, Muted, spacing = 0.4.sp) }

private fun Modifier.graphicsLayerRot(deg: Float): Modifier = this.then(Modifier.graphicsLayer { rotationZ = deg })
