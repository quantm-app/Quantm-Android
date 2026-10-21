@file:OptIn(ExperimentalSerializationApi::class)

package app.quantm.android.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/** SoundCloud api-v2 JSON is snake_case and full of fields Quantm doesn't need */
val QuantmJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

@Serializable
data class User(
    val id: Long,
    val username: String = "",
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val permalinkUrl: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val description: String? = null,
    val followersCount: Long = 0,
    val followingsCount: Long = 0,
    val trackCount: Long = 0,
    val playlistCount: Long = 0,
    val verified: Boolean = false,
    val stationUrn: String? = null,
    val visuals: Visuals? = null,
)

@Serializable
data class Visuals(val visuals: List<Visual> = emptyList())

@Serializable
data class Visual(val visualUrl: String? = null)

@Serializable
data class Format(val protocol: String = "", val mimeType: String = "")

@Serializable
data class Transcoding(
    val url: String = "",
    val preset: String? = null,
    val snipped: Boolean = false,
    val quality: String? = null,
    val format: Format = Format(),
)

@Serializable
data class Media(val transcodings: List<Transcoding> = emptyList())

@Serializable
data class PublisherMetadata(val artist: String? = null)

@Serializable
data class Track(
    val id: Long,
    val title: String = "",
    val user: User? = null,
    val artworkUrl: String? = null,
    /** milliseconds */
    val duration: Long = 0,
    val fullDuration: Long? = null,
    val permalinkUrl: String? = null,
    val waveformUrl: String? = null,
    val playbackCount: Long? = null,
    val likesCount: Long? = null,
    val repostsCount: Long? = null,
    val commentCount: Long? = null,
    val genre: String? = null,
    val tagList: String? = null,
    val createdAt: String? = null,
    val displayDate: String? = null,
    val description: String? = null,
    val media: Media? = null,
    val trackAuthorization: String? = null,
    val downloadable: Boolean = false,
    val hasDownloadsLeft: Boolean = true,
    val policy: String? = null,
    val stationUrn: String? = null,
    val publisherMetadata: PublisherMetadata? = null,
    val sharing: String? = null,
) {
    val artist: String get() = user?.username.orEmpty()

    /** Copy-protected label songs only come as DRM-encrypted HLS, which Quantm can't play */
    val isProtected: Boolean
        get() {
            val all = media?.transcodings.orEmpty()
            return all.isNotEmpty() && all.none { it.format.protocol == "hls" || it.format.protocol == "progressive" }
        }

    /** Only a 30-second preview is available (Go+ songs) */
    val isPreview: Boolean
        get() {
            val playable = media?.transcodings.orEmpty().filter { it.format.protocol == "hls" || it.format.protocol == "progressive" }
            return playable.isNotEmpty() && playable.all { it.snipped }
        }

    /** The artist turned on SoundCloud's download button */
    val isDownloadable: Boolean get() = downloadable && hasDownloadsLeft

    /** A stub from a playlist or station that only carries the id */
    val isStub: Boolean get() = title.isEmpty()
}

@Serializable
data class Playlist(
    val id: Long,
    val title: String = "",
    val user: User? = null,
    val artworkUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val trackCount: Int = 0,
    val duration: Long = 0,
    val permalinkUrl: String? = null,
    val isAlbum: Boolean = false,
    val setType: String? = null,
    val sharing: String? = null,
    val likesCount: Long? = null,
    val repostsCount: Long? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val releaseDate: String? = null,
    val publishedAt: String? = null,
) {
    /** Playlists without their own cover borrow the first track's */
    val cover: String? get() = artworkUrl ?: tracks.firstOrNull { it.artworkUrl != null }?.artworkUrl
    val kindLabel: String
        get() = when {
            isAlbum || setType == "album" -> "Album"
            setType == "ep" -> "EP"
            setType == "single" -> "Single"
            else -> "Playlist"
        }
}

/** Stations and SoundCloud's own mixes: the id is a urn and tracks are stubs */
@Serializable
data class SystemPlaylist(
    val urn: String = "",
    val title: String = "",
    val shortTitle: String? = null,
    val description: String? = null,
    val shortDescription: String? = null,
    val artworkUrl: String? = null,
    val calculatedArtworkUrl: String? = null,
    val permalinkUrl: String? = null,
    val user: User? = null,
    val tracks: List<Track> = emptyList(),
) {
    val cover: String? get() = artworkUrl ?: calculatedArtworkUrl ?: tracks.firstOrNull { it.artworkUrl != null }?.artworkUrl
}

@Serializable
data class Comment(
    val id: Long,
    val body: String = "",
    /** position in the track, ms */
    val timestamp: Long? = null,
    val createdAt: String? = null,
    val user: User? = null,
)

/** One card on Home or in search results */
sealed interface Item {
    val key: String

    data class TrackItem(val track: Track) : Item {
        override val key get() = "t${track.id}"
    }

    data class PlaylistItem(val playlist: Playlist) : Item {
        override val key get() = "p${playlist.id}"
    }

    data class SystemItem(val playlist: SystemPlaylist) : Item {
        override val key get() = "s${playlist.urn}"
    }

    data class UserItem(val user: User) : Item {
        override val key get() = "u${user.id}"
    }
}

/** Feed post: a track or playlist, possibly reposted by someone you follow */
data class FeedPost(
    val key: String,
    val item: Item,
    val reposter: User?,
    val createdAt: String?,
)

data class Section(val title: String, val description: String?, val items: List<Item>)
