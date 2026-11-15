package com.xcluice.app.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Tiny synthesized UI sounds, no asset files. */
object Sfx {
    enum class K { SEND, RECV, LIKE, TAP, STORY, DONE }

    @Volatile var enabled = true
    private const val RATE = 22050
    private val cache = HashMap<K, ShortArray>()

    private fun tone(parts: List<Triple<Float, Float, Float>>): ShortArray {
        // parts: (freqHz, startSec, lenSec)
        val total = parts.maxOf { it.second + it.third }
        val out = FloatArray((total * RATE).toInt() + 1)
        for ((f, st, len) in parts) {
            val s0 = (st * RATE).toInt()
            val n = (len * RATE).toInt()
            for (i in 0 until n) {
                val t = i / RATE.toFloat()
                val env = (1f - i / n.toFloat()).let { it * it } * (1f - exp(-t * 600f))
                val v = sin(2.0 * PI * f * t).toFloat() * 0.6f + sin(4.0 * PI * f * t).toFloat() * 0.12f
                if (s0 + i < out.size) out[s0 + i] += v * env * 0.5f
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1f, 1f) * 32000).toInt().toShort() }
    }

    private fun build(k: K): ShortArray = when (k) {
        K.SEND -> tone(listOf(Triple(660f, 0f, 0.07f), Triple(990f, 0.05f, 0.1f)))
        K.RECV -> tone(listOf(Triple(880f, 0f, 0.09f), Triple(1175f, 0.08f, 0.16f)))
        K.LIKE -> tone(listOf(Triple(520f, 0f, 0.05f), Triple(780f, 0.04f, 0.09f)))
        K.TAP -> tone(listOf(Triple(700f, 0f, 0.035f)))
        K.STORY -> tone(listOf(Triple(440f, 0f, 0.06f), Triple(660f, 0.05f, 0.06f), Triple(880f, 0.1f, 0.1f)))
        K.DONE -> tone(listOf(Triple(784f, 0f, 0.08f), Triple(988f, 0.07f, 0.08f), Triple(1319f, 0.14f, 0.16f)))
    }

    fun play(k: K) {
        if (!enabled) return
        Thread {
            runCatching {
                val pcm = synchronized(cache) { cache.getOrPut(k) { build(k) } }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setNotificationMarkerPosition(pcm.size)
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) { t.release() }
                    override fun onPeriodicNotification(t: AudioTrack) {}
                })
                track.play()
            }
        }.start()
    }
}
