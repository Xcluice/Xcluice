package com.xcluice.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

/** One-tap update: downloads the new APK inside the app (with progress) and opens the system installer. */
object Updater {
    /** 0f..1f while downloading, -1f idle, 2f = ready to install */
    var progress by mutableStateOf(-1f)
    var error by mutableStateOf<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    fun start(ctx: Context, url: String) {
        val app = ctx.applicationContext
        // Android asks once for "install unknown apps" permission; send the user there and resume on return
        if (android.os.Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            error = "Allow Xcluice to install updates, then tap Update again"
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (running) return
        running = true; error = null; progress = 0f
        scope.launch {
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, "Xcluice.apk")
                if (tryPatch(app, url, out)) { progress = 2f; install(app, out); return@launch }
                progress = 0f
                Api.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) throw RuntimeException("HTTP " + r.code)
                    val body = r.body ?: throw RuntimeException("empty")
                    val total = body.contentLength().coerceAtLeast(1L)
                    var got = 0L
                    body.byteStream().use { ins -> out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            os.write(buf, 0, n); got += n
                            progress = (got.toFloat() / total).coerceIn(0f, 0.99f)
                        }
                    } }
                }
                progress = 2f
                install(app, out)
            } catch (e: Exception) {
                error = "Download failed. Check your connection and tap Update again"
                progress = -1f
            } finally { running = false }
        }
    }

    /** Incremental update: fetch patch-from-<installed build> (a few hundred KB), apply to the installed APK. */
    private fun tryPatch(app: Context, url: String, out: File): Boolean = try {
        @Suppress("DEPRECATION")
        val cur = app.packageManager.getPackageInfo(app.packageName, 0).versionCode
        val purl = url.substringBeforeLast('/') + "/patch-from-$cur.bin"
        val data = Api.http.newCall(Request.Builder().url(purl).build()).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val body = r.body ?: return@use null
            val total = body.contentLength().coerceAtLeast(1L)
            val bo = java.io.ByteArrayOutputStream()
            body.byteStream().use { ins ->
                val buf = ByteArray(32 * 1024); var got = 0L
                while (true) { val n = ins.read(buf); if (n < 0) break; bo.write(buf, 0, n); got += n; progress = (got.toFloat() / total * 0.9f).coerceIn(0f, 0.9f) }
            }
            bo.toByteArray()
        }
        if (data == null) false else {
            BsPatch.apply(File(app.applicationInfo.sourceDir), data, out)
            true
        }
    } catch (e: Throwable) { out.delete(); false }

    fun install(app: Context, f: File) {
        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", f)
        app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun reinstall(app: Context) {
        val f = File(File(app.cacheDir, "updates"), "Xcluice.apk")
        if (f.exists() && f.length() > 0) install(app, f)
    }
}
