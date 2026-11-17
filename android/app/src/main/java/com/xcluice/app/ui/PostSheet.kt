package com.xcluice.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xcluice.app.data.isImageUrl
import com.xcluice.app.data.isLinkUrl
import com.xcluice.app.data.normalizeLink
import com.xcluice.app.state.App
import com.xcluice.app.state.Draft
import com.xcluice.app.state.EditDraft
import com.xcluice.app.state.Posting
import kotlinx.coroutines.launch

private fun close() { App.composing = false; App.editing = null }

@Composable
fun PostSheet() {
    val open = App.composing || App.editing != null
    BackHandler(open) { close() }
    AnimatedVisibility(
        open,
        enter = slideInVertically(tween(360, easing = OutExpo)) { it } + fadeIn(),
        exit = slideOutVertically(tween(220)) { it } + fadeOut()
    ) { SheetBody() }
}

@Composable
private fun SheetBody() {
    val ed = remember { App.editing }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(ed?.title ?: "") }
    var desc by remember { mutableStateOf(if (ed != null && ed.subject != ed.title) ed.subject else "") }
    var file by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileIsImage by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf(ed != null) }
    var link by remember { mutableStateOf(ed?.let { it.linkUrl?.takeIf { l -> l.isNotBlank() } ?: it.fileUrl.takeIf { f -> isLinkUrl(f) } } ?: "") }
    var thumbMode by remember { mutableStateOf("image") }
    var thumbUri by remember { mutableStateOf<Uri?>(null) }
    var thumbText by remember { mutableStateOf("") }
    var thumbLinkIn by remember { mutableStateOf("") }
    var keepThumb by remember { mutableStateOf(ed?.thumbUrl != null) }
    var busy by remember { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { file = u; fileName = Posting.nameOf(u).ifBlank { "File" }; fileIsImage = Posting.mimeOf(u).startsWith("image/") }
    }
    val pickThumb = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) { thumbUri = u; keepThumb = false } }

    // Thumbnail row rules, same as the website
    val canThumb = if (ed != null) ((ed.hasRealFile && !isImageUrl(ed.fileUrl)) || (!ed.hasRealFile && isLinkUrl(ed.fileUrl)))
    else (if (file != null) !fileIsImage else linkOpen)

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) { detectTapGestures { close() } }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 40.dp).navigationBarsPadding().imePadding()
                .clip(RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp)).background(Panel2)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (ed != null) "Edit post" else "New post", 19.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(34.dp).pressable(true, 0.86f) { close() }.clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) { Glyph(G.CLOSE, 16.dp, Color.White) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InputBox(title, { title = it }, "Give your post a title…", maxLen = 80)
                InputBox(desc, { desc = it }, "What's this about?", singleLine = false, minHeight = 110.dp, maxLen = 1000)

                if (ed == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(if (file == null) "Attach file" else fileName.take(22), G.IMAGE, { pickFile.launch("*/*") }, on = file != null)
                        Pill(if (linkOpen) "Remove link" else "Add link", G.LINK, { linkOpen = !linkOpen; if (!linkOpen) link = "" }, on = linkOpen)
                        if (file != null) Pill("Clear", G.CLOSE, { file = null; fileName = ""; fileIsImage = false })
                    }
                }
                AnimatedVisibility(linkOpen, enter = expandVertically(tween(240)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
                    InputBox(link, { link = it }, "Paste a link  (example.com)", keyboard = KeyboardType.Uri)
                }

                AnimatedVisibility(canThumb, enter = expandVertically(tween(240)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        T("THUMBNAIL", 11.sp, FontWeight.Bold, Muted, spacing = 1.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Image", G.IMAGE, { thumbMode = "image" }, on = thumbMode == "image")
                            Pill("Text", G.EDIT, { thumbMode = "text" }, on = thumbMode == "text")
                            Pill("URL", G.LINK, { thumbMode = "url" }, on = thumbMode == "url")
                        }
                        if (thumbMode == "image") {
                            val shown: Any? = thumbUri ?: (if (keepThumb) ed?.thumbUrl else null)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                                    if (shown != null) AsyncImage(shown, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Glyph(G.IMAGE, 22.dp, Muted)
                                }
                                if (shown != null) {
                                    Pill("Change", null, { pickThumb.launch("image/*") })
                                    Pill("Remove", G.CLOSE, { thumbUri = null; keepThumb = false })
                                } else Pill("Choose cover image", G.IMAGE, { pickThumb.launch("image/*") })
                            }
                        } else if (thumbMode == "url") {
                            InputBox(thumbLinkIn, { thumbLinkIn = it }, "Image URL  (https://…/cover.jpg)", keyboard = KeyboardType.Uri)
                            val pv = normalizeLink(thumbLinkIn.trim())
                            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                                if (pv != null) AsyncImage(pv, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Glyph(G.IMAGE, 24.dp, Muted)
                            }
                        } else {
                            InputBox(thumbText, { thumbText = it }, "Text for the thumbnail card", maxLen = 90, minHeight = 48.dp)
                            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF171717)).padding(14.dp), contentAlignment = Alignment.Center) {
                                T(thumbText.uppercase().ifBlank { "PREVIEW" }, 28.sp, FontWeight.Black, Color(0xFFF5F5F5), align = TextAlign.Center, maxLines = 3, spacing = 1.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                BtnWhite(if (busy) "Please wait…" else if (ed != null) "Save changes" else "Publish", Modifier.width(170.dp), enabled = !busy) {
                    val raw = link.trim()
                    val norm = if (raw.isNotEmpty()) normalizeLink(raw) else null
                    if (raw.isNotEmpty() && norm == null) { App.toast("That link doesn't look right"); return@BtnWhite }
                    val tText = if (thumbMode == "text" && canThumb) thumbText.trim().ifBlank { null } else null
                    val tUri = if (thumbMode == "image" && canThumb) thumbUri else null
                    val tLink = if (thumbMode == "url" && canThumb && thumbLinkIn.isNotBlank()) normalizeLink(thumbLinkIn.trim()) else null
                    if (thumbMode == "url" && canThumb && thumbLinkIn.isNotBlank() && tLink == null) { App.toast("That thumbnail URL doesn't look right"); return@BtnWhite }
                    scope.launch {
                        busy = true
                        val ok = if (ed != null) Posting.saveEdit(ed, EditDraft(title.trim(), desc.trim(), norm, tUri, tText, keepThumb && tUri == null && tText == null && tLink == null, false, tLink))
                        else {
                            if (title.isBlank() && desc.isBlank() && file == null && norm == null) { busy = false; return@launch }
                            val dr = Draft(title.trim(), desc.trim(), norm, file, tUri, tText, tLink)
                            App.scope.launch { Posting.publish(dr) }
                            true
                        }
                        busy = false
                        if (ok) close()
                    }
                }
            }
        }
    }
}
