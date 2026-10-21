package app.quantm.android.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import app.quantm.android.data.Item
import app.quantm.android.data.Playlist
import app.quantm.android.data.Track
import app.quantm.android.data.User
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data class TrackRoute(val id: Long)
@Serializable data class ArtistRoute(val id: Long)
@Serializable data class PlaylistRoute(val id: Long)
@Serializable data class StationRoute(val urn: String, val title: String)
@Serializable data class LocalPlaylistRoute(val id: String)
@Serializable data object LikesRoute
@Serializable data object HistoryRoute
@Serializable data object DownloadsRoute
@Serializable data object PlaylistsRoute
@Serializable data object LocalPlaylistsRoute
@Serializable data class UsersRoute(val userId: Long, val followers: Boolean, val title: String)
@Serializable data object SettingsRoute
@Serializable data object EqualizerRoute
@Serializable data object ThemesRoute

/** What any screen or row can ask the app to do */
@Stable
interface Actions {
    fun navigate(route: Any)
    fun back()
    fun trackMenu(track: Track, removable: (() -> Unit)? = null)
    fun addToPlaylist(tracks: List<Track>)
    fun share(title: String, url: String?)
    fun openInSoundCloud(url: String?)
    fun expandPlayer()

    fun open(item: Item) = when (item) {
        is Item.TrackItem -> navigate(TrackRoute(item.track.id))
        is Item.PlaylistItem -> navigate(PlaylistRoute(item.playlist.id))
        is Item.SystemItem -> navigate(StationRoute(item.playlist.urn, item.playlist.title))
        is Item.UserItem -> navigate(ArtistRoute(item.user.id))
    }

    fun openArtist(user: User?) { if (user != null && user.id != 0L) navigate(ArtistRoute(user.id)) }
    fun openPlaylist(playlist: Playlist) = navigate(PlaylistRoute(playlist.id))
}

val LocalActions = staticCompositionLocalOf<Actions> { error("No actions") }
