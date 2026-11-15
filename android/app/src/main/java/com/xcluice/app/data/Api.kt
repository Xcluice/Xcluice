package com.xcluice.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class AuthRes(val ok: Boolean, val msg: String = "", val userId: String? = null)

fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

object Api {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val JSON_T = "application/json".toMediaType()
    private val lock = Mutex()

    var token: String? = null
    var refreshToken: String? = null
    var expiresAt: Long = 0L
    var onSession: (() -> Unit)? = null

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun req(path: String): Request.Builder {
        val b = Request.Builder().url(Cfg.SB + path).header("apikey", Cfg.KEY)
        token?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private suspend fun refreshIfNeeded(force: Boolean = false) {
        val rt = refreshToken ?: return
        if (!force && System.currentTimeMillis() < expiresAt - 60_000) return
        lock.withLock {
            if (!force && System.currentTimeMillis() < expiresAt - 60_000) return
            val body = JSONObject().put("refresh_token", rt).toString().toRequestBody(JSON_T)
            val r = Request.Builder().url(Cfg.SB + "/auth/v1/token?grant_type=refresh_token")
                .header("apikey", Cfg.KEY).post(body).build()
            val res = withContext(Dispatchers.IO) {
                try { http.newCall(r).execute().use { it.code to (it.body?.string() ?: "") } }
                catch (e: Exception) { -1 to "" }
            }
            if (res.first in 200..299) applySession(JSONObject(res.second))
            else if (res.first == 400 || res.first == 401) { token = null; refreshToken = null; onSession?.invoke() }
        }
    }

    private fun applySession(o: JSONObject) {
        token = o.str("access_token")
        refreshToken = o.str("refresh_token") ?: refreshToken
        val exp = o.optLong("expires_in", 3600)
        expiresAt = System.currentTimeMillis() + exp * 1000
        onSession?.invoke()
    }

    private suspend fun call(build: () -> Request.Builder): Pair<Int, String> {
        refreshIfNeeded()
        var res = run(build)
        if (res.first == 401 && refreshToken != null) {
            refreshIfNeeded(true)
            res = run(build)
        }
        return res
    }

    private suspend fun run(build: () -> Request.Builder): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            http.newCall(build().build()).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: Exception) {
            -1 to ""
        }
    }

    private suspend fun getArr(path: String): JSONArray? {
        val (c, b) = call { req(path).get() }
        if (c !in 200..299) return null
        return try { JSONArray(b) } catch (e: Exception) { null }
    }

    private fun jsonBody(o: Any) = o.toString().toRequestBody(JSON_T)

    // ---------- auth ----------
    suspend fun signIn(email: String, pass: String): AuthRes {
        val body = JSONObject().put("email", email).put("password", pass)
        val r = Request.Builder().url(Cfg.SB + "/auth/v1/token?grant_type=password")
            .header("apikey", Cfg.KEY).post(jsonBody(body)).build()
        val res = withContext(Dispatchers.IO) {
            try { http.newCall(r).execute().use { it.code to (it.body?.string() ?: "") } }
            catch (e: Exception) { -1 to "" }
        }
        if (res.first == -1) return AuthRes(false, "No connection")
        if (res.first !in 200..299) return AuthRes(false, "Invalid credentials")
        val o = JSONObject(res.second)
        applySession(o)
        return AuthRes(true, "", o.optJSONObject("user")?.str("id"))
    }

    suspend fun signUp(email: String, pass: String, username: String, display: String): AuthRes {
        val body = JSONObject().put("email", email).put("password", pass)
            .put("data", JSONObject().put("username", username).put("display_name", display))
        val r = Request.Builder().url(Cfg.SB + "/auth/v1/signup")
            .header("apikey", Cfg.KEY).post(jsonBody(body)).build()
        val res = withContext(Dispatchers.IO) {
            try { http.newCall(r).execute().use { it.code to (it.body?.string() ?: "") } }
            catch (e: Exception) { -1 to "" }
        }
        if (res.first == -1) return AuthRes(false, "No connection")
        if (res.first !in 200..299) {
            val m = runCatching { JSONObject(res.second).let { it.str("msg") ?: it.str("error_description") ?: "" } }.getOrDefault("")
            return AuthRes(false, if (m.contains("already", true)) "Username already taken" else m.ifBlank { "Couldn't create account" })
        }
        val o = JSONObject(res.second)
        if (o.str("access_token") == null) return AuthRes(false, "Email confirmation is on for this project")
        applySession(o)
        return AuthRes(true, "", o.optJSONObject("user")?.str("id"))
    }

    fun clearSession() { token = null; refreshToken = null; expiresAt = 0L }

    suspend fun profile(id: String): User? {
        for (i in 0 until 4) {
            val a = getArr("/rest/v1/fosthub_users?id=eq.$id&select=id,username,email,display_name,role,avatar_url,verified")
                ?: return null
            if (a.length() > 0) {
                val o = a.getJSONObject(0)
                return User(
                    id = o.optString("id"),
                    username = o.str("username") ?: "",
                    email = o.str("email") ?: "",
                    displayName = o.str("display_name") ?: o.str("username") ?: "User",
                    avatarUrl = o.str("avatar_url"),
                    verified = o.optBoolean("verified", false)
                )
            }
            kotlinx.coroutines.delay(300)
        }
        return null
    }

    suspend fun updateAvatar(user: User, url: String) {
        call { req("/rest/v1/fosthub_users?id=eq.${user.id}").header("Content-Type", "application/json").patch(jsonBody(JSONObject().put("avatar_url", url))) }
        call { req("/rest/v1/homework?author_email=eq.${enc(user.email)}").patch(jsonBody(JSONObject().put("author_photo", url))) }
        call { req("/rest/v1/chat_users?uid=eq.${user.id}").patch(jsonBody(JSONObject().put("photo", url))) }
    }

    suspend fun verified(): Pair<Set<String>, Set<String>> {
        val ids = HashSet<String>(); val emails = HashSet<String>()
        getArr("/rest/v1/fosthub_users?select=id&verified=eq.true")?.let { a ->
            for (i in 0 until a.length()) ids.add(a.getJSONObject(i).optString("id"))
        }
        val (c, b) = call { req("/rest/v1/rpc/verified_post_emails").post(jsonBody(JSONObject())) }
        if (c in 200..299) runCatching {
            val a = JSONArray(b)
            for (i in 0 until a.length()) emails.add(a.optString(i).lowercase())
        }
        return ids to emails
    }

    // ---------- posts ----------
    private fun parsePosts(a: JSONArray): List<Post> {
        val out = ArrayList<Post>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val created = o.str("created_at") ?: ""
            out.add(
                Post(
                    id = o.optLong("id"),
                    title = o.str("title") ?: "",
                    subject = o.str("subject") ?: "",
                    fileUrl = o.str("file_url") ?: "",
                    authorName = o.str("author_name") ?: o.str("title") ?: "Anonymous",
                    authorEmail = o.str("author_email") ?: "",
                    authorPhoto = o.str("author_photo"),
                    createdAt = created,
                    createdMs = parseMs(created),
                    likes = o.optLong("likes", 0),
                    taps = o.optLong("taps", 0),
                    pinned = o.optBoolean("pinned", false)
                )
            )
        }
        return out
    }

    suspend fun posts(limit: Int, before: String? = null): List<Post>? {
        var p = "/rest/v1/homework?order=created_at.desc&limit=$limit"
        if (before != null) p += "&created_at=lt.${enc(before)}"
        return getArr(p)?.let { parsePosts(it) }
    }

    suspend fun postsByAuthor(email: String): List<Post>? =
        getArr("/rest/v1/homework?author_email=eq.${enc(email)}&order=created_at.desc&limit=100")?.let { parsePosts(it) }

    suspend fun updatePost(id: Long, title: String, subject: String, fileUrl: String? = null): Boolean {
        val o = JSONObject().put("title", title).put("subject", subject)
        if (fileUrl != null) o.put("file_url", fileUrl)
        return call { req("/rest/v1/homework?id=eq.$id").header("Content-Type", "application/json").patch(jsonBody(o)) }.first in 200..299
    }

    suspend fun pinned(): List<Post> =
        getArr("/rest/v1/homework?order=created_at.desc&pinned=eq.true")?.let { parsePosts(it) } ?: emptyList()

    suspend fun createPost(user: User, title: String, subject: String, fileUrl: String): Boolean {
        val o = JSONObject().put("title", title).put("subject", subject).put("file_url", fileUrl)
            .put("author_name", user.displayName).put("author_email", user.email)
            .put("author_photo", user.avatarUrl ?: JSONObject.NULL)
        val (c, _) = call { req("/rest/v1/homework").header("Content-Type", "application/json").header("Prefer", "return=minimal").post(jsonBody(o)) }
        return c in 200..299
    }

    suspend fun deletePost(id: Long): Boolean =
        call { req("/rest/v1/homework?id=eq.$id").delete() }.first in 200..299

    suspend fun myLikes(uid: String): Set<Long> {
        val a = getArr("/rest/v1/post_likes?uid=eq.${enc(uid)}&select=post_id&limit=3000") ?: return emptySet()
        val s = HashSet<Long>()
        for (i in 0 until a.length()) s.add(a.getJSONObject(i).optLong("post_id"))
        return s
    }

    suspend fun like(postId: Long, uid: String): Boolean =
        call { req("/rest/v1/post_likes").header("Prefer", "return=minimal").post(jsonBody(JSONObject().put("post_id", postId).put("uid", uid))) }.first in 200..299

    suspend fun unlike(postId: Long, uid: String): Boolean =
        call { req("/rest/v1/post_likes?post_id=eq.$postId&uid=eq.${enc(uid)}").delete() }.first in 200..299

    suspend fun bumpTap(id: Long) {
        call { req("/rest/v1/rpc/bump_post_tap").post(jsonBody(JSONObject().put("p_id", id))) }
    }

    suspend fun upload(name: String, mime: String, bytes: ByteArray): String? {
        val (c, _) = call {
            req("/storage/v1/object/${Cfg.BUCKET}/$name").post(bytes.toRequestBody(mime.toMediaType()))
        }
        return if (c in 200..299) "${Cfg.SB}/storage/v1/object/public/${Cfg.BUCKET}/$name" else null
    }

    // ---------- chat ----------
    suspend fun chatUsers(): List<ChatUser>? {
        val a = getArr("/rest/v1/chat_users?order=last_active.desc") ?: return null
        val out = ArrayList<ChatUser>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(ChatUser(o.optString("uid"), o.str("name") ?: "?", o.str("email") ?: "", o.str("photo"), o.str("last_active")))
        }
        return out
    }

    suspend fun presence(u: User) {
        val o = JSONObject().put("uid", u.id).put("name", u.displayName).put("email", u.email)
            .put("photo", u.avatarUrl ?: JSONObject.NULL)
            .put("last_active", java.time.Instant.now().toString())
        call { req("/rest/v1/chat_users").header("Content-Type", "application/json").header("Prefer", "resolution=merge-duplicates").post(jsonBody(o)) }
    }

    private fun parseMsgs(a: JSONArray): List<Msg> {
        val out = ArrayList<Msg>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val created = o.str("created_at") ?: ""
            out.add(
                Msg(
                    id = o.optLong("id"),
                    uid = o.str("uid") ?: "",
                    name = o.str("name") ?: "",
                    text = o.str("text") ?: "",
                    toUid = o.str("to_uid"),
                    replyTo = if (o.isNull("reply_to")) null else o.optLong("reply_to"),
                    createdAt = created,
                    createdMs = parseMs(created)
                )
            )
        }
        return out
    }

    /** afterId == null -> newest 100 (returned oldest first); else everything newer. null = request failed */
    suspend fun messages(afterId: Long?): List<Msg>? {
        val path = if (afterId != null) "/rest/v1/chat_messages?id=gt.$afterId&order=created_at.asc"
        else "/rest/v1/chat_messages?order=created_at.desc&limit=100"
        val a = getArr(path) ?: return null
        val l = parseMsgs(a)
        return if (afterId == null) l.reversed() else l
    }

    suspend fun sendMessage(uid: String, name: String, text: String, toUid: String?, replyTo: Long?): Msg? {
        val o = JSONObject().put("uid", uid).put("name", name).put("text", text)
            .put("to_uid", toUid ?: JSONObject.NULL)
        if (replyTo != null) o.put("reply_to", replyTo)
        val (c, b) = call { req("/rest/v1/chat_messages").header("Content-Type", "application/json").header("Prefer", "return=representation").post(jsonBody(o)) }
        if (c !in 200..299) return null
        return runCatching { parseMsgs(JSONArray(b)).firstOrNull() }.getOrNull()
    }

    suspend fun deleteMessage(id: Long): Boolean =
        call { req("/rest/v1/chat_messages?id=eq.$id").delete() }.first in 200..299

    suspend fun reactions(): List<Reaction>? {
        val a = getArr("/rest/v1/chat_reactions?select=message_id,uid,emoji&limit=2000") ?: return null
        val out = ArrayList<Reaction>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Reaction(o.optLong("message_id"), o.optString("uid"), o.optString("emoji")))
        }
        return out
    }

    suspend fun liveMessageIds(): List<Long>? {
        val a = getArr("/rest/v1/chat_messages?select=id&order=id.desc&limit=400") ?: return null
        val out = ArrayList<Long>()
        for (i in 0 until a.length()) out.add(a.getJSONObject(i).optLong("id"))
        return out
    }

    suspend fun setReaction(mid: Long, uid: String, emoji: String) {
        val o = JSONObject().put("message_id", mid).put("uid", uid).put("emoji", emoji)
        call { req("/rest/v1/chat_reactions?on_conflict=message_id,uid").header("Content-Type", "application/json").header("Prefer", "resolution=merge-duplicates,return=minimal").post(jsonBody(o)) }
    }

    suspend fun clearReaction(mid: Long, uid: String) {
        call { req("/rest/v1/chat_reactions?message_id=eq.$mid&uid=eq.${enc(uid)}").delete() }
    }

    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
        } catch (e: Exception) { null }
    }
}
