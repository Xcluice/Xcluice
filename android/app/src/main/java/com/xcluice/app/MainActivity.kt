package com.xcluice.app

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.app.state.App
import com.xcluice.app.ui.AuthScreen
import com.xcluice.app.ui.ChatTab
import com.xcluice.app.ui.PostSheet
import com.xcluice.app.ui.ConfirmHost
import com.xcluice.app.ui.StoryViewer
import com.xcluice.app.ui.StoryComposer
import com.xcluice.app.ui.EditProfileSheet
import com.xcluice.app.ui.FeedTab
import com.xcluice.app.ui.G
import com.xcluice.app.ui.Glass
import com.xcluice.app.ui.Glyph
import com.xcluice.app.ui.Hair
import com.xcluice.app.ui.Intro
import com.xcluice.app.ui.Ink
import com.xcluice.app.ui.Muted
import com.xcluice.app.ui.OutExpo
import com.xcluice.app.ui.PostViewer
import com.xcluice.app.ui.ProfileTab
import com.xcluice.app.ui.T
import com.xcluice.app.ui.pressable

class MainActivity : ComponentActivity() {
    override fun onStart() { super.onStart(); App.foreground = true; App.onOpen() }
    override fun onStop() { super.onStop(); App.foreground = false }

    override fun onNewIntent(i: android.content.Intent) { super.onNewIntent(i); if (i.hasExtra("tab")) App.tab = i.getIntExtra("tab", 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        com.xcluice.app.data.Notifier.setup(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
        App.tab = intent?.getIntExtra("tab", App.tab) ?: App.tab
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val stage = when {
            !App.introDone -> 0
            App.user == null && !App.guest -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
            label = "stage"
        ) { s ->
            when (s) {
                0 -> Intro { App.introDone = true }
                1 -> AuthScreen()
                else -> MainShell()
            }
        }
        Toast()
    }
}

@Composable
private fun MainShell() {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = App.tab,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "tabs"
        ) { t ->
            when (t) {
                0 -> FeedTab()
                1 -> ChatTab()
                else -> ProfileTab()
            }
        }
        BottomBar(Modifier.align(Alignment.BottomCenter))
        com.xcluice.app.ui.ProfileOverlay()
        PostViewer()
        EditProfileSheet()
        PostSheet()
        StoryComposer()
        StoryViewer()
        ConfirmHost()
    }
}

@Composable
private fun BottomBar(modifier: Modifier) {
    val hide = App.tab == 1 && App.thread != null
    val off by animateDpAsState(if (hide) 120.dp else 0.dp, spring(0.8f, 400f), label = "bar")
    Box(modifier.offset(y = off).navigationBarsPadding().padding(bottom = 12.dp)) {
        Row(
            Modifier.clip(CircleShape).background(Color(0xEE141416)).border(1.dp, Hair, CircleShape).padding(6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            listOf(G.FEED to "Feed", G.CHAT to "Chat", G.ME to "You").forEachIndexed { i, (g, label) ->
                val on = App.tab == i
                val w by animateDpAsState(if (on) 108.dp else 56.dp, spring(0.7f, 500f), label = "w")
                Box(
                    Modifier.width(w).height(48.dp).pressable(true, 0.9f) { App.tab = i }
                        .clip(CircleShape).background(if (on) Color.White else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(g, 22.dp, if (on) Color.Black else Muted)
                        if (on) { androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp)); T(label, 14.sp, FontWeight.Bold, Color.Black, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toast() {
    val m = App.toastMsg
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 14.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(m != null, enter = slideInVertically(tween(320, easing = OutExpo)) { -it * 2 } + fadeIn(), exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut()) {
            var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            if (m != null) last = m
            Box(Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 20.dp, vertical = 11.dp)) {
                T(last, 14.sp, FontWeight.SemiBold, Color.Black)
            }
        }
    }
}
