package app.quantm.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiException(val status: Int, val body: String? = null, message: String? = null) :
    IOException(message ?: "SoundCloud error $status") {
    /** SoundCloud's bot protection answered instead of the API */
    val isHumanCheck: Boolean get() = status == 403 && body?.contains("captcha-delivery.com") == true
}

class NotSignedInException : IOException("Not signed in")

/**
 * SoundCloud api-v2, the same API soundcloud.com uses. Requests carry the user's OAuth token
 * and a public client_id scraped from soundcloud.com (refreshed when it goes stale).
 */
class SoundCloud(val http: OkHttpClient, private val authStore: AuthStore) {
    private val scrapeLock = Mutex()
    private val trackCache = object : LinkedHashMap<Long, Track>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Track>?) = size > 3000
    }

    // ---- transport -------------------------------------------------------------

    suspend fun get(resource: String, params: Map<String, Any?> = emptyMap()): JsonElement =
        request("GET", resource, params) ?: JsonNull

    suspend fun request(
        method: String,
        resource: String,
        params: Map<String, Any?> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement? = withContext(Dispatchers.IO) { requestBlocking(method, resource, params, body) }

    /** For the player's loader thread, which is allowed to block */
    fun requestBlocking(
        method: String,
        resource: String,
        params: Map<String, Any?> = emptyMap(),
        body: JsonElement? = null,
        retried: Boolean = false,
    ): JsonElement? {
        val auth = authStore.current ?: throw NotSignedInException()
        val base = if (resource.startsWith("https:")) resource else API + resource.trimStart('/')
        val url = base.toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> if (value != null && value.toString().isNotEmpty()) setQueryParameter(key, value.toString()) }
            setQueryParameter("client_id", auth.clientId)
        }.build()
        if (url.host != "api-v2.soundcloud.com") throw ApiException(400, message = "Blocked host")

        val request = Request.Builder()
            .url(url)
            // debug builds' guest mode has no token: public endpoints work with the client_id alone
            .apply { if (auth.token.isNotEmpty()) header("Authorization", "OAuth " + auth.token) }
            .header("Accept", "application/json")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")
            .method(method, body?.toString()?.toRequestBody(JSON) ?: if (method == "GET" || method == "DELETE") null else "".toRequestBody(JSON))
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            // connection resets happen; retry reads once, never writes (the first may have arrived)
            if (!retried && method == "GET") return requestBlocking(method, resource, params, body, true)
            throw e
        }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                val error = ApiException(it.code, text)
                // a stale client_id gives 401/403 even with a valid token: refresh it once
                if (!retried && (it.code == 401 || it.code == 403) && !error.isHumanCheck) {
                    val fresh = runCatching { scrapeClientIdBlocking() }.getOrNull()
                    if (fresh != null && fresh != auth.clientId) {
                        authStore.updateClientId(fresh)
                        return requestBlocking(method, resource, params, body, true)
                    }
                }
                throw error
            }
            return if (text.isBlank()) null else QuantmJson.parseToJsonElement(text)
        }
    }

    // ---- client_id -----------------------------------------------------------

    suspend fun scrapeClientId(): String = withContext(Dispatchers.IO) { scrapeClientIdBlocking() }

    private val scrapeMonitor = Any()

    private fun scrapeClientIdBlocking(): String = synchronized(scrapeMonitor) {
        val html = fetchText("https://soundcloud.com/")
        // the asset host has changed before (a-v2, a-v3...): accept any sndcdn script
        val scripts = Regex("""<script[^>]+src="(https://[a-z0-9-]+\.sndcdn\.com/[^"]+\.js)"""")
            .findAll(html).map { it.groupValues[1] }.toList().reversed()
        // the id is usually in one of the last bundles
        for (src in scripts) {
            val js = runCatching { fetchText(src) }.getOrNull() ?: continue
            Regex("""client_id\s*[:=]\s*"([a-zA-Z0-9]{32})"""").find(js)?.let { return it.groupValues[1] }
        }
        throw IOException("Could not find a SoundCloud client_id")
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", DESKTOP_UA).build()
        http.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} for $url")
            return it.body.string()
        }
    }

    // ---- decoding --------------------------------------------------------------

    inline fun <reified T> decode(element: JsonElement): T = QuantmJson.decodeFromJsonElement(element)

    fun cache(track: Track): Track {
        if (!track.isStub) synchronized(trackCache) { trackCache[track.id] = track }
        return track
    }

    fun cached(id: Long): Track? = synchronized(trackCache) { trackCache[id] }

    fun itemOf(element: JsonElement?): Item? {
        val obj = element as? JsonObject ?: return null
        return when (obj.str("kind")) {
            "track" -> Item.TrackItem(cache(decode(obj)))
            "playlist" -> Item.PlaylistItem(decode(obj))
            "system-playlist" -> Item.SystemItem(decode(obj))
            "user" -> Item.UserItem(decode(obj))
            else -> null
        }
    }

    fun <T> pager(resource: String, params: Map<String, Any?> = emptyMap(), map: (JsonObject) -> T?) =
        Pager(this, resource, params, map)

    private fun trackPager(resource: String, params: Map<String, Any?> = emptyMap(), field: String? = null) =
        pager(resource, params) { obj ->
            val raw = if (field == null) obj else obj[field] as? JsonObject
            raw?.let { cache(decode<Track>(it)) }
        }

    // ---- hydration: playlists and stations only include ids for most tracks ---------

    suspend fun tracksByIds(ids: List<Long>): List<Track> = coroutineScope {
        val found = HashMap<Long, Track>()
        ids.forEach { id -> cached(id)?.let { found[id] = it } }
        val missing = ids.filter { it !in found }.distinct()
        missing.chunked(50).map { chunk ->
            async { decode<List<Track>>(get("tracks", mapOf("ids" to chunk.joinToString(",")))) }
        }.awaitAll().flatten().forEach { track -> found[track.id] = cache(track) }
        ids.mapNotNull { found[it] }
    }

    suspend fun hydrate(tracks: List<Track>): List<Track> =
        if (tracks.none { it.isStub }) tracks.onEach { cache(it) } else tracksByIds(tracks.map { it.id })

    // ---- endpoints ---------------------------------------------------------------

    suspend fun me(): User = decode(get("me"))

    suspend fun user(id: Long): User = decode(get("users/$id"))

    suspend fun track(id: Long): Track = cache(decode(get("tracks/$id")))

    suspend fun playlist(id: Long): Playlist {
        val playlist = decode<Playlist>(get("playlists/$id", mapOf("representation" to "full")))
        return playlist.copy(tracks = hydrate(playlist.tracks))
    }

    suspend fun systemPlaylist(urn: String): SystemPlaylist {
        val playlist = decode<SystemPlaylist>(get("system-playlists/" + urn))
        return playlist.copy(tracks = hydrate(playlist.tracks))
    }

    suspend fun resolve(url: String): Item? = itemOf(get("resolve", mapOf("url" to url)))

    /** Home: SoundCloud's own sections ("More of what you like", mixes, stations...) */
    suspend fun mixedSelections(): List<Section> {
        val data = get("mixed-selections", mapOf("limit" to 12)).jsonObject
        return data.arr("collection").mapNotNull { section ->
            val obj = section as? JsonObject ?: return@mapNotNull null
            val items = (obj["items"] as? JsonObject)?.arr("collection").orEmpty().mapNotNull(::itemOf)
            if (items.isEmpty()) null else Section(obj.str("title") ?: "", obj.str("description"), items)
        }
    }

    fun trending(genre: String = "soundcloud:genres:all-music") =
        trackPager("charts", mapOf("kind" to "trending", "genre" to genre, "limit" to 50), field = "track")

    fun stream() = pager("stream", mapOf("limit" to 20)) { obj -> feedPost(obj) }

    fun userReposts(id: Long) = pager("stream/users/$id/reposts", mapOf("limit" to 20)) { obj -> feedPost(obj) }

    private fun feedPost(obj: JsonObject): FeedPost? {
        val type = obj.str("type").orEmpty()
        // SoundCloud mixes paid "promoted" posts into feeds; Quantm never shows them
        if ("promoted" in type || obj["promoted"]?.let { it !is JsonNull } == true) return null
        val item = (obj["track"] as? JsonObject)?.let { Item.TrackItem(cache(decode(it))) }
            ?: (obj["playlist"] as? JsonObject)?.let { Item.PlaylistItem(decode(it)) }
            ?: return null
        val reposter = if ("repost" in type) (obj["user"] as? JsonObject)?.let { decode<User>(it) } else null
        return FeedPost(obj.str("uuid") ?: (item.key + obj.str("created_at")), item, reposter, obj.str("created_at"))
    }

    fun userTracks(id: Long) = trackPager("users/$id/tracks", mapOf("limit" to 20))
    fun userTopTracks(id: Long) = trackPager("users/$id/toptracks", mapOf("limit" to 10))
    fun userAlbums(id: Long) = pager("users/$id/albums", mapOf("limit" to 20)) { decode<Playlist>(it) }
    fun userPlaylists(id: Long) = pager("users/$id/playlists_without_albums", mapOf("limit" to 20)) { decode<Playlist>(it) }
    fun userFollowings(id: Long) = pager("users/$id/followings", mapOf("limit" to 30)) { decode<User>(it) }
    fun userFollowers(id: Long) = pager("users/$id/followers", mapOf("limit" to 30)) { decode<User>(it) }
    fun userLikes(id: Long) = pager("users/$id/likes", mapOf("limit" to 24)) { obj ->
        (obj["track"] as? JsonObject)?.let { Item.TrackItem(cache(decode(it))) }
            ?: (obj["playlist"] as? JsonObject)?.let { Item.PlaylistItem(decode(it)) }
    }

    fun related(id: Long) = trackPager("tracks/$id/related", mapOf("limit" to 20))
    fun comments(id: Long) = pager("tracks/$id/comments", mapOf("threaded" to 0, "sort" to "newest", "limit" to 30)) { decode<Comment>(it) }

    suspend fun postComment(trackId: Long, body: String, timestampMs: Long): Comment = decode(
        request("POST", "tracks/$trackId/comments", body = buildJsonObject {
            put("comment", buildJsonObject {
                put("body", body)
                put("timestamp", timestampMs.coerceAtLeast(0))
            })
        }) ?: JsonNull
    )

    // library
    fun myTrackLikes(meId: Long) = trackPager("users/$meId/track_likes", mapOf("limit" to 50), field = "track")
    fun myLibrary() = pager("me/library/all", mapOf("limit" to 30)) { obj ->
        (obj["playlist"] as? JsonObject)?.let { Item.PlaylistItem(decode(it)) }
            ?: (obj["system_playlist"] as? JsonObject)?.let { Item.SystemItem(decode(it)) }
    }
    fun myHistory() = trackPager("me/play-history/tracks", mapOf("limit" to 30), field = "track")

    /** Every liked track, for "Shuffle all": pages of 200, so even 2,000 likes take 10 requests */
    suspend fun allTrackLikes(meId: Long): List<Track> {
        val pager = trackPager("users/$meId/track_likes", mapOf("limit" to 200), field = "track")
        val all = ArrayList<Track>()
        while (!pager.done) all += pager.next()
        return all
    }

    private suspend fun allIds(resource: String): Set<Long> {
        val ids = HashSet<Long>()
        var next: String? = resource
        var params: Map<String, Any?> = mapOf("limit" to 200, "linked_partitioning" to 1)
        while (next != null) {
            val data = get(next, params).jsonObject
            data.arr("collection").forEach { el -> el.jsonPrimitive.longOrNull?.let(ids::add) }
            next = data.str("next_href")
            params = emptyMap()
        }
        return ids
    }

    suspend fun likedTrackIds() = allIds("me/track_likes/ids")
    suspend fun likedPlaylistIds() = allIds("me/playlist_likes/ids")
    suspend fun repostedTrackIds() = allIds("me/track_reposts/ids")
    suspend fun followingIds(meId: Long) = allIds("users/$meId/followings/ids")

    // actions
    suspend fun likeTrack(meId: Long, id: Long, on: Boolean) { request(if (on) "PUT" else "DELETE", "users/$meId/track_likes/$id") }
    suspend fun likePlaylist(meId: Long, id: Long, on: Boolean) { request(if (on) "PUT" else "DELETE", "users/$meId/playlist_likes/$id") }
    suspend fun repostTrack(id: Long, on: Boolean) { request(if (on) "PUT" else "DELETE", "me/track_reposts/$id") }
    suspend fun follow(id: Long, on: Boolean) { request(if (on) "POST" else "DELETE", "me/followings/$id") }

    suspend fun myPlaylists(meId: Long): List<Playlist> {
        val pager = pager("users/$meId/playlists", mapOf("limit" to 50)) { decode<Playlist>(it) }
        val all = ArrayList<Playlist>()
        while (!pager.done) all += pager.next()
        return all
    }

    suspend fun createPlaylist(title: String, trackIds: List<Long>, private: Boolean): Playlist = decode(
        request("POST", "playlists", body = buildJsonObject {
            put("playlist", buildJsonObject {
                put("title", title)
                put("sharing", if (private) "private" else "public")
                put("tracks", buildJsonArray { trackIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            })
        }) ?: JsonNull
    )

    suspend fun setPlaylistTracks(id: Long, trackIds: List<Long>) {
        request("PUT", "playlists/$id", body = buildJsonObject {
            put("playlist", buildJsonObject {
                put("tracks", buildJsonArray { trackIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            })
        })
    }

    suspend fun renamePlaylist(id: Long, title: String) {
        request("PUT", "playlists/$id", body = buildJsonObject { put("playlist", buildJsonObject { put("title", title) }) })
    }

    suspend fun deletePlaylist(id: Long) { request("DELETE", "playlists/$id") }

    // search. kind: "" (everything) | tracks | users | albums | playlists_without_albums
    fun search(query: String, kind: String = "") =
        pager(if (kind.isEmpty()) "search" else "search/$kind", mapOf("q" to query, "limit" to 20)) { itemOf(it) }

    suspend fun suggestions(query: String): List<String> =
        get("search/queries", mapOf("q" to query, "limit" to 8)).jsonObject.arr("collection")
            .mapNotNull { (it as? JsonObject)?.str("output") }

    /** Waveform samples scaled to 0..1 */
    suspend fun waveform(track: Track): FloatArray? = withContext(Dispatchers.IO) {
        val url = track.waveformUrl?.replace(Regex("\\.png$"), ".json") ?: return@withContext null
        if (!url.startsWith("https://")) return@withContext null
        runCatching {
            val obj = QuantmJson.parseToJsonElement(fetchText(url)).jsonObject
            val samples = obj.arr("samples").map { it.jsonPrimitive.contentOrNull?.toFloatOrNull() ?: 0f }
            val max = obj["height"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: samples.maxOrNull() ?: 1f
            FloatArray(samples.size) { (samples[it] / max).coerceIn(0f, 1f) }
        }.getOrNull()
    }

    // ---- playback: SoundCloud serves HLS (AAC preferred, MP3 fallback) ---------------

    data class Stream(val url: String, val hls: Boolean, val snipped: Boolean)

    /**
     * Resolve a playable URL. track_authorization expires, so the track is always refetched.
     * [hls] picks HLS (the player's default) or a progressive file.
     */
    fun streamBlocking(id: Long, hls: Boolean = true): Stream {
        val fresh = cache(decode<Track>(requestBlocking("GET", "tracks/$id") ?: JsonNull))
        if (fresh.isProtected) throw ApiException(403, message = "This song is copy-protected")
        val playable = fresh.media?.transcodings.orEmpty().filter {
            val mime = it.format.mimeType
            (it.format.protocol == "hls" || it.format.protocol == "progressive") && "ogg" !in mime && "opus" !in mime
        }
        val full = playable.filter { !it.snipped }
        val candidates = (full.ifEmpty { playable })
            .sortedBy { t ->
                val wanted = (t.format.protocol == "hls") == hls
                (if (wanted) 0 else 10) + if ("mp4" in t.format.mimeType || "aac" in t.format.mimeType) 0 else 1
            }
        if (candidates.isEmpty()) throw ApiException(403, message = "This song isn't available for streaming")
        var last: Exception? = null
        for (t in candidates) {
            try {
                val data = requestBlocking("GET", t.url, mapOf("track_authorization" to fresh.trackAuthorization))?.jsonObject
                val url = data?.str("url") ?: continue
                return Stream(url, t.format.protocol == "hls", t.snipped)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: ApiException(403, message = "No stream")
    }

    /** The original file the artist allowed to download */
    suspend fun downloadUrl(id: Long): String =
        get("tracks/$id/download").jsonObject.str("redirectUri") ?: throw ApiException(404, message = "No download")

    companion object {
        const val API = "https://api-v2.soundcloud.com/"
        // soundcloud.com only shows its sign-in form to desktop browsers
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
fun JsonObject.arr(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())

/** Cursor pagination over any api-v2 collection; next() returns [] once done */
class Pager<T>(
    private val api: SoundCloud,
    private val resource: String,
    params: Map<String, Any?>,
    private val map: (JsonObject) -> T?,
) {
    private val params = mapOf<String, Any?>("limit" to 24, "linked_partitioning" to 1) + params
    private var nextHref: String? = null
    private var started = false
    private val lock = Mutex()
    var done = false
        private set

    suspend fun next(): List<T> = lock.withLock {
        if (done) return emptyList()
        val data = (if (started) api.get(nextHref!!) else api.get(resource, params)).jsonObject
        started = true
        nextHref = data.str("next_href")
        done = nextHref == null
        data.arr("collection").mapNotNull { (it as? JsonObject)?.let(map) }
    }
}
