package com.xcluice.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.app.state.App

/** Dismissible "new version available" card, driven by GitHub releases. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    AnimatedVisibility(
        visible = App.newBuild > 0 && !App.newBuildDismissed && !App.updateRequired,
        enter = slideInVertically(tween(420)) { -it } + fadeIn(tween(420)),
        exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300)),
        modifier = modifier
    ) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF1C1C1E)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                T("Update available", 14.sp, FontWeight.Bold)
                T("A newer version of Xcluice is ready", 12.sp, color = Muted)
            }
            Box(Modifier.pressable(true, 0.92f) { App.dismissNewBuild() }.clip(CircleShape).padding(horizontal = 10.dp, vertical = 8.dp)) { T("Later", 13.sp, FontWeight.SemiBold, Muted) }
            Box(Modifier.pressable(true, 0.92f) {
                com.xcluice.app.data.Updater.start(ctx, App.newBuildUrl())
            }.clip(CircleShape).background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp)) { T("Update", 13.sp, FontWeight.Bold, Color.Black) }
        }
    }
}
