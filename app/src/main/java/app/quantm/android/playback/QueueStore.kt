package app.quantm.android.playback

import android.content.Context
import app.quantm.android.data.LocalPlaylists
import app.quantm.android.data.QuantmJson
import app.quantm.android.data.Track
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class SavedQueue(
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int = 0,
)

/** The queue survives the app being closed: it comes back paused where you left off */
class QueueStore(context: Context) {
    private val file = File(context.filesDir, "queue.json")

    fun save(queue: SavedQueue) {
        runCatching {
            val slim = queue.copy(tracks = queue.tracks.map(LocalPlaylists::slim))
            val tmp = File(file.parentFile, "queue.json.tmp")
            tmp.writeText(QuantmJson.encodeToString(SavedQueue.serializer(), slim))
            // replace in one step, so a crash mid-save never leaves a half-written file
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }
    }

    fun load(): SavedQueue? = runCatching {
        if (!file.exists()) null else QuantmJson.decodeFromString(SavedQueue.serializer(), file.readText())
    }.getOrNull()?.takeIf { it.tracks.isNotEmpty() }

    fun clear() { file.delete() }
}
