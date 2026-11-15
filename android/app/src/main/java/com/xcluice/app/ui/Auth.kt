package com.xcluice.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcluice.app.state.App
import kotlinx.coroutines.launch

@Composable
private fun Marquee(text: String, reverse: Boolean, sizeSp: Int, millis: Int, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mq")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing)), label = "x")
    var w by remember { mutableIntStateOf(1) }
    Box(modifier.fillMaxWidth().clipToBounds()) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = if (reverse) (x * w) - w else -(x * w) }
        ) {
            for (i in 0 until 4) {
                T(
                    text, sizeSp.sp, FontWeight.Black, Color.White.copy(alpha = 0.055f), maxLines = 1,
                    modifier = if (i == 0) Modifier.onSizeChanged { w = maxOf(1, it.width) } else Modifier
                )
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, password: Boolean, last: Boolean, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Color.White else Hair, tween(220), label = "b")
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Ascii,
            imeAction = if (last) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Glass2)
                    .border(1.5.dp, border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) T(hint, 17.sp, FontWeight.Medium, Muted)
                inner()
            }
        }
    )
}

@Composable
fun AuthScreen() {
    var register by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        err = null
        scope.launch {
            busy = true
            val e = App.authenticate(user, pass, register, confirm)
            busy = false
            if (e != null) {
                err = e
                for (s in listOf(-1f, 1f, -0.7f, 0.7f, -0.3f, 0f)) shake.animateTo(s, tween(55))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        DotField(Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f })
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(30.dp))
            Marquee("XCLUICE  •  CONNECT TO THE WORLD  •  ", false, 120, 30000)
            Marquee("TECH  •  ANDROID  •  GOOGLE  •  NEWS  •  ", true, 90, 24000)
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(120.dp))
            Box(
                Modifier.enter(0).size(58.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Glyph(G.CLOSE, 26.dp, Color.Black, sw = 3.4f) }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.enter(1)) {
                AnimatedContent(
                    targetState = register,
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(300))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(200))) },
                    label = "title"
                ) { r ->
                    T(if (r) "Join\nXcluice." else "Welcome\nback.", 48.sp, FontWeight.Black, lineHeight = 50.sp, spacing = (-1.5).sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.enter(2)) { T("Tech stories worth your tap.", 15.sp, FontWeight.Medium, Muted) }
            Spacer(Modifier.height(26.dp))
            Column(
                Modifier.enter(3).graphicsLayer { translationX = shake.value * 14.dp.toPx() },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Field(user, { user = it }, "Username", false, false) { submit() }
                Field(pass, { pass = it }, "Password", true, !register) { submit() }
                AnimatedVisibility(register, enter = expandVertically(tween(320)) + fadeIn(), exit = shrinkVertically(tween(240)) + fadeOut()) {
                    Field(confirm, { confirm = it }, "Confirm password", true, true) { submit() }
                }
            }
            AnimatedVisibility(err != null) {
                T(err ?: "", 14.sp, FontWeight.SemiBold, Soft, Modifier.padding(top = 12.dp, start = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .enter(4)
                    .fillMaxWidth()
                    .height(58.dp)
                    .pressable(!busy, 0.95f) { submit() }
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (busy) Spinner(26.dp, Color.Black)
                else T(if (register) "Create account" else "Sign in", 17.sp, FontWeight.Bold, Color.Black)
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.enter(5).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.pressable(true, 0.94f) { register = !register; err = null }.padding(8.dp)) {
                    T(if (register) "Already have an account?  Sign in" else "New here?  Create account", 14.5f.sp, FontWeight.SemiBold, Soft)
                }
            }
            Box(Modifier.enter(6).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.pressable(true, 0.94f) { App.continueAsGuest() }.padding(8.dp)) {
                    T("Browse without an account", 13.sp, FontWeight.Medium, Muted)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
