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
import com.xcluice.app.data.Profile
import com.xcluice.app.data.Story
import com.xcluice.app.data.Av
import org.json.JSONArray
import com.xcluice.app.data.Realtime
import com.xcluice.app.data.User
import com.xcluice.app.data.parseMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.xcluice.app.data.isImageUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class Confirm(val msg: String, val ok: String, val danger: Boolean, val onOk: () -> Unit)

object App {
    private lateinit var prefs: SharedPreferences
    lateinit var appCtx: Context
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
    var profiles by mutableStateOf<List<Profile>>(emptyList())
    var follows by mutableStateOf<List<Pair<String, String>>>(emptyList())
    val counts = mutableStateMapOf<String, Pair<Long?, Long?>>()
    var stories by mutableStateOf<List<Story>>(emptyList())
    var seen by mutableStateOf<Set<Long>>(emptySet())
    var storyQueue by mutableStateOf<List<String>?>(null)
    var storyU by mutableIntStateOf(0)
    var storyI by mutableIntStateOf(0)
    var storyDraft by mutableStateOf<android.net.Uri?>(null)
    var search by mutableStateOf("")
    var editingProfile by mutableStateOf(false)
    var ready by mutableStateOf(false)
    @Volatile var foreground = true
    private var firstOpen = true
    var confirm by mutableStateOf<Confirm?>(null)

    // ---- feed ----
    var posts by mutableStateOf<List<Post>>(emptyList())
    var postsLoading by mutableStateOf(false)
    var postsError by mutableStateOf<String?>(null)
    var showSkeleton by mutableStateOf(false)
    private fun netMsg() = if (Api.offline) "No internet connection" else "Server didn't respond. Try again"
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
    private var backgrounded = false
    private var loopJob: Job? = null
    private var rt: Realtime? = null

    private var inited = false
    fun ensureInit(ctx: Context) { if (!inited) init(ctx) }

    var pushToken: String? = null
    fun pushActive(): Boolean = prefs.getString("push_token", null) != null
    fun markPushReady(t: String?) { pushToken = t; prefs.edit().apply { if (t == null) remove("push_token") else putString("push_token", t) }.apply() }
    fun advanceMsgMark(id: Long) { if (id > prefs.getLong("n_msg", -1L)) prefs.edit().putLong("n_msg", id).apply() }
    fun advancePostMark(id: Long) { if (id > prefs.getLong("n_post", -1L)) prefs.edit().putLong("n_post", id).apply() }

    fun init(ctx: Context) {
        inited = true
        appCtx = ctx.applicationContext
        prefs = ctx.getSharedPreferences("xcluice", Context.MODE_PRIVATE)
        runCatching { val a = JSONArray(prefs.getString("seen", "[]")); seen = (0 until a.length()).map { a.getLong(it) }.toSet() }
        Api.token = prefs.getString("access", null)
        Api.refreshToken = prefs.getString("refresh", null)
        Api.expiresAt = prefs.getLong("exp", 0L)
        Api.onSession = { saveSession() }
        runCatching { readMap = JSONObject(prefs.getString("read", "{}") ?: "{}").let { o -> o.keys().asSequence().associateWith { k -> o.optLong(k) } } }
        prefs.getString("user", null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                user = User(o.getString("id"), o.optString("username"), o.optString("email"), o.optString("displayName"),
                    if (o.isNull("avatarUrl")) null else o.optString("avatarUrl"), o.optBoolean("verified"), o.optString("role"))
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
            .put("displayName", u.displayName).put("avatarUrl", u.avatarUrl ?: JSONObject.NULL).put("verified", u.verified).put("role", u.role).toString()).apply()
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
        val pt = pushToken ?: prefs.getString("push_token", null)
        markPushReady(null)
        scope.launch {
            if (pt != null) runCatching { kotlinx.coroutines.withTimeoutOrNull(3000) { Api.unregisterDevice(pt) } }
            Api.clearSession(); saveSession()
        }
        user = null; guest = false; thread = null; tab = 0
        messages = emptyList(); reactions = emptyList(); chatUsers = emptyList(); chatUsersLoaded = false
        follows = emptyList(); stories = emptyList(); storyQueue = null; profile = null
        liked = emptySet(); seenIds.clear(); renderedIds.clear(); lastId = null
        prefs.edit().remove("user").putBoolean("guest", false).apply()
    }

    // ---------------- feed ----------------
    private var bootedFor: String? = null

    private suspend fun bootData() {
        val key = user?.id ?: "guest"
        if (bootedFor == key && posts.isNotEmpty()) { ready = true; return }
        bootedFor = key
        val jobs = mutableListOf<Job>()
        jobs += scope.launch { loadPosts() }
        jobs += scope.launch { Api.profiles(user != null)?.let { applyProfiles(it) }; Api.stories()?.let { stories = it } }
        jobs += scope.launch { val (a, b) = Api.verified(); verifiedIds = a; verifiedEmails = b }
        user?.let { u ->
            jobs += scope.launch { liked = Api.myLikes(u.id) }
            jobs += scope.launch { user?.let { x -> Api.follows(x.id)?.let { follows = it } } }
            jobs += scope.launch { fetchUsers() }
            startChat()
            com.xcluice.app.data.Push.register()
        }
        jobs.forEach { it.join() }
        prefetchImages()
        ready = true
    }

    private fun prefetchImages() {
        val loader = coil.Coil.imageLoader(appCtx)
        val urls = posts.take(8).flatMap { listOf(it.thumbUrl, it.authorPhoto, it.fileUrl.takeIf { u -> isImageUrl(u) }) } +
            stories.take(6).map { it.mediaUrl.takeIf { _ -> it.kind == "image" } }
        urls.filterNotNull().filter { it.isNotBlank() }.distinct().take(20).forEach {
            loader.enqueue(coil.request.ImageRequest.Builder(appCtx).data(it).build())
        }
    }

    private fun notifyFresh(fresh: List<Msg>) {
        val me = user?.id ?: return
        val base = prefs.getLong("n_msg", -1L)
        val max = fresh.maxOf { it.id }
        if (base < 0) { prefs.edit().putLong("n_msg", max).apply(); return }
        val mine = fresh.filter { it.id > base && it.uid != me && it.toUid == me }
        if (max > base) prefs.edit().putLong("n_msg", max).apply()
        if (!foreground) com.xcluice.app.data.Notifier.messages(appCtx, mine.map { it.name to (if (it.isAudio) "Voice message" else it.text) })
    }

    /** Background check used by WorkManager (also safe to call while the process is alive). */
    suspend fun checkNotifications() {
        val me = user ?: return
        if (Api.token == null) return
        val base = prefs.getLong("n_msg", -1L)
        if (base < 0) { Api.messages(null)?.maxOfOrNull { it.id }?.let { prefs.edit().putLong("n_msg", it).apply() } }
        else {
            val rows = Api.messages(base)
            if (rows != null && rows.isNotEmpty()) {
                prefs.edit().putLong("n_msg", rows.maxOf { it.id }).apply()
                com.xcluice.app.data.Notifier.messages(appCtx, rows.filter { it.uid != me.id && it.toUid == me.id }.map { it.name to (if (it.isAudio) "Voice message" else it.text) })
            }
        }
        val pb = prefs.getLong("n_post", -1L)
        val latest = Api.posts(10)
        if (latest != null && latest.isNotEmpty()) {
            val max = latest.maxOf { it.id }
            if (pb < 0) prefs.edit().putLong("n_post", max).apply()
            else if (max > pb) {
                prefs.edit().putLong("n_post", max).apply()
                com.xcluice.app.data.Notifier.posts(appCtx, latest.filter { it.id > pb && !it.authorEmail.equals(me.email, true) })
            }
        }
    }

    /** Called every time the app comes to the foreground. */
    fun onOpen() {
        if (firstOpen) { firstOpen = false; return }
        if (!ready) return
        scope.launch {
            launch { loadPosts() }
            loadSocial()
            if (user != null) { launch { fetchMessages(); syncState() }; launch { fetchUsers() }; launch { user?.let { Api.presence(it) } } }
        }
    }


    fun isVerified(uid: String?, email: String?): Boolean =
        (uid != null && uid in verifiedIds) || (!email.isNullOrBlank() && email.lowercase() in verifiedEmails)

    fun refresh() { if (showSkeleton) return; showSkeleton = true; scope.launch { val t0 = System.currentTimeMillis(); loadPosts(); val d = 650 - (System.currentTimeMillis() - t0); if (d > 0) delay(d); showSkeleton = false } }
    suspend fun loadPostsNow() { loadPosts() }

    suspend fun loadPosts() {
        postsLoading = true
        val rows = Api.posts(30)
        if (rows == null) {
            postsError = netMsg()
            if (posts.isNotEmpty()) toast("Couldn't refresh posts \u00B7 " + netMsg())
        } else {
            postsError = null
            val pins = Api.pinned()
            val seen = rows.map { it.id }.toSet()
            val all = rows + pins.filter { it.id !in seen }
            posts = all.sortedWith(compareByDescending<Post> { it.pinned }.thenByDescending { it.createdMs })
            postsMore = rows.size == 30
            if (foreground) rows.maxOfOrNull { it.id }?.let { m -> if (m > prefs.getLong("n_post", -1L)) prefs.edit().putLong("n_post", m).apply() }
        }
        postsLoading = false
    }

    fun loadMore() {
        if (postsLoading || !postsMore) return
        val last = posts.filter { !it.pinned }.minByOrNull { it.createdMs } ?: return
        scope.launch {
            postsLoading = true
            val rows = Api.posts(30, last.createdAt)
            if (rows == null) toast("Couldn't load more \u00B7 " + netMsg())
            if (rows != null) {
                val seen = posts.map { it.id }.toSet()
                posts = (posts + rows.filter { it.id !in seen }).sortedWith(compareByDescending<Post> { it.pinned }.thenByDescending { it.createdMs })
                postsMore = rows.size == 30
            }
            postsLoading = false
        }
    }

    fun visiblePosts(): List<Post> {
        val q = search.trim().lowercase()
        return if (q.isEmpty()) posts else posts.filter { it.title.lowercase().contains(q) || it.subject.lowercase().contains(q) }
    }

    fun toggleLike(p: Post) {
        val u = user
        if (u == null) { toast("Sign in to like posts"); return }
        val was = p.id in liked
        liked = if (was) liked - p.id else liked + p.id
        posts = posts.map { if (it.id == p.id) it.copy(likes = maxOf(0, it.likes + if (was) -1 else 1)) else it }
        scope.launch { if (was) Api.unlike(p.id, u.id) else Api.like(p.id, u.id) }
    }

    fun open(p: Post, ctx: Context) {
        posts = posts.map { if (it.id == p.id) it.copy(taps = it.taps + 1) else it }
        scope.launch { Api.bumpTap(p.id) }
        val l = p.link
        if (p.linkOnly && l != null) {
            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(l)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else viewing = p
    }

    fun isAdmin(): Boolean {
        val u = user ?: return false
        return u.email.equals("momin@" + Cfg.AUTH_DOMAIN, true) || u.role.equals("Admin", true)
    }

    fun canEdit(p: Post): Boolean {
        val u = user ?: return false
        return isAdmin() || (p.authorEmail.isNotBlank() && u.email.equals(p.authorEmail, true))
    }

    fun togglePin(p: Post) {
        scope.launch {
            val next = !p.pinned
            if (Api.patchPost(p.id, JSONObject().put("pinned", next))) { toast(if (next) "Post pinned" else "Post unpinned"); loadPosts() } else toast("Couldn't update pin")
        }
    }

    fun deletePost(p: Post) {
        scope.launch {
            if (Api.deletePost(p.id)) { posts = posts.filter { it.id != p.id }; viewing = null; toast("Post deleted") } else toast("Delete failed: not allowed or already removed")
        }
    }

    fun openProfile(w: com.xcluice.app.data.Who) {
        if (user == null) { toast("Sign in to view profiles"); return }
        profile = w
    }

    fun ask(msg: String, ok: String, danger: Boolean, onOk: () -> Unit) { confirm = Confirm(msg, ok, danger, onOk) }

    // ---------------- profiles, follows, stories ----------------
    fun loadSocial() {
        scope.launch {
            Api.profiles(user != null)?.let { applyProfiles(it) }
            user?.let { u -> Api.follows(u.id)?.let { follows = it } }
            Api.stories()?.let { stories = it }
        }
    }

    private fun applyProfiles(list: List<Profile>) {
        profiles = list
        Av.rank = list.filter { it.email.isNotBlank() }
            .sortedBy { com.xcluice.app.data.parseMs(it.joined) }
            .mapIndexed { i, pr -> pr.email.lowercase() to i }.toMap()
    }

    fun resolve(w: com.xcluice.app.data.Who): Profile {
        profiles.firstOrNull { w.uid != null && it.id == w.uid }?.let { return it }
        if (w.email.isNotBlank()) profiles.firstOrNull { it.email.equals(w.email, true) }?.let { return it }
        chatUsers.firstOrNull { w.uid != null && it.uid == w.uid }?.let { return Profile(it.uid, "", it.name, it.email, it.photo, "", null, false, null, "", "") }
        return Profile(w.uid ?: "", "", w.name, w.email, w.photo, "", null, false, null, "", "")
    }

    fun loadCounts(id: String) {
        scope.launch { counts[id] = Api.followCount("following", id) to Api.followCount("follower", id) }
    }

    fun isFollowing(id: String): Boolean { val u = user ?: return false; return follows.any { it.first == u.id && it.second == id } }
    fun followersOf(id: String): Long = profiles.firstOrNull { it.id == id }?.fo ?: counts[id]?.first ?: follows.count { it.second == id }.toLong()
    fun followingOf(id: String): Long = counts[id]?.second ?: follows.count { it.first == id }.toLong()

    fun toggleFollow(id: String) {
        val u = user ?: return
        val was = isFollowing(id)
        follows = if (was) follows.filter { !(it.first == u.id && it.second == id) } else follows + (u.id to id)
        counts[id]?.let { c -> if (c.first != null) counts[id] = (c.first!! + if (was) -1 else 1) to c.second }
        scope.launch {
            val ok = if (was) Api.unfollow(u.id, id) else Api.follow(u.id, id)
            if (!ok) {
                follows = if (was) follows + (u.id to id) else follows.filter { !(it.first == u.id && it.second == id) }
                toast("Couldn't update follow")
            }
        }
    }

    fun storiesOf(uid: String): List<Story> = stories.filter { it.uid == uid && it.expiresMs > System.currentTimeMillis() }
    fun hasUnseen(uid: String): Boolean = storiesOf(uid).any { it.id !in seen }

    fun markSeen(id: Long) {
        if (id in seen) return
        seen = seen + id
        prefs.edit().putString("seen", JSONArray(seen.toList().takeLast(500)).toString()).apply()
    }

    fun trayOrder(): List<String> {
        val me = user?.id
        val ids = stories.map { it.uid }.distinct().filter { it != me && storiesOf(it).isNotEmpty() }
            .sortedBy { (if (hasUnseen(it)) 0 else 2) + (if (isFollowing(it)) 0 else 1) }
        return (if (me != null && storiesOf(me).isNotEmpty()) listOf(me) else emptyList()) + ids
    }

    fun openStories(queue: List<String>, u: Int, i: Int) {
        val q = queue.filter { storiesOf(it).isNotEmpty() }
        if (q.isEmpty()) return
        storyU = u.coerceIn(0, q.size - 1); storyI = i; storyQueue = q
    }

    fun closeStories() { storyQueue = null }

    fun shareStory(uri: android.net.Uri, caption: String, onDone: (Boolean) -> Unit) {
        val u = user ?: return onDone(false)
        scope.launch {
            val blob = com.xcluice.app.state.Posting.read(uri)
            if (blob == null) { toast("Couldn't read that file"); onDone(false); return@launch }
            val kind = if (blob.mime.startsWith("video/")) "video" else "image"
            val max = if (kind == "video") 40L * 1024 * 1024 else 10L * 1024 * 1024
            if (blob.bytes.size > max) { toast(if (kind == "video") "Video is too big (max 40 MB)" else "Photo is too big (max 10 MB)"); onDone(false); return@launch }
            val ext = blob.ext.lowercase().replace(Regex("[^a-z0-9]"), "").take(5).ifBlank { "bin" }
            val url = Api.upload("story_${u.id.take(8)}_${System.currentTimeMillis()}.$ext", blob.mime, blob.bytes)
            val ok = url != null && Api.createStory(u.id, url, kind, caption.trim().ifBlank { null })
            if (ok) { toast("Story shared"); Api.stories()?.let { stories = it } } else toast("Couldn't share story")
            onDone(ok)
        }
    }

    fun deleteStory(s: Story, after: () -> Unit) {
        scope.launch {
            if (Api.deleteStory(s.id)) { stories = stories.filter { it.id != s.id }; toast("Story deleted"); after() } else toast("Couldn't delete story")
        }
    }

    fun saveProfile(name: String, username: String, bio: String, site: String, onResult: (String?) -> Unit) {
        val u = user ?: return
        scope.launch {
            when (Api.updateProfile(u, name, username, bio, site)) {
                0 -> {
                    saveUser(u.copy(displayName = name, username = username))
                    profiles = profiles.map { if (it.id == u.id) it.copy(name = name, username = username, bio = bio, website = site) else it }
                    loadPosts(); toast("Profile updated"); onResult(null)
                }
                1 -> onResult("That username is taken.")
                else -> onResult("Couldn't save. Try again.")
            }
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
                if (!foreground) { delay(4000); backgrounded = true; continue }
                if (backgrounded) { backgrounded = false; fetchMessages(); syncState() }
                delay(1000)
                t++
                tick = System.currentTimeMillis()
                if (t % 4 == 0) { fetchMessages(); syncState() }
                if (t % 25 == 0) user?.let { Api.presence(it) }
                if (t % 15 == 0) fetchUsers()
                if (t % 60 == 0) loadSocial()
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
        notifyFresh(fresh)
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
        messages = messages + Msg(tid, u.id, u.displayName, text, to, rep?.id, java.time.Instant.now().toString(), System.currentTimeMillis(), true)
        scope.launch { deliver(u, tid, text, to, rep?.id) }
    }

    private suspend fun deliver(u: User, tid: Long, text: String, to: String, replyId: Long?) {
        val saved = Api.sendMessage(u.id, u.displayName, text, to, replyId?.takeIf { it > 0 })
        if (saved == null) {
            messages = messages.filter { it.id != tid }; toast("Message not sent"); return
        }
        seenIds.add(saved.id); renderedIds.add(saved.id)
        messages = if (messages.any { it.id == saved.id }) messages.filter { it.id != tid }
        else messages.map { if (it.id == tid) saved else it }
        val l = lastId
        if (l == null || saved.id > l) lastId = saved.id
    }

    fun sendVoice(f: File, secs: Int) {
        val u = user ?: return
        val to = thread?.uid ?: return
        val rep = reply; reply = null
        val tid = tempSeq--
        val d = maxOf(1, secs)
        // show the bubble instantly, playable from the local file, then upload in the background
        messages = messages + Msg(tid, u.id, u.displayName, "[AUDIO::${f.absolutePath}#d=$d]", to, rep?.id, java.time.Instant.now().toString(), System.currentTimeMillis(), true)
        scope.launch {
            val bytes = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { f.readBytes() }.getOrNull() }
            val url = bytes?.let { Api.upload("voice_${u.username}_${System.currentTimeMillis()}.m4a", "audio/mp4", it) }
            if (url == null) {
                messages = messages.filter { it.id != tid }; toast("Voice upload failed. Check your connection"); return@launch
            }
            runCatching { f.delete() }
            deliver(u, tid, "[AUDIO::$url#d=$d]", to, rep?.id)
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
