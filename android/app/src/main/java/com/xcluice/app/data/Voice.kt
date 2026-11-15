package com.xcluice.app.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** One shared voice-note player. */
object VoicePlayer {
    var playingId by mutableStateOf<Long?>(null)
    var progress by mutableFloatStateOf(0f)
    var seconds by mutableFloatStateOf(0f)
    private var mp: MediaPlayer? = null
    private var loop: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    fun toggle(id: Long, url: String) {
        if (playingId == id) { stop(); return }
        stop()
        val p = MediaPlayer()
        mp = p
        playingId = id
        progress = 0f; seconds = 0f
        try {
            p.setDataSource(url)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepareAsync()
        } catch (e: Exception) { stop(); return }
        loop = scope.launch {
            while (true) {
                delay(80)
                val m = mp ?: break
                try {
                    if (m.isPlaying) {
                        val d = m.duration
                        seconds = m.currentPosition / 1000f
                        progress = if (d > 0) m.currentPosition.toFloat() / d else 0f
                    }
                } catch (e: Exception) { break }
            }
        }
    }

    fun stop() {
        loop?.cancel(); loop = null
        runCatching { mp?.release() }
        mp = null
        playingId = null
        progress = 0f; seconds = 0f
    }
}

/** Voice recorder -> .m4a (AAC). */
class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        return try {
            val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(32000)
            r.setAudioSamplingRate(16000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r; file = f; startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { rec?.release() }
            rec = null; file = null
            false
        }
    }

    /** returns the file and its length in seconds, or null if cancelled / too short */
    fun stop(cancel: Boolean): Pair<File, Int>? {
        val r = rec ?: return null
        val f = file
        rec = null; file = null
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        try { r.stop() } catch (e: Exception) { runCatching { f?.delete() }; runCatching { r.release() }; return null }
        runCatching { r.release() }
        if (cancel || f == null || secs < 1) { runCatching { f?.delete() }; return null }
        return f to secs
    }
}
