package com.xcluice.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class Part(val ang: Float, val speed: Float, val size: Float, val color: Color)

private fun clamp01(x: Float) = max(0f, min(1f, x))
private fun easeOut(x: Float): Float { val k = 1f - clamp01(x); return 1f - k * k * k }
private fun easeInOut(x: Float): Float { val c = clamp01(x); return c * c * (3f - 2f * c) }

@Composable
fun Intro(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val parts = remember {
        val r = Random(11)
        List(54) { i ->
            Part(
                ang = (i / 54f) * (2f * PI.toFloat()) + r.nextFloat() * 0.25f,
                speed = 0.45f + r.nextFloat() * 0.75f,
                size = 3f + r.nextFloat() * 6f,
                color = Confetti[i % Confetti.size]
            )
        }
    }
    var finished by remember { mutableStateOf(false) }
    suspend fun finish() {
        if (finished) return
        finished = true
        kotlinx.coroutines.withTimeoutOrNull(4500) { androidx.compose.runtime.snapshotFlow { com.xcluice.app.state.App.ready }.first { it } }
        onDone()
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(3300, easing = LinearEasing))
        finish()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { scope.launch { finish() } } }
            .graphicsLayer {
                val f = easeInOut((p.value - 0.92f) / 0.08f)
                alpha = 1f - f
                scaleX = 1f + 0.18f * f
                scaleY = 1f + 0.18f * f
            }
    ) {
        DotField(Modifier.fillMaxSize().graphicsLayer { alpha = easeOut(p.value / 0.3f) })
        Canvas(Modifier.fillMaxSize()) { drawIntro(p.value, parts) }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.64f))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                "XCLUICE".forEachIndexed { i, ch ->
                    T(
                        ch.toString(), 34.sp, FontWeight.Black,
                        modifier = Modifier.graphicsLayer {
                            val a = easeOut((p.value - (0.46f + i * 0.04f)) / 0.2f)
                            alpha = a
                            translationY = (1f - a) * 70.dp.toPx()
                            rotationZ = (1f - a) * (if (i % 2 == 0) -18f else 18f)
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            T(
                "connect to the world", 13.sp, FontWeight.Medium, Muted, spacing = 3.sp,
                modifier = Modifier.graphicsLayer { alpha = easeOut((p.value - 0.78f) / 0.14f) }.padding(top = 2.dp)
            )
            Spacer(Modifier.weight(0.36f))
        }
    }
}

private fun DrawScope.drawIntro(t: Float, parts: List<Part>) {
    val cx = size.width / 2f
    val cy = size.height * 0.36f
    val half = min(size.width, size.height) * 0.17f
    val sw = half * 0.42f
    val pulse = 1f + 0.09f * sin(PI.toFloat() * clamp01((t - 0.40f) / 0.22f))

    withTransform({ scale(pulse, pulse, Offset(cx, cy)) }) {
        val s1 = easeOut((t - 0.06f) / 0.24f)
        val s2 = easeOut((t - 0.20f) / 0.24f)
        if (s1 > 0f) {
            drawLine(
                Color.White, Offset(cx - half, cy - half),
                Offset(cx - half + 2f * half * s1, cy - half + 2f * half * s1), sw, StrokeCap.Round
            )
        }
        if (s2 > 0f) {
            drawLine(
                Color.White, Offset(cx + half, cy - half),
                Offset(cx + half - 2f * half * s2, cy - half + 2f * half * s2), sw, StrokeCap.Round
            )
        }
    }

    // orbiting satellites
    val orb = easeOut((t - 0.5f) / 0.2f)
    if (orb > 0f) {
        for (i in 0 until 3) {
            val a = t * 2f * PI.toFloat() * 1.6f + i * (2f * PI.toFloat() / 3f)
            val rr = half * 2.05f * orb
            drawCircle(Color.White, 5.dp.toPx(), Offset(cx + cos(a) * rr, cy + sin(a) * rr))
        }
        drawCircle(Color.White.copy(alpha = 0.18f * orb), half * 2.05f * orb, Offset(cx, cy), style = Stroke(1.5f.dp.toPx()))
    }

    // shockwave + confetti
    val q = (t - 0.42f) / 0.58f
    if (q > 0f) {
        val e = easeOut(q)
        val maxR = size.maxDimension * 0.7f
        drawCircle(Color.White.copy(alpha = 1f - e), maxR * e, Offset(cx, cy), style = Stroke(3.dp.toPx() * (1f - e) + 0.5f))
        drawCircle(Color.White.copy(alpha = (1f - e) * 0.5f), maxR * 0.7f * easeOut(q * 0.9f), Offset(cx, cy), style = Stroke(1.5f.dp.toPx()))
    }
}
