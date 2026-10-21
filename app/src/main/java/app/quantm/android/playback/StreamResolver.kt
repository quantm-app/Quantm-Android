package app.quantm.android.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import app.quantm.android.data.Offline
import app.quantm.android.data.SoundCloud
import java.io.IOException

/** SoundCloud only has a progressive file for this song, so the HLS item must be rebuilt */
class ProgressiveOnlyException(val trackId: Long) : IOException("Progressive only")

/** The song can't be streamed at all (copy-protected, removed, region-locked...) */
class UnplayableException(val trackId: Long, cause: Throwable) : IOException(cause.message, cause)

/**
 * Turns quantm://track/<id> into a fresh stream URL right before the player loads it. Runs on
 * the player's loader thread, which may block. Downloaded songs play from their file.
 */
class StreamResolver(private val api: SoundCloud, private val offline: Offline) : ResolvingDataSource.Resolver {
    private data class Cached(val url: String, val at: Long)
    private val cache = object : LinkedHashMap<String, Cached>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 32
    }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != MediaItems.SCHEME) return dataSpec
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IOException("Bad song address")
        offline.fileFor(id)?.let { return dataSpec.withUri(Uri.fromFile(it)) }
        val hls = uri.getQueryParameter("hls") != "0"
        val key = "$id:$hls"
        // the same item can be opened more than once while loading; signed URLs last much longer
        synchronized(cache) { cache[key] }?.takeIf { System.currentTimeMillis() - it.at < 5 * 60_000 }?.let {
            return dataSpec.withUri(it.url.toUri())
        }
        val stream = try {
            api.streamBlocking(id, hls)
        } catch (e: IOException) {
            if (e is app.quantm.android.data.ApiException && (e.status == 403 || e.status == 404)) throw UnplayableException(id, e)
            throw e
        }
        if (hls && !stream.hls) throw ProgressiveOnlyException(id)
        synchronized(cache) { cache[key] = Cached(stream.url, System.currentTimeMillis()) }
        return dataSpec.withUri(stream.url.toUri())
    }

    fun forget(id: Long) {
        synchronized(cache) {
            cache.remove("$id:true")
            cache.remove("$id:false")
        }
    }
}
