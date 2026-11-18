package com.xcluice.app.data

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.xcluice.app.state.App
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Firebase push on top of the polling service. Polling keeps running until the first real push arrives. */
object Fcm {
    fun register() {
        val u = App.user ?: return
        App.scope.launch {
            runCatching {
                val t = FirebaseMessaging.getInstance().token.await()
                if (Api.registerDevice(u.id, t)) App.markPushReady(t)
            }
        }
    }

    fun unregister() {
        val t = App.pushToken ?: return
        App.scope.launch { runCatching { Api.unregisterDevice(t) } }
        App.markPushReady(null)
    }
}

class XPushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        App.ensureInit(applicationContext)
        App.scope.launch { App.user?.let { if (Api.registerDevice(it.id, token)) App.markPushReady(token) } }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        App.ensureInit(applicationContext)
        App.markFcmSeen()
        val d = m.data
        val id = d["id"]?.toLongOrNull()
        when (d["type"]) {
            "message" -> if (id == null || App.advanceMsgMark(id)) {
                if (!App.foreground) Notifier.messages(applicationContext, listOf((d["title"] ?: "New message") to (d["body"] ?: "")))
            }
            "post" -> if (id == null || App.advancePostMark(id)) {
                if (!App.foreground) Notifier.postText(applicationContext, d["title"] ?: "New post", d["body"] ?: "")
            }
        }
    }
}
