package com.xcluice.app.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.app.state.App

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val URL_RE = Regex("(https?://[^\\s<>\"']+|www\\.[^\\s<>\"']+)", RegexOption.IGNORE_CASE)

fun firstUrl(text: String): String? {
    val m = URL_RE.find(text) ?: return null
    val t = m.value.trimEnd('.', ',', '!', '?', ';', ':', ')', ']')
    return if (t.startsWith("www.", true)) "https://$t" else t
}

fun linkified(text: String, link: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in URL_RE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val raw = m.value
        val trimmed = raw.trimEnd('.', ',', '!', '?', ';', ':', ')', ']')
        val trail = raw.substring(trimmed.length)
        val full = if (trimmed.startsWith("www.", true)) "https://$trimmed" else trimmed
        pushStringAnnotation("URL", full)
        withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(trimmed) }
        pop()
        append(trail)
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun LinkText(text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, lineHeight: TextUnit = TextUnit.Unspecified, linkColor: Color = Color.White) {
    val ctx = LocalContext.current
    val a = remember(text, linkColor) { linkified(text, linkColor) }
    ClickableText(
        a, modifier = modifier, style = TextStyle(color = color, fontSize = size, lineHeight = lineHeight),
        onClick = { off -> a.getStringAnnotations("URL", off, off).firstOrNull()?.let { openUrl(ctx, it.item) } }
    )
}

@Composable
fun InputBox(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, minHeight: Dp = 52.dp, password: Boolean = false, maxLen: Int = Int.MAX_VALUE,
    keyboard: KeyboardType = KeyboardType.Text
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Color.White else Hair, tween(200), label = "ib")
    BasicTextField(
        value = value, onValueChange = { if (it.length <= maxLen) onChange(it) }, singleLine = singleLine,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(16.dp)).background(Glass2)
                    .border(1.5.dp, border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty()) T(hint, 16.sp, color = Muted)
                inner()
            }
        }
    )
}

@Composable
fun Pill(label: String?, g: G?, onClick: (() -> Unit)?, modifier: Modifier = Modifier, on: Boolean = false, tint: Color = Soft) {
    val bg by animateColorAsState(if (on) Color.White else Glass2, tween(200), label = "pill")
    val fg = if (on) Color.Black else tint
    val base = modifier.height(32.dp).clip(CircleShape).background(bg).border(1.dp, Hair, CircleShape)
    Row(
        (if (onClick != null) base.pressable(true, 0.9f, onClick) else base).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (g != null) Glyph(g, 15.dp, fg, filled = on && g == G.HEART, sw = 2.2f)
        if (label != null) T(label, 12.sp, FontWeight.SemiBold, fg, maxLines = 1)
    }
}

@Composable
fun BtnWhite(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, g: G? = null, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).pressable(enabled, 0.94f, onClick).clip(CircleShape).background(if (enabled) Color.White else Glass),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (g != null) Glyph(g, 18.dp, if (enabled) Color.Black else Muted)
            T(label, 15.sp, FontWeight.Bold, if (enabled) Color.Black else Muted)
        }
    }
}

@Composable
fun BtnGhost(label: String, modifier: Modifier = Modifier, g: G? = null, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).pressable(true, 0.94f, onClick).clip(CircleShape).border(1.5.dp, Hair, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (g != null) Glyph(g, 18.dp, Color.White)
            T(label, 15.sp, FontWeight.SemiBold)
        }
    }
}

@Composable
fun ConfirmHost() {
    val c = App.confirm
    var last by remember { mutableStateOf<com.xcluice.app.state.Confirm?>(null) }
    if (c != null) last = c
    AnimatedVisibility(c != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
        val l = last
        if (l != null) {
            val p = remember(l) { Animatable(0f) }
            LaunchedEffect(l) { p.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
            Box(
                Modifier.fillMaxSize().background(Color(0xAA000000)).pointerInput(Unit) { detectTapGestures { App.confirm = null } },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(32.dp).widthIn(max = 340.dp).graphicsLayer { scaleX = 0.9f + 0.1f * p.value; scaleY = scaleX; alpha = p.value.coerceIn(0f, 1f) }
                        .clip(RoundedCornerShape(24.dp)).background(Panel2).border(1.dp, Hair, RoundedCornerShape(24.dp))
                        .pointerInput(Unit) { detectTapGestures { } }.padding(22.dp)
                ) {
                    T(l.msg, 17.sp, FontWeight.SemiBold, lineHeight = 24.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BtnGhost("Cancel", Modifier.weight(1f)) { App.confirm = null }
                        Box(
                            Modifier.weight(1f).height(48.dp).pressable(true, 0.94f) { App.confirm = null; l.onOk() }.clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) { T(l.ok, 15.sp, FontWeight.Bold, Color.Black) }
                    }
                }
            }
        }
    }
}


@Composable
fun UploadBanner() {
    val label = App.upLabel
    var last by remember { mutableStateOf("") }
    if (label != null) last = label
    androidx.compose.animation.AnimatedVisibility(
        label != null,
        enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(300)) { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(250)) { -it } + androidx.compose.animation.fadeOut()
    ) {
        val f by androidx.compose.animation.core.animateFloatAsState(App.upFrac, androidx.compose.animation.core.tween(220), label = "up")
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xFF111111))
                    .border(1.dp, Color(0x33FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(label ?: last, 14.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    T("${(f * 100).toInt()}%", 13.sp, FontWeight.Bold, Soft)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}


/** Full-screen overlays must swallow touches so they never reach the screen underneath. */
fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) { detectTapGestures { } }
