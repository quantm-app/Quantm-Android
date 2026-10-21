package app.quantm.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

data class LocalPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tracks: List<Track>,
)

/**
 * Quantm playlists: saved on the phone, so adding and removing songs is instant and never
 * blocked by SoundCloud. The file format is the same as desktop Quantm's export, so playlists
 * move between the two with Export / Import.
 */
class LocalPlaylists(context: Context) {
    private val file = File(context.filesDir, "local-playlists.json")
    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    fun get(id: String): LocalPlaylist? = _playlists.value.find { it.id == id }

    fun create(title: String, tracks: List<Track> = emptyList()): LocalPlaylist {
        val now = System.currentTimeMillis()
        val playlist = LocalPlaylist(newId(), title.trim().take(300), "", now, now, clean(tracks))
        save(_playlists.value + playlist)
        return playlist
    }

    fun rename(id: String, title: String) = edit(id) { it.copy(title = title.trim().take(300)) }

    fun delete(id: String) = save(_playlists.value.filter { it.id != id })

    fun contains(id: String, trackId: Long) = get(id)?.tracks?.any { it.id == trackId } == true

    /** Returns false when the song was already there */
    fun add(id: String, track: Track): Boolean {
        if (contains(id, track.id)) return false
        edit(id) { it.copy(tracks = it.tracks + slim(track)) }
        return true
    }

    fun addAll(id: String, tracks: List<Track>) = edit(id) { p ->
        val have = p.tracks.map { it.id }.toHashSet()
        p.copy(tracks = p.tracks + clean(tracks).filter { it.id !in have })
    }

    fun remove(id: String, trackId: Long) = edit(id) { p -> p.copy(tracks = p.tracks.filter { it.id != trackId }) }

    fun move(id: String, from: Int, to: Int) = edit(id) { p ->
        if (from !in p.tracks.indices || to !in p.tracks.indices) return@edit p
        p.copy(tracks = p.tracks.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun exportJson(id: String? = null): String {
        val list = if (id == null) _playlists.value else _playlists.value.filter { it.id == id }
        return pretty.encodeToString(JsonObject.serializer(), document(list))
    }

    /** Adds the playlists in a desktop or Android export. Returns how many were added */
    fun importJson(text: String): Int {
        val root = runCatching { QuantmJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw IllegalArgumentException("That isn't a playlist file.")
        if ((root["version"] as? JsonPrimitive)?.longOrNull != 1L || root["playlists"] !is JsonArray) {
            throw IllegalArgumentException("Unsupported playlist file.")
        }
        val existing = _playlists.value.map { it.id }.toHashSet()
        val added = root.arr("playlists").mapNotNull { parse(it as? JsonObject) }.map { p ->
            if (p.id in existing) p.copy(id = newId()) else p
        }
        if (added.isEmpty()) throw IllegalArgumentException("No playlists found in that file.")
        save(_playlists.value + added)
        return added.size
    }

    private fun edit(id: String, change: (LocalPlaylist) -> LocalPlaylist) {
        save(_playlists.value.map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    private fun save(list: List<LocalPlaylist>) {
        _playlists.value = list
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(QuantmJson.encodeToString(JsonObject.serializer(), document(list)))
        // replace in one step, so a crash mid-save never leaves a half-written file
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    private fun load(): List<LocalPlaylist> = runCatching {
        if (!file.exists()) return emptyList()
        val root = QuantmJson.parseToJsonElement(file.readText()).jsonObject
        root.arr("playlists").mapNotNull { parse(it as? JsonObject) }
    }.getOrDefault(emptyList())

    private fun parse(obj: JsonObject?): LocalPlaylist? {
        obj ?: return null
        val title = obj.str("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val id = obj.str("id")?.takeIf { Regex("^q[a-z0-9]{1,80}$").matches(it) } ?: newId()
        val now = System.currentTimeMillis()
        val tracks = obj.arr("tracks").mapNotNull { el ->
            runCatching { QuantmJson.decodeFromJsonElement<Track>(el) }.getOrNull()?.takeIf { it.title.isNotBlank() && it.user != null }
        }
        return LocalPlaylist(
            id, title.take(300), obj.str("description").orEmpty(),
            (obj["createdAt"] as? JsonPrimitive)?.longOrNull ?: now,
            (obj["updatedAt"] as? JsonPrimitive)?.longOrNull ?: now,
            clean(tracks),
        )
    }

    private fun document(list: List<LocalPlaylist>) = buildJsonObject {
        put("version", 1)
        put("playlists", buildJsonArray {
            list.forEach { p ->
                add(buildJsonObject {
                    put("id", p.id)
                    put("title", p.title)
                    put("description", p.description)
                    put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt)
                    put("tracks", JsonArray(p.tracks.map { QuantmJson.encodeToJsonElement(slim(it)) }))
                })
            }
        })
    }

    private fun clean(tracks: List<Track>): List<Track> {
        val seen = HashSet<Long>()
        return tracks.filter { !it.isStub && seen.add(it.id) }.map(::slim)
    }

    companion object {
        private val pretty = kotlinx.serialization.json.Json { prettyPrint = true }

        private fun newId() = "q" + UUID.randomUUID().toString().replace("-", "")

        /** Only what's needed to show and play the song: never authorizations or stream URLs */
        fun slim(t: Track) = Track(
            id = t.id,
            title = t.title,
            duration = t.duration,
            permalinkUrl = t.permalinkUrl,
            artworkUrl = t.artworkUrl,
            user = t.user?.let { User(id = it.id, username = it.username, avatarUrl = it.avatarUrl) },
            downloadable = t.downloadable,
            hasDownloadsLeft = t.hasDownloadsLeft,
            media = t.media?.let { m -> Media(m.transcodings.map { Transcoding(format = Format(protocol = it.format.protocol, mimeType = it.format.mimeType), snipped = it.snipped) }) },
        )
    }
}
