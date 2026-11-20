package com.xcluice.app.data

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.InflaterInputStream

/** Applies an Xcluice "XPT1" patch (bsdiff + zlib, see android/tools/make_patch.py) to the installed APK. */
object BsPatch {
    private fun offtin(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 6 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        v = (v shl 8) or (b[o + 7].toLong() and 0x7F)
        return if (b[o + 7].toInt() and 0x80 != 0) -v else v
    }

    private fun inflate(src: ByteArray): InputStream = InflaterInputStream(ByteArrayInputStream(src), java.util.zip.Inflater(), 64 * 1024)

    private fun readFully(i: InputStream, b: ByteArray, off: Int, len: Int) {
        var n = 0
        while (n < len) { val r = i.read(b, off + n, len - n); if (r < 0) throw RuntimeException("short stream"); n += r }
    }

    /** Writes the patched APK to [out]; throws on any mismatch (caller falls back to a full download). */
    fun apply(oldApk: File, patch: ByteArray, out: File) {
        require(patch.size > 68 && String(patch, 0, 4) == "XPT1") { "bad patch" }
        val want = patch.copyOfRange(4, 36)
        val h = DataInputStream(ByteArrayInputStream(patch, 36, 32))
        val nsize = h.readLong(); val cl = h.readLong().toInt(); val dl = h.readLong().toInt(); val el = h.readLong().toInt()
        require(nsize in 1..400_000_000L && 68L + cl + dl + el == patch.size.toLong()) { "bad header" }
        val ctrl = inflate(patch.copyOfRange(68, 68 + cl))
        val diff = inflate(patch.copyOfRange(68 + cl, 68 + cl + dl))
        val extra = inflate(patch.copyOfRange(68 + cl + dl, patch.size))
        val old = oldApk.readBytes()
        val new = ByteArray(nsize.toInt())
        val c = ByteArray(24)
        var np = 0; var op = 0L
        while (np < new.size) {
            readFully(ctrl, c, 0, 24)
            val x = offtin(c, 0).toInt(); val y = offtin(c, 8).toInt(); val z = offtin(c, 16)
            require(x >= 0 && y >= 0 && np + x + y <= new.size) { "bad ctrl" }
            readFully(diff, new, np, x)
            for (i in 0 until x) { val p = op + i; if (p >= 0 && p < old.size) new[np + i] = (new[np + i] + old[p.toInt()]).toByte() }
            np += x; op += x
            readFully(extra, new, np, y)
            np += y; op += z
        }
        require(MessageDigest.getInstance("SHA-256").digest(new).contentEquals(want)) { "hash mismatch" }
        out.outputStream().use { it.write(new) }
    }
}
