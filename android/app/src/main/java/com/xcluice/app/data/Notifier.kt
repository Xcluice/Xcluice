package com.xcluice.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xcluice.app.MainActivity
import com.xcluice.app.R
import com.xcluice.app.state.App
import java.util.concurrent.TimeUnit

object Notifier {
    private const val CH_MSG = "messages"
    private const val CH_POST = "posts"

    fun setup(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CH_POST, "New posts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "xc-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    private fun allowed(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(ctx: Context, tab: Int) = PendingIntent.getActivity(
        ctx, tab, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("tab", tab),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun messages(ctx: Context, items: List<Pair<String, String>>) {
        if (items.isEmpty() || !allowed(ctx)) return
        val byName = items.groupBy({ it.first }, { it.second })
        byName.entries.forEachIndexed { i, (name, texts) ->
            val style = NotificationCompat.InboxStyle()
            texts.takeLast(5).forEach { style.addLine(it) }
            val n = NotificationCompat.Builder(ctx, CH_MSG)
                .setSmallIcon(R.drawable.ic_notif).setContentTitle(name)
                .setContentText(texts.last()).setStyle(if (texts.size > 1) style else null)
                .setNumber(texts.size).setAutoCancel(true).setContentIntent(open(ctx, 1))
                .setPriority(NotificationCompat.PRIORITY_HIGH).build()
            runCatching { NotificationManagerCompat.from(ctx).notify(("m:$name").hashCode() + i, n) }
        }
    }

    fun postText(ctx: Context, title: String, text: String) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_POST)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open(ctx, 0)).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7001, n) }
    }

    fun posts(ctx: Context, items: List<Post>) {
        if (items.isEmpty() || !allowed(ctx)) return
        val p = items.first()
        val title = if (items.size == 1) "New post from ${p.authorName}" else "${items.size} new posts"
        val text = if (items.size == 1) p.headline else items.take(3).joinToString(" • ") { it.headline }
        val n = NotificationCompat.Builder(ctx, CH_POST)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open(ctx, 0)).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7001, n) }
    }
}

class CheckWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        if (!App.foreground && !App.pushActive()) runCatching { App.checkNotifications() }
        return Result.success()
    }
}
