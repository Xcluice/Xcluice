package com.xcluice.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Minimal Supabase Realtime (Phoenix) client, only for the "xc-typing" broadcast channel. */
class Realtime(private val onTyping: (fromUid: String, toUid: String) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var job: Job? = null
    private var ref = 1
    private var alive = false
    private val topic = "realtime:xc-typing"

    private fun nextRef(): String { ref += 1; return ref.toString() }

    fun start(accessToken: String?) {
        stop()
        alive = true
        job = scope.launch {
            while (isActive && alive) {
                connect(accessToken)
                delay(6000)
            }
        }
    }

    fun stop() {
        alive = false
        job?.cancel(); job = null
        runCatching { ws?.close(1000, "bye") }
        ws = null
    }

    private suspend fun connect(accessToken: String?) {
        val url = "wss://nzolsmouyulzqeaocejm.supabase.co/realtime/v1/websocket?apikey=${Cfg.KEY}&vsn=1.0.0"
        val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val cfg = JSONObject()
                    .put("config", JSONObject()
                        .put("broadcast", JSONObject().put("self", false))
                        .put("presence", JSONObject().put("key", ""))
                        .put("postgres_changes", org.json.JSONArray()))
                if (accessToken != null) cfg.put("access_token", accessToken)
                val msg = JSONObject().put("topic", topic).put("event", "phx_join")
                    .put("payload", cfg).put("ref", "1").put("join_ref", "1")
                webSocket.send(msg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val o = JSONObject(text)
                    if (o.optString("event") == "broadcast") {
                        val p = o.optJSONObject("payload") ?: return
                        if (p.optString("event") == "typing") {
                            val d = p.optJSONObject("payload") ?: return
                            onTyping(d.optString("uid"), d.optString("to_uid"))
                        }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
        }
        ws = Api.http.newWebSocket(Request.Builder().url(url).build(), listener)
        val hb = scope.launch {
            while (isActive) {
                delay(25000)
                val m = JSONObject().put("topic", "phoenix").put("event", "heartbeat")
                    .put("payload", JSONObject()).put("ref", nextRef())
                ws?.send(m.toString())
            }
        }
        closed.await()
        hb.cancel()
    }

    fun sendTyping(fromUid: String, toUid: String) {
        val inner = JSONObject().put("type", "broadcast").put("event", "typing")
            .put("payload", JSONObject().put("uid", fromUid).put("to_uid", toUid))
        val m = JSONObject().put("topic", topic).put("event", "broadcast")
            .put("payload", inner).put("ref", nextRef())
        ws?.send(m.toString())
    }
}
