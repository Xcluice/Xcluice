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
import androidx.compose.ui.graphics.Path
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

private fun clamp01(x: Float) = max(0f, min(1f, x))
private fun easeOut(x: Float): Float { val k = 1f - clamp01(x); return 1f - k * k * k }
private fun easeInOut(x: Float): Float { val c = clamp01(x); return c * c * (3f - 2f * c) }

@Composable
fun Intro(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
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
        Canvas(Modifier.fillMaxSize()) { drawIntro(p.value) }
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

private fun lerpO(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

private fun DrawScope.poly(c: Color, p: List<Offset>) {
    val path = Path().apply {
        moveTo(p[0].x, p[0].y)
        for (i in 1 until p.size) lineTo(p[i].x, p[i].y)
        close()
    }
    drawPath(path, c)
}

/** The app icon: a "\" bar, then a "/" blade slashes through it and gets cut in two. */
private fun DrawScope.drawIntro(t: Float) {
    val cx = size.width / 2f
    val cy = size.height * 0.36f
    val minDim = min(size.width, size.height)
    val k = (minDim * 0.34f) / 46.8f
    fun pt(x: Float, y: Float) = Offset(cx + (x - 54f) * k, cy + (y - 54f) * k)

    // main "\" bar (exact launcher-icon geometry)
    val a = pt(30.6f, 33.2f); val b = pt(42.3f, 33.2f); val c = pt(65.7f, 74.8f); val d = pt(77.4f, 74.8f)
    // full "/" band: top edge t0-t1, bottom edge b0-b1
    val t0 = pt(68.04f, 33.2f); val t1 = pt(77.4f, 33.2f); val b0 = pt(30.6f, 74.8f); val b1 = pt(39.96f, 74.8f)
    val uA = 0.3063f; val uB = 0.6937f  // where the main bar cuts the "/" band

    // part of the "/" band between u0..u1 along its length, thinned to width w (1 = full)
    fun band(u0: Float, u1: Float, w: Float) {
        if (u1 <= u0 || w <= 0f) return
        val l0 = lerpO(t0, b0, u0); val r0 = lerpO(t1, b1, u0)
        val l1 = lerpO(t0, b0, u1); val r1 = lerpO(t1, b1, u1)
        val m0 = lerpO(l0, r0, 0.5f); val m1 = lerpO(l1, r1, 0.5f)
        poly(Color.White, listOf(lerpO(m0, l0, w), lerpO(m0, r0, w), lerpO(m1, r1, w), lerpO(m1, l1, w)))
    }

    val s1 = easeOut((t - 0.05f) / 0.22f)        // "\" bar draws in
    val sweep = easeInOut((t - 0.30f) / 0.12f)   // "/" blade slashes across
    val cut = easeOut((t - 0.40f) / 0.10f)       // the part inside the bar is cut away
    val jolt = sin(PI.toFloat() * clamp01((t - 0.33f) / 0.12f))
    val shake = sin(t * 140f) * jolt * 5.dp.toPx()
    val pulse = 1f + 0.07f * sin(PI.toFloat() * clamp01((t - 0.52f) / 0.2f))

    withTransform({ translate(shake, 0f); scale(pulse, pulse, Offset(cx, cy)) }) {
        if (s1 > 0f) poly(Color.White, listOf(a, b, lerpO(b, d, s1), lerpO(a, c, s1)))
        if (sweep > 0f) {
            band(0f, min(sweep, uA), 1f)
            band(uB, sweep, 1f)
            band(uA, min(sweep, uB), 1f - cut)
        }
    }

    // the blade flash: a thin line through the icon along the cut
    val bl = 1f - clamp01((t - 0.30f) / 0.16f)
    if (t > 0.30f && bl > 0f) {
        val tm = lerpO(t0, t1, 0.5f); val bm = lerpO(b0, b1, 0.5f)
        drawLine(
            Color.White.copy(alpha = bl * easeOut((t - 0.30f) / 0.04f)),
            lerpO(tm, bm, -0.7f), lerpO(tm, bm, 1.7f), 2.5f.dp.toPx(), StrokeCap.Round
        )
    }
}
