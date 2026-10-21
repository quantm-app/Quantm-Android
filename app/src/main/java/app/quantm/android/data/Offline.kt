package app.quantm.android.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.File

@Serializable
data class OfflineEntry(val track: Track, val file: String, val bytes: Long, val savedAt: Long)

/**
 * Offline downloads. Only songs whose artist turned on SoundCloud's download button: the
 * original file the artist uploaded, which then plays with no internet.
 */
class Offline(context: Context, private val api: SoundCloud, private val scope: CoroutineScope, private val messages: Messages) {
    private val dir = File(context.filesDir, "offline").apply { mkdirs() }
    private val index = File(dir, "index.json")

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<OfflineEntry>> = _entries.asStateFlow()

    /** Download progress 0..1 by track id */
    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()
    private val jobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()

    fun fileFor(trackId: Long): File? =
        _entries.value.find { it.track.id == trackId }?.let { File(dir, it.file) }?.takeIf { it.exists() }

    fun has(trackId: Long) = _entries.value.any { it.track.id == trackId }

    val totalBytes: Long get() = _entries.value.sumOf { it.bytes }

    fun download(track: Track) {
        if (!track.isDownloadable || has(track.id) || jobs.containsKey(track.id)) return
        _progress.update { it + (track.id to 0f) }
        // registered before it starts, so a fast finish can't remove it before it's added
        val job = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val url = api.downloadUrl(track.id)
                val request = Request.Builder().url(url).build()
                api.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException(response.code, message = "Download failed (${response.code})")
                    val ext = extensionFor(response.header("Content-Type"), url)
                    val name = "${track.id}.$ext"
                    val target = File(dir, "$name.part")
                    val total = response.body.contentLength()
                    var received = 0L
                    response.body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var lastReport = 0L
                            while (isActive) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                received += n
                                if (total > 0 && received - lastReport > 256 * 1024) {
                                    lastReport = received
                                    _progress.update { it + (track.id to received.toFloat() / total) }
                                }
                            }
                        }
                    }
                    if (!isActive) { target.delete(); return@use }
                    val final = File(dir, name)
                    target.renameTo(final)
                    val entry = OfflineEntry(LocalPlaylists.slim(track), name, final.length(), System.currentTimeMillis())
                    _entries.update { list -> list.filter { it.track.id != track.id } + entry }
                    saveIndex()
                    messages.show("Downloaded \"${track.title}\"")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) messages.show("Couldn't download: " + e.friendly())
            } finally {
                jobs.remove(track.id)
                _progress.update { it - track.id }
            }
        }
        jobs[track.id] = job
        job.start()
    }

    fun cancel(trackId: Long) { jobs.remove(trackId)?.cancel() }

    fun remove(trackId: Long) {
        _entries.value.find { it.track.id == trackId }?.let { File(dir, it.file).delete() }
        _entries.update { list -> list.filter { it.track.id != trackId } }
        saveIndex()
    }

    fun removeAll() {
        jobs.values.toList().forEach { it.cancel() }
        dir.listFiles()?.forEach { if (it != index) it.delete() }
        _entries.value = emptyList()
        saveIndex()
    }

    private fun extensionFor(type: String?, url: String): String {
        val fromUrl = Regex("""\.([a-z0-9]{2,4})(\?|$)""", RegexOption.IGNORE_CASE).find(url.substringBefore('?') + "?")?.groupValues?.get(1)?.lowercase()
        return when {
            type == null -> fromUrl ?: "mp3"
            "wav" in type -> "wav"
            "flac" in type -> "flac"
            "aiff" in type || "aif" in type -> "aiff"
            "mp4" in type || "m4a" in type || "aac" in type -> "m4a"
            "ogg" in type -> "ogg"
            "mpeg" in type || "mp3" in type -> "mp3"
            else -> fromUrl ?: "mp3"
        }
    }

    private fun load(): List<OfflineEntry> = runCatching {
        if (!index.exists()) emptyList()
        else QuantmJson.decodeFromString<List<OfflineEntry>>(index.readText()).filter { File(dir, it.file).exists() }
    }.getOrDefault(emptyList())

    private val indexLock = Any()

    /** Downloads finish on several threads at once: one writer at a time, replacing the file in one step */
    private fun saveIndex() {
        synchronized(indexLock) {
            runCatching {
                val tmp = File(dir, "index.json.tmp")
                tmp.writeText(QuantmJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(OfflineEntry.serializer()), _entries.value))
                java.nio.file.Files.move(tmp.toPath(), index.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            }
        }
    }
}
