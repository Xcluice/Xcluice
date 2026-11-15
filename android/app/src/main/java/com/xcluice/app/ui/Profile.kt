package com.xcluice.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.app.state.App

@Composable
fun ProfileTab() {
    val u = App.user
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            if (bytes != null) App.changeAvatar(bytes, mime, ext)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        DotField(Modifier.fillMaxSize(), strength = 0.7f)
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))
            if (u == null) {
                Column(Modifier.enter(0), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("You're browsing as a guest", 20.sp, FontWeight.Bold, align = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    T("Sign in to post, like and chat.", 14.sp, color = Muted)
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.height(52.dp).width(200.dp).pressable(true, 0.94f) { App.guest = false }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        T("Sign in", 16.sp, FontWeight.Bold, Color.Black)
                    }
                }
            } else {
                Box(Modifier.enter(0).pressable(true, 0.94f) { pick.launch("image/*") }) {
                    Avatar(u.avatarUrl, u.email, 120.dp, Modifier.border(2.dp, Color.White, CircleShape))
                    Box(Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Glyph(G.IMAGE, 18.dp, Color.Black) }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.enter(1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    T(u.displayName, 26.sp, FontWeight.Black, spacing = (-0.5).sp)
                    if (u.verified || App.isVerified(u.id, u.email)) Tick(20.dp)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.enter(2)) { T("@" + u.username, 14.sp, color = Muted) }
                Spacer(Modifier.height(40.dp))
                Box(
                    Modifier.enter(3).height(52.dp).width(220.dp).pressable(true, 0.94f) { App.signOut() }
                        .clip(CircleShape).border(1.5.dp, Hair, CircleShape),
                    contentAlignment = Alignment.Center
                ) { T("Sign out", 16.sp, FontWeight.SemiBold) }
            }
        }
    }
}
