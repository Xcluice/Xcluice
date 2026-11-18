package com.xcluice.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xcluice.app.R
import com.xcluice.app.state.App
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Firebase-free push: a lightweight foreground service that checks Supabase while the app is in the background. */
object Push {
    fun register() = start(App.appContext())
    fun unregister() { val c = App.appContext(); c.stopService(Intent(c, NotifyService::class.java)) }

    fun start(ctx: Context) {
        if (App.fcmConfirmed()) return
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, NotifyService::class.java)) }
    }
}

class NotifyService : Service() {
    private var job: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        App.ensureInit(applicationContext)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "Background connection", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
            )
        }
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle("Xcluice")
            .setContentText("Listening for new messages").setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive != true) {
            App.svcRunning = true
            job = App.scope.launch {
                while (isActive) {
                    if (App.user == null || App.fcmConfirmed()) { stopSelf(); break }
                    if (!App.foreground) runCatching { App.checkNotifications() }
                    delay(15_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { job?.cancel(); App.svcRunning = false; super.onDestroy() }

    companion object { private const val CH = "xc-service" }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val signedIn = ctx.getSharedPreferences("xcluice", Context.MODE_PRIVATE).getString("user", null) != null
        if (signedIn) Push.start(ctx)
    }
}
