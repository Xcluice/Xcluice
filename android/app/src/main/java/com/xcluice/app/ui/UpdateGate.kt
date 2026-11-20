package com.xcluice.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.app.state.App

/** Full-screen, non-dismissible gate shown when this build is older than the remote minimum version. */
@Composable
fun UpdateGate() {
    val ctx = LocalContext.current
    BackHandler(true) { }
    val t = rememberInfiniteTransition(label = "upd")
    val pulse by t.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val fade by animateFloatAsState(1f, tween(450), label = "f")
    Column(
        Modifier.fillMaxSize().background(Color.Black).blockTouches().alpha(fade).statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(96.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) {
            Glyph(G.CHAT, 40.dp, Color.White)
        }
        Spacer(Modifier.height(28.dp))
        T("Update required", 24.sp, FontWeight.Bold, Color.White, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        T(App.updateMsg.ifBlank { "A new version of Xcluice is required to continue." }, 15.sp, color = Muted, align = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(32.dp))
        BtnWhite("Update now", Modifier.fillMaxWidth()) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(App.updateUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
