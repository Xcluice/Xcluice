package com.xcluice.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xcluice.app.data.defAvatar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

val Ink = Color(0xFF0A0A0B)
val Panel = Color(0xFF141416)
val Panel2 = Color(0xFF1C1C1E)
val Muted = Color(0xFF8E8E93)
val Soft = Color(0xFFC7C7CC)
val Hair = Color(0x14FFFFFF)
val Glass = Color(0x1AFFFFFF)
val Glass2 = Color(0x12FFFFFF)

val OutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Vivid colours are only ever used inside animations. */
val Confetti = listOf(
    Color(0xFFFF4D6D), Color(0xFFFFB400), Color(0xFF2EE66B), Color(0xFF0FADFF),
    Color(0xFFB36BFF), Color(0xFFFF8A3D), Color(0xFF00E5C3), Color(0xFFFF6FB5)
)

@Composable
fun T(
    text: String,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
    spacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, textAlign = align,
            letterSpacing = spacing, lineHeight = lineHeight
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines
    )
}

/** Tap target with a springy squash. No ripple, no highlight. */
fun Modifier.pressable(enabled: Boolean = true, scaleTo: Float = 0.9f, onClick: () -> Unit): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        if (pressed) scaleTo else 1f,
        spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium), label = "press"
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { if (enabled) onClick() }
            )
        }
}

/** Staggered slide-up/fade-in when first shown. */
fun Modifier.enter(index: Int = 0, dist: Dp = 36.dp): Modifier = composed {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) 1f else 0f, tween(560, index * 70, OutExpo), label = "enter")
    this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * dist.toPx()
    }
}

enum class G {
    BACK, SEND, MIC, SEARCH, CHATPLUS, HEART, PLAY, PAUSE, TRASH, CLOSE, PLUS, REPLY, COPY,
    FEED, CHAT, ME, EYE, OPEN, CHECK, IMAGE, REFRESH, EDIT
}

@Composable
fun Glyph(g: G, dp: Dp = 22.dp, tint: Color = Color.White, filled: Boolean = false, sw: Float = 2.2f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(dp)) {
        val k = this.size.minDimension / 24f
        withTransform({ scale(k, k, Offset.Zero) }) { drawGlyph(g, tint, filled, sw) }
    }
}

private fun path(block: Path.() -> Unit): Path = Path().apply(block)

private fun DrawScope.drawGlyph(g: G, c: Color, filled: Boolean, sw: Float) {
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (g) {
        G.BACK -> drawPath(path { moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f) }, c, style = st)
        G.SEND -> drawPath(path { moveTo(21f, 3f); lineTo(3f, 10.5f); lineTo(10f, 13f); lineTo(12.5f, 20f); close() }, c, style = if (filled) Fill else st)
        G.MIC -> {
            drawRoundRect(c, Offset(9f, 3f), Size(6f, 12f), CornerRadius(3f, 3f), style = st)
            drawArc(c, 0f, 180f, false, Offset(5f, 4f), Size(14f, 14f), style = st)
            drawLine(c, Offset(12f, 18f), Offset(12f, 21f), sw, StrokeCap.Round)
        }
        G.SEARCH -> {
            drawCircle(c, 7f, Offset(11f, 11f), style = st)
            drawLine(c, Offset(20f, 20f), Offset(16.5f, 16.5f), sw, StrokeCap.Round)
        }
        G.CHATPLUS -> {
            drawCircle(c, 8.5f, Offset(12f, 12f), style = st)
            drawLine(c, Offset(12f, 8.5f), Offset(12f, 15.5f), sw, StrokeCap.Round)
            drawLine(c, Offset(8.5f, 12f), Offset(15.5f, 12f), sw, StrokeCap.Round)
        }
        G.HEART -> drawPath(path {
            moveTo(12f, 21f)
            cubicTo(5f, 15.5f, 2f, 12f, 2f, 8.5f)
            cubicTo(2f, 5.5f, 4.3f, 3.5f, 7f, 3.5f)
            cubicTo(9f, 3.5f, 11f, 4.6f, 12f, 6.5f)
            cubicTo(13f, 4.6f, 15f, 3.5f, 17f, 3.5f)
            cubicTo(19.7f, 3.5f, 22f, 5.5f, 22f, 8.5f)
            cubicTo(22f, 12f, 19f, 15.5f, 12f, 21f)
            close()
        }, c, style = if (filled) Fill else st)
        G.PLAY -> drawPath(path { moveTo(8f, 5.5f); lineTo(8f, 18.5f); lineTo(19f, 12f); close() }, c)
        G.PAUSE -> {
            drawRoundRect(c, Offset(6f, 5f), Size(4.5f, 14f), CornerRadius(1.5f, 1.5f))
            drawRoundRect(c, Offset(13.5f, 5f), Size(4.5f, 14f), CornerRadius(1.5f, 1.5f))
        }
        G.TRASH -> {
            drawLine(c, Offset(4f, 7f), Offset(20f, 7f), sw, StrokeCap.Round)
            drawRoundRect(c, Offset(6f, 7f), Size(12f, 13f), CornerRadius(2f, 2f), style = st)
            drawPath(path { moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f) }, c, style = st)
            drawLine(c, Offset(10f, 11f), Offset(10f, 16f), sw, StrokeCap.Round)
            drawLine(c, Offset(14f, 11f), Offset(14f, 16f), sw, StrokeCap.Round)
        }
        G.CLOSE -> {
            drawLine(c, Offset(6f, 6f), Offset(18f, 18f), sw, StrokeCap.Round)
            drawLine(c, Offset(18f, 6f), Offset(6f, 18f), sw, StrokeCap.Round)
        }
        G.PLUS -> {
            drawLine(c, Offset(12f, 5f), Offset(12f, 19f), sw, StrokeCap.Round)
            drawLine(c, Offset(5f, 12f), Offset(19f, 12f), sw, StrokeCap.Round)
        }
        G.REPLY -> {
            drawPath(path { moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f) }, c, style = st)
            drawPath(path { moveTo(4f, 9f); lineTo(14f, 9f); quadraticBezierTo(20f, 9f, 20f, 15f); lineTo(20f, 19f) }, c, style = st)
        }
        G.COPY -> {
            drawRoundRect(c, Offset(9f, 9f), Size(11f, 11f), CornerRadius(2.5f, 2.5f), style = st)
            drawPath(path { moveTo(5f, 15f); lineTo(5f, 6.5f); quadraticBezierTo(5f, 4f, 7.5f, 4f); lineTo(15f, 4f) }, c, style = st)
        }
        G.FEED -> {
            drawRoundRect(c, Offset(4f, 4f), Size(6.5f, 6.5f), CornerRadius(2f, 2f), style = st)
            drawRoundRect(c, Offset(13.5f, 4f), Size(6.5f, 6.5f), CornerRadius(2f, 2f), style = st)
            drawRoundRect(c, Offset(4f, 13.5f), Size(6.5f, 6.5f), CornerRadius(2f, 2f), style = st)
            drawRoundRect(c, Offset(13.5f, 13.5f), Size(6.5f, 6.5f), CornerRadius(2f, 2f), style = st)
        }
        G.CHAT -> {
            drawCircle(c, 8f, Offset(12f, 11f), style = st)
            drawPath(path { moveTo(7f, 17.5f); lineTo(4.5f, 21f); lineTo(10f, 19f) }, c, style = st)
        }
        G.ME -> {
            drawCircle(c, 4f, Offset(12f, 8f), style = st)
            drawArc(c, 180f, 180f, false, Offset(4f, 15f), Size(16f, 16f), style = st)
        }
        G.EYE -> {
            drawPath(path { moveTo(2f, 12f); quadraticBezierTo(12f, 3f, 22f, 12f); quadraticBezierTo(12f, 21f, 2f, 12f) }, c, style = st)
            drawCircle(c, 3f, Offset(12f, 12f), style = st)
        }
        G.OPEN -> {
            drawLine(c, Offset(7f, 17f), Offset(17f, 7f), sw, StrokeCap.Round)
            drawPath(path { moveTo(9f, 7f); lineTo(17f, 7f); lineTo(17f, 15f) }, c, style = st)
        }
        G.CHECK -> drawPath(path { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }, c, style = st)
        G.IMAGE -> {
            drawRoundRect(c, Offset(3f, 5f), Size(18f, 14f), CornerRadius(3f, 3f), style = st)
            drawCircle(c, 1.6f, Offset(9f, 10f))
            drawPath(path { moveTo(3f, 17f); lineTo(9f, 12.5f); lineTo(14f, 16f); lineTo(17f, 13.5f); lineTo(21f, 17f) }, c, style = st)
        }
        G.EDIT -> {
            drawPath(path { moveTo(4f, 20f); lineTo(4.8f, 15.5f); lineTo(16f, 4.3f); quadraticBezierTo(17.3f, 3f, 18.7f, 4.3f); lineTo(19.7f, 5.3f); quadraticBezierTo(21f, 6.7f, 19.7f, 8f); lineTo(8.5f, 19.2f); close() }, c, style = st)
            drawLine(c, Offset(14f, 6.5f), Offset(17.5f, 10f), sw, StrokeCap.Round)
        }
        G.REFRESH -> {
            drawArc(c, -50f, 280f, false, Offset(4f, 4f), Size(16f, 16f), style = st)
            drawPath(path { moveTo(20f, 3.5f); lineTo(20f, 8.5f); lineTo(15f, 8.5f) }, c, style = st)
        }
    }
}

@Composable
fun Tick(dp: Dp = 14.dp) {
    Canvas(Modifier.size(dp)) {
        val k = this.size.minDimension / 24f
        drawCircle(Color.White, 11.5f * k, Offset(12f * k, 12f * k))
        drawPath(
            path { moveTo(7.6f * k, 12.4f * k); lineTo(10.8f * k, 15.5f * k); lineTo(16.6f * k, 9.1f * k) },
            Color.Black, style = Stroke(2.3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun Avatar(url: String?, key: String?, dp: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = if (url.isNullOrBlank()) defAvatar(key) else url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(dp).clip(CircleShape).background(Color(0xFF111111))
    )
}

@Composable
fun Spinner(dp: Dp = 22.dp, color: Color = Color.White) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(dp)) {
        inset(2.5f.dp.toPx()) {
            drawArc(color, a, 110f, false, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Monochrome dot grid with a travelling wave. */
@Composable
fun DotField(modifier: Modifier = Modifier, spacing: Dp = 26.dp, strength: Float = 1f) {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier) {
        val sp = spacing.toPx()
        val cx = size.width / 2f; val cy = size.height * 0.38f
        var y = sp / 2f
        while (y < size.height) {
            var x = sp / 2f
            while (x < size.width) {
                val dx = x - cx; val dy = y - cy
                val d = sqrt(dx * dx + dy * dy)
                val w = (sin(d / (sp * 1.6f) - phase) * 0.5f + 0.5f)
                val r = (0.9f + 1.9f * w * w) * strength
                drawCircle(Color.White.copy(alpha = (0.05f + 0.30f * w * w) * strength), r.dp.toPx() * 0.9f, Offset(x, y))
                x += sp
            }
            y += sp
        }
    }
}

@Composable
fun Dot(dp: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(dp).clip(CircleShape).background(color))
}
