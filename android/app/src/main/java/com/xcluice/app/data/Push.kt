package com.xcluice.app.data

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.xcluice.app.state.App
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object Push {
    /** Fetch the FCM token and register it for the signed-in user. Silently does nothing if Firebase isn't configured. */
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
        App.scope.launch { runCatching { Api.unregisterDevice(t) }; App.markPushReady(null) }
    }
}

class XPushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        App.ensureInit(applicationContext)
        App.scope.launch { App.user?.let { if (Api.registerDevice(it.id, token)) App.markPushReady(token) } }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        App.ensureInit(applicationContext)
        val d = m.data
        val id = d["id"]?.toLongOrNull()
        when (d["type"]) {
            "message" -> {
                if (!App.foreground) Notifier.messages(applicationContext, listOf((d["title"] ?: "New message") to (d["body"] ?: "")))
                id?.let { App.advanceMsgMark(it) }
            }
            "post" -> {
                if (!App.foreground) Notifier.postText(applicationContext, d["title"] ?: "New post", d["body"] ?: "")
                id?.let { App.advancePostMark(it) }
            }
        }
    }
}
