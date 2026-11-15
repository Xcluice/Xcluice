package com.xcluice.app.state

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xcluice.app.data.Api
import com.xcluice.app.data.ChatUser
import com.xcluice.app.data.Cfg
import com.xcluice.app.data.FileType
import com.xcluice.app.data.Msg
import com.xcluice.app.data.Post
import com.xcluice.app.data.Reaction
import com.xcluice.app.data.Realtime
import com.xcluice.app.data.User
import com.xcluice.app.data.parseMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object App {
    private lateinit var prefs: SharedPreferences
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- navigation / session ----
    var introDone by mutableStateOf(false)
    var user by mutableStateOf<User?>(null)
    var guest by mutableStateOf(false)
    var tab by mutableIntStateOf(0)
    var viewing by mutableStateOf<Post?>(null)
    var composing by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)
    var editing by mutableStateOf<Post?>(null)
    var profile by mutableStateOf<com.xcluice.app.data.Who?>(null)

    // ---- feed ----
    var posts by mutableStateOf<List<Post>>(emptyList())
    var postsLoading by mutableStateOf(false)
    var postsMore by mutableStateOf(false)
    var filter by mutableIntStateOf(0)
    var liked by mutableStateOf<Set<Long>>(emptySet())
    var verifiedIds by mutableStateOf<Set<String>>(emptySet())
    var verifiedEmails by mutableStateOf<Set<String>>(emptySet())

    // ---- chat ----
    var chatUsers by mutableStateOf<List<ChatUser>>(emptyList())
    var chatUsersLoaded by mutableStateOf(false)
    var messages by mutableStateOf<List<Msg>>(emptyList())
    var reactions by mutableStateOf<List<Reaction>>(emptyList())
    var thread by mutableStateOf<ChatUser?>(null)
    var reply by mutableStateOf<Msg?>(null)
    var sheetMsg by mutableStateOf<Msg?>(null)
    var readMap by mutableStateOf<Map<String, Long>>(emptyMap())
    val typingFrom = mutableStateMapOf<String, Long>()
    val bursts = mutableStateMapOf<Long, Pair<Int, String>>()
    var flashId by mutableStateOf<Long?>(null)
    var toastMsg by mutableStateOf<String?>(null)

    private val seenIds = HashSet<Long>()
    val renderedIds = HashSet<Long>()
    private var lastId: Long? = null
    private var tempSeq = -1L
    private var reactionPending = 0
    private var lastTyping = 0L
    private var loopsOn = false
    private var loopJob: Job? = null
    private var rt: Realtime? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("xcluice", Context.MODE_PRIVATE)
        Api.token = prefs.getString("access", null)
        Api.refreshToken = prefs.getString("refresh", null)
        Api.expiresAt = prefs.getLong("exp", 0L)
        Api.onSession = { saveSession() }
        runCatching { readMap = JSONObject(prefs.getString("read", "{}") ?: "{}").let { o -> o.keys().asSequence().associateWith { k -> o.optLong(k) } } }
        prefs.getString("user", null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                user = User(o.getString("id"), o.optString("username"), o.optString("email"), o.optString("displayName"),
                    if (o.isNull("avatarUrl")) null else o.optString("avatarUrl"), o.optBoolean("verified"))
            }
        }
        if (Api.refreshToken == null) user = null
        guest = prefs.getBoolean("guest", false)
        scope.launch {
            val u = user
            if (u != null) {
                val fresh = Api.profile(u.id)
                if (fresh != null) { saveUser(fresh) }
                else if (Api.token == null) { user = null }
            }
            bootData()
        }
    }

    private fun saveSession() {
        prefs.edit().putString("access", Api.token).putString("refresh", Api.refreshToken).putLong("exp", Api.expiresAt).apply()
    }

    private fun saveUser(u: User) {
        user = u
        prefs.edit().putString("user", JSONObject().put("id", u.id).put("username", u.username).put("email", u.email)
            .put("displayName", u.displayName).put("avatarUrl", u.avatarUrl ?: JSONObject.NULL).put("verified", u.verified).toString()).apply()
    }

    fun toast(s: String) {
        toastMsg = s
        scope.launch { delay(2200); if (toastMsg == s) toastMsg = null }
    }

    // ---------------- auth ----------------
    suspend fun authenticate(rawUser: String, pass: String, register: Boolean, confirm: String): String? {
        val clean = rawUser.trim().lowercase().removeSuffix("@xcluice.io").removeSuffix("@mailinator.com")
            .removeSuffix("@fosthub.com").replace(Regex("[^a-z0-9_.]"), "")
        if (rawUser.isBlank() || pass.isEmpty()) return "Fields cannot be empty"
        if (clean.length < 3) return "Username min 3 characters"
        if (pass.length < 6) return "Password min 6 characters"
        if (register && pass != confirm) return "Passwords do not match"
        val email = "$clean@${Cfg.AUTH_DOMAIN}"
        val res = if (register) Api.signUp(email, pass, clean, clean.replaceFirstChar { it.uppercase() })
        else Api.signIn(email, pass)
        if (!res.ok || res.userId == null) return res.msg.ifBlank { "Something went wrong" }
        val prof = Api.profile(res.userId) ?: User(res.userId, clean, email, clean.replaceFirstChar { it.uppercase() }, null, false)
        saveUser(prof)
        guest = false
        prefs.edit().putBoolean("guest", false).apply()
        bootData()
        return null
    }

    fun continueAsGuest() { guest = true; prefs.edit().putBoolean("guest", true).apply(); scope.launch { bootData() } }

    fun signOut() {
        loopsOn = false; loopJob?.cancel(); rt?.stop(); rt = null
        Api.clearSession(); saveSession()
        user = null; guest = false; thread = null; tab = 0
        messages = emptyList(); reactions = emptyList(); chatUsers = emptyList(); chatUsersLoaded = false
        liked = emptySet(); seenIds.clear(); renderedIds.clear(); lastId = null
        prefs.edit().remove("user").putBoolean("guest", false).apply()
    }

    // ---------------- feed ----------------
    private var bootedFor: String? = null

    private suspend fun bootData() {
        val key = user?.id ?: "guest"
        if (bootedFor == key && posts.isNotEmpty()) return
        bootedFor = key
        loadPosts()
        scope.launch { val (a, b) = Api.verified(); verifiedIds = a; verifiedEmails = b }
        user?.let { u ->
            scope.launch { liked = Api.myLikes(u.id) }
            startChat()
        }
    }

    fun isVerified(uid: String?, email: String?): Boolean =
        (uid != null && uid in verifiedIds) || (!email.isNullOrBlank() && email.lowercase() in verifiedEmails)

    fun refresh() { scope.launch { loadPosts() } }

    suspend fun loadPosts() {
        postsLoading = true
        val rows = Api.posts(30)
        if (rows != null) {
            val pins = Api.pinned()
            val seen = rows.map { it.id }.toSet()
            val all = rows + pins.filter { it.id !in seen }
            posts = all.sortedWith(compareByDescending<Post> { it.pinned }.thenByDescending { it.createdMs })
            postsMore = rows.size == 30
        }
        postsLoading = false
    }

    fun loadMore() {
        if (postsLoading || !postsMore) return
        val last = posts.filter { !it.pinned }.minByOrNull { it.createdMs } ?: return
        scope.launch {
            postsLoading = true
            val rows = Api.posts(30, last.createdAt)
            if (rows != null) {
                val seen = posts.map { it.id }.toSet()
                posts = (posts + rows.filter { it.id !in seen }).sortedWith(compareByDescending<Post> { it.pinned }.thenByDescending { it.createdMs })
                postsMore = rows.size == 30
            }
            postsLoading = false
        }
    }

    fun visiblePosts(): List<Post> = when (filter) {
        1 -> posts.filter { it.type == FileType.MEDIA }
        2 -> posts.filter { it.type == FileType.DOCS }
        3 -> posts.filter { it.type == FileType.HTML }
        else -> posts
    }

    fun toggleLike(p: Post) {
        val u = user
        if (u == null) { toast("Sign in to like posts"); return }
        val was = p.id in liked
        liked = if (was) liked - p.id else liked + p.id
        posts = posts.map { if (it.id == p.id) it.copy(likes = maxOf(0, it.likes + if (was) -1 else 1)) else it }
        scope.launch { if (was) Api.unlike(p.id, u.id) else Api.like(p.id, u.id) }
    }

    fun openPost(p: Post) {
        viewing = p
        posts = posts.map { if (it.id == p.id) it.copy(taps = it.taps + 1) else it }
        scope.launch { Api.bumpTap(p.id) }
    }

    fun publish(subject: String, bytes: ByteArray?, mime: String?, ext: String?) {
        val u = user ?: return
        scope.launch {
            var url = ""
            if (bytes != null && mime != null) {
                val name = "${u.username}_${System.currentTimeMillis()}.${ext ?: "bin"}"
                url = Api.upload(name, mime, bytes) ?: run { toast("Upload failed"); return@launch }
            }
            val ok = Api.createPost(u, subject.take(60).ifBlank { "Post" }, subject, url)
            if (ok) { toast("Posted"); loadPosts() } else toast("Couldn't post")
        }
    }

    fun openProfile(w: com.xcluice.app.data.Who) { profile = w }

    fun editPost(p: Post, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        scope.launch {
            if (Api.updatePost(p.id, t.take(60), t)) {
                val f = { x: Post -> if (x.id == p.id) x.copy(title = t.take(60), subject = t) else x }
                posts = posts.map(f)
                viewing = viewing?.let(f)
                toast("Post updated")
            } else toast("Couldn't update post")
        }
    }

    fun deletePost(p: Post) {
        scope.launch {
            if (Api.deletePost(p.id)) { posts = posts.filter { it.id != p.id }; viewing = null; toast("Post deleted") } else toast("Delete failed")
        }
    }

    fun changeAvatar(bytes: ByteArray, mime: String, ext: String) {
        val u = user ?: return
        scope.launch {
            val url = Api.upload("avatar_${u.username}_${System.currentTimeMillis()}.$ext", mime, bytes)
            if (url == null) { toast("Upload failed"); return@launch }
            Api.updateAvatar(u, url)
            saveUser(u.copy(avatarUrl = url))
            Api.presence(user ?: u)
            toast("Photo updated")
        }
    }

    // ---------------- chat ----------------
    private fun startChat() {
        if (loopsOn) return
        loopsOn = true
        rt = Realtime { from, to ->
            val me = user?.id
            if (me != null && to == me) scope.launch { typingFrom[from] = System.currentTimeMillis() }
        }
        rt?.start(Api.token)
        loopJob = scope.launch {
            fetchMessages(); user?.let { Api.presence(it) }; fetchUsers(); syncState()
            var t = 0
            while (loopsOn) {
                delay(1000)
                t++
                tick = System.currentTimeMillis()
                if (t % 4 == 0) { fetchMessages(); syncState() }
                if (t % 25 == 0) user?.let { Api.presence(it) }
                if (t % 15 == 0) fetchUsers()
            }
        }
    }

    private suspend fun fetchUsers() {
        Api.chatUsers()?.let { chatUsers = it; chatUsersLoaded = true }
    }

    private suspend fun fetchMessages() {
        val rows = Api.messages(lastId) ?: return
        if (rows.isEmpty()) { return }
        val fresh = rows.filter { seenIds.add(it.id) }
        if (fresh.isEmpty()) return
        messages = messages.filter { !it.temp } + fresh
        lastId = rows.last().id
    }

    private suspend fun syncState() {
        if (Api.token == null) return
        if (reactionPending == 0) {
            Api.reactions()?.let { r ->
                fun sig(l: List<Reaction>) = l.map { "${it.messageId}:${it.uid}:${it.emoji}" }.sorted()
                if (sig(r) != sig(reactions)) reactions = r
            }
        }
        Api.liveMessageIds()?.let { ids ->
            if (ids.isNotEmpty()) {
                val set = ids.toSet(); val max = ids.first(); val min = if (ids.size >= 400) ids.last() else Long.MIN_VALUE
                val keep = messages.filter { it.temp || it.id > max || it.id < min || it.id in set }
                if (keep.size != messages.size) messages = keep
            }
        }
    }

    fun markRead(uid: String) {
        readMap = readMap + (uid to System.currentTimeMillis())
        prefs.edit().putString("read", JSONObject(readMap as Map<*, *>).toString()).apply()
    }

    fun openThread(u: ChatUser) { thread = u; reply = null; markRead(u.uid) }
    fun closeThread() { thread = null; reply = null; sheetMsg = null }

    fun threadMessages(): List<Msg> {
        val t = thread ?: return emptyList()
        val me = user?.id ?: return emptyList()
        return messages.filter { (it.uid == me && it.toUid == t.uid) || (it.uid == t.uid && it.toUid == me) }
    }

    fun sendText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        post(clean)
    }

    private fun post(text: String) {
        val u = user ?: return
        val to = thread?.uid ?: return
        val rep = reply; reply = null
        val tid = tempSeq--
        val now = System.currentTimeMillis()
        val temp = Msg(tid, u.id, u.displayName, text, to, rep?.id, java.time.Instant.now().toString(), now, true)
        messages = messages + temp
        scope.launch {
            val saved = Api.sendMessage(u.id, u.displayName, text, to, rep?.id?.takeIf { it > 0 })
            if (saved == null) {
                messages = messages.filter { it.id != tid }; toast("Message not sent"); return@launch
            }
            seenIds.add(saved.id); renderedIds.add(saved.id)
            messages = if (messages.any { it.id == saved.id }) messages.filter { it.id != tid }
            else messages.map { if (it.id == tid) saved else it }
            val l = lastId
            if (l == null || saved.id > l) lastId = saved.id
        }
    }

    fun sendVoice(f: File, secs: Int) {
        val u = user ?: return
        scope.launch {
            toast("Sending voice message…")
            val bytes = runCatching { f.readBytes() }.getOrNull()
            if (bytes == null) { toast("Voice upload failed"); return@launch }
            val url = Api.upload("voice_${u.username}_${System.currentTimeMillis()}.m4a", "audio/mp4", bytes)
            runCatching { f.delete() }
            if (url == null) { toast("Voice upload failed"); return@launch }
            post("[AUDIO::$url#d=${maxOf(1, secs)}]")
        }
    }

    fun onTypingInput() {
        val u = user ?: return
        val t = thread ?: return
        val now = System.currentTimeMillis()
        if (now - lastTyping < 1500) return
        lastTyping = now
        rt?.sendTyping(u.id, t.uid)
    }

    fun react(mid: Long, emoji: String) {
        val u = user ?: return
        if (mid <= 0) return
        val cur = reactions.firstOrNull { it.messageId == mid && it.uid == u.id }
        val same = cur != null && cur.emoji == emoji
        reactions = reactions.filter { !(it.messageId == mid && it.uid == u.id) } + if (same) emptyList<Reaction>() else listOf(Reaction(mid, u.id, emoji))
        if (!same) bursts[mid] = ((bursts[mid]?.first ?: 0) + 1) to emoji
        reactionPending++
        scope.launch {
            if (same) Api.clearReaction(mid, u.id) else Api.setReaction(mid, u.id, emoji)
            reactionPending--
        }
    }

    fun deleteMessage(m: Msg) {
        val u = user ?: return
        if (m.uid != u.id || m.id <= 0) return
        val old = messages
        messages = messages.filter { it.id != m.id }
        reactions = reactions.filter { it.messageId != m.id }
        scope.launch {
            if (!Api.deleteMessage(m.id)) { messages = old; toast("Couldn't delete message") }
        }
    }
}
