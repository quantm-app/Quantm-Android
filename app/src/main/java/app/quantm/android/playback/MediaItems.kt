package app.quantm.android.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import app.quantm.android.data.Media
import app.quantm.android.data.Track
import app.quantm.android.data.Transcoding
import app.quantm.android.data.Format
import app.quantm.android.data.User
import app.quantm.android.ui.components.artworkLarge
import java.io.File

/**
 * Songs in the player are "quantm://track/<id>" items. The stream URL is looked up only when the
 * player is about to load the song (StreamResolver), because SoundCloud's URLs expire.
 */
object MediaItems {
    const val SCHEME = "quantm"
    private const val EXTRA_USER_ID = "userId"
    private const val EXTRA_AVATAR = "avatar"
    private const val EXTRA_PERMALINK = "permalink"
    private const val EXTRA_HLS = "hls"
    private const val EXTRA_DOWNLOADABLE = "downloadable"

    /** HLS unless SoundCloud only offers a progressive file for this song */
    fun prefersHls(track: Track): Boolean {
        val all = track.media?.transcodings.orEmpty()
        return all.isEmpty() || all.any { it.format.protocol == "hls" }
    }

    fun from(track: Track): MediaItem {
        val extras = Bundle().apply {
            putLong(EXTRA_USER_ID, track.user?.id ?: 0)
            putString(EXTRA_AVATAR, track.user?.avatarUrl)
            putString(EXTRA_PERMALINK, track.permalinkUrl)
            putBoolean(EXTRA_HLS, prefersHls(track))
            putBoolean(EXTRA_DOWNLOADABLE, track.isDownloadable)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(artworkLarge(track.artworkUrl ?: track.user?.avatarUrl)?.toUri())
            .setDurationMs(track.duration)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Adds what the player needs to load the song. Items coming from a controller arrive
     * without a URI (Media3 strips it), so the service always calls this.
     */
    fun playable(item: MediaItem, offline: File?, forceProgressive: Boolean = false): MediaItem {
        val id = item.mediaId.toLongOrNull() ?: return item
        val builder = item.buildUpon()
        if (offline != null) {
            builder.setUri(Uri.fromFile(offline)).setMimeType(null)
        } else {
            val hls = !forceProgressive && item.mediaMetadata.extras?.getBoolean(EXTRA_HLS, true) != false
            builder.setUri("$SCHEME://track/$id?hls=${if (hls) 1 else 0}".toUri())
                .setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else MimeTypes.AUDIO_MPEG)
        }
        return builder.build()
    }

    /** The song behind a player item, for screens that only have the item */
    fun trackOf(item: MediaItem): Track {
        val m = item.mediaMetadata
        val extras = m.extras
        val hls = extras?.getBoolean(EXTRA_HLS, true) != false
        return Track(
            id = item.mediaId.toLongOrNull() ?: 0,
            title = m.title?.toString().orEmpty(),
            user = User(id = extras?.getLong(EXTRA_USER_ID) ?: 0, username = m.artist?.toString().orEmpty(), avatarUrl = extras?.getString(EXTRA_AVATAR)),
            artworkUrl = m.artworkUri?.toString(),
            duration = m.durationMs ?: 0,
            permalinkUrl = extras?.getString(EXTRA_PERMALINK),
            downloadable = extras?.getBoolean(EXTRA_DOWNLOADABLE) == true,
            media = Media(listOf(Transcoding(format = Format(protocol = if (hls) "hls" else "progressive")))),
        )
    }
}
