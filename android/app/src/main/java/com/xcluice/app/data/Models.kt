package com.xcluice.app.data

import java.time.OffsetDateTime

object Cfg {
    const val SB = "https://nzolsmouyulzqeaocejm.supabase.co"
    const val KEY = "sb_publishable_EGX3jDjRh5LiXjdTw2AXxA_e1YIaP4Q"
    const val BUCKET = "homework-files"
    const val SITE = "https://xcluice.vercel.app"
    const val AUTH_DOMAIN = "mailinator.com"
}

data class User(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val verified: Boolean,
    val role: String = ""
)

data class Post(
    val id: Long,
    val title: String,
    val subject: String,
    val fileUrl: String,
    val authorName: String,
    val authorEmail: String,
    val authorPhoto: String?,
    val createdAt: String,
    val createdMs: Long,
    val likes: Long,
    val taps: Long,
    val pinned: Boolean,
    val linkUrl: String? = null,
    val thumbUrl: String? = null
) {
    val isShot: Boolean get() = !linkUrl.isNullOrBlank() && Regex("/link_\\d+_").containsMatchIn(fileUrl)
    val link: String? get() = linkUrl?.takeIf { it.isNotBlank() } ?: fileUrl.takeIf { isLinkUrl(it) }
    val linkOnly: Boolean get() = if (!linkUrl.isNullOrBlank()) isShot else isLinkUrl(fileUrl)
    val hasRealFile: Boolean get() = fileUrl.isNotBlank() && !isShot && !isLinkUrl(fileUrl)
    val type: FileType get() = fileType(fileUrl)
    val headline: String get() = subject.ifBlank { title }.ifBlank { "Untitled" }
}

data class Who(val name: String, val email: String, val photo: String?, val uid: String?)

data class ChatUser(
    val uid: String,
    val name: String,
    val email: String,
    val photo: String?,
    val lastActive: String?
)

data class Msg(
    val id: Long,
    val uid: String,
    val name: String,
    val text: String,
    val toUid: String?,
    val replyTo: Long?,
    val createdAt: String,
    val createdMs: Long,
    val temp: Boolean = false
) {
    val isAudio: Boolean get() = text.startsWith("[AUDIO::")
    val audioUrl: String
        get() = text.removePrefix("[AUDIO::").removeSuffix("]").substringBefore("#d=")
    val audioDur: Int
        get() = text.substringAfter("#d=", "").removeSuffix("]").toIntOrNull() ?: 0
    val preview: String get() = if (isAudio) "🎤 Voice message" else text
}

data class Reaction(val messageId: Long, val uid: String, val emoji: String)

data class Profile(
    val id: String, val username: String, val name: String, val email: String, val photo: String?,
    val role: String, val joined: String?, val verified: Boolean, val fo: Long?, val bio: String, val website: String
)

data class Story(
    val id: Long, val uid: String, val mediaUrl: String, val kind: String, val caption: String?,
    val createdMs: Long, val expiresMs: Long
)

fun isLinkUrl(u: String?): Boolean =
    !u.isNullOrBlank() && Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u) && !u.startsWith(Cfg.SB + "/storage/")

fun hostOf(u: String): String =
    runCatching { java.net.URI(u).host?.removePrefix("www.") }.getOrNull() ?: u

fun normalizeLink(raw: String): String? {
    var s = raw.trim()
    if (s.isEmpty()) return null
    if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s = "https://$s"
    return try {
        val u = java.net.URI(s)
        if (u.host == null || !u.host.contains('.') || (u.scheme != "http" && u.scheme != "https")) null else u.toString()
    } catch (e: Exception) { null }
}

object Av { var rank by androidx.compose.runtime.mutableStateOf<Map<String, Int>>(emptyMap()) }

enum class FileType { TEXT, MEDIA, DOCS, HTML, APK, ARCHIVE, OTHER }

fun fileType(url: String?): FileType {
    if (url.isNullOrBlank()) return FileType.TEXT
    val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png", "jpg", "jpeg", "gif", "webp", "svg" -> FileType.MEDIA
        "pdf", "txt", "docx", "csv", "xlsx" -> FileType.DOCS
        "html" -> FileType.HTML
        "apk" -> FileType.APK
        "zip", "rar", "7z" -> FileType.ARCHIVE
        "mp3", "wav", "ogg", "webm", "mp4", "mkv" -> FileType.MEDIA
        else -> FileType.OTHER
    }
}

fun isImageUrl(url: String?): Boolean {
    val ext = url?.substringBefore('?')?.substringAfterLast('.', "")?.lowercase() ?: return false
    return ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg")
}

fun parseMs(s: String?): Long =
    runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)

fun defAvatar(key: String?): String {
    val k = (key ?: "").lowercase()
    var n = (Av.rank[k] ?: -1).toLong()
    if (n < 0) { n = 7L; for (c in k) n = (n * 31 + c.code) and 0xFFFFFFFFL }
    return Cfg.SITE + "/avatars/" + (n % 20 + 1) + ".svg"
}

fun agoShort(ms: Long): String {
    val s = maxOf(0L, (System.currentTimeMillis() - ms) / 1000)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        s < 604800 -> "${s / 86400}d"
        else -> "${s / 604800}w"
    }
}
