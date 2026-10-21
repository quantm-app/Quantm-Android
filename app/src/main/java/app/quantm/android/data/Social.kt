package app.quantm.android.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** Short messages shown as snackbars from anywhere in the app */
class Messages {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = flow.asSharedFlow()
    fun show(text: String) { flow.tryEmit(text) }
}

/** Friendly text for an error, for snackbars and error screens */
fun Throwable.friendly(): String = when {
    this is ApiException && isHumanCheck -> "SoundCloud is asking for a human check. Try again in a few minutes."
    this is ApiException && status == 429 -> "SoundCloud is busy. Try again in a minute."
    this is ApiException && message?.startsWith("SoundCloud error") == false -> message!!
    this is ApiException && status == 404 -> "Not found on SoundCloud."
    this is ApiException -> "SoundCloud didn't accept that (error $status)."
    this is NotSignedInException -> "Sign in to SoundCloud first."
    this is java.net.UnknownHostException || this is java.net.SocketTimeoutException || this is java.net.ConnectException ->
        "No internet connection."
    else -> message ?: "Something went wrong."
}

/**
 * Who you are and what you've liked, reposted and followed. Toggles update instantly and
 * roll back if SoundCloud refuses.
 */
class Social(
    context: Context,
    private val api: SoundCloud,
    private val scope: CoroutineScope,
    private val messages: Messages,
) {
    private val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)

    private val _me = MutableStateFlow(prefs.getString("me", null)?.let { runCatching { QuantmJson.decodeFromString<User>(it) }.getOrNull() })
    val me: StateFlow<User?> = _me.asStateFlow()

    val likedTracks = MutableStateFlow<Set<Long>>(emptySet())
    val likedPlaylists = MutableStateFlow<Set<Long>>(emptySet())
    val repostedTracks = MutableStateFlow<Set<Long>>(emptySet())
    val following = MutableStateFlow<Set<Long>>(emptySet())

    /** Bumped when likes change, so lists of likes can refresh */
    private val _likesVersion = MutableStateFlow(0)
    val likesVersion: StateFlow<Int> = _likesVersion.asStateFlow()

    fun load() {
        scope.launch {
            runCatching { api.me() }.onSuccess { user ->
                _me.value = user
                prefs.edit().putString("me", QuantmJson.encodeToString(User.serializer(), user)).apply()
                val f = async { runCatching { api.followingIds(user.id) }.getOrNull() }
                val lt = async { runCatching { api.likedTrackIds() }.getOrNull() }
                val lp = async { runCatching { api.likedPlaylistIds() }.getOrNull() }
                val rt = async { runCatching { api.repostedTrackIds() }.getOrNull() }
                confirmed.clear()
                lt.await()?.let { likedTracks.value = it }
                lp.await()?.let { likedPlaylists.value = it }
                rt.await()?.let { repostedTracks.value = it }
                f.await()?.let { following.value = it }
            }
        }
    }

    fun clear() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        confirmed.clear()
        prefs.edit().clear().apply()
        _me.value = null
        likedTracks.value = emptySet()
        likedPlaylists.value = emptySet()
        repostedTracks.value = emptySet()
        following.value = emptySet()
    }

    private val writes = kotlinx.coroutines.sync.Mutex()
    private val pending = HashMap<Pair<MutableStateFlow<Set<Long>>, Long>, kotlinx.coroutines.Job>()
    /** What SoundCloud has for each item, as far as Quantm knows */
    private val confirmed = HashMap<Pair<MutableStateFlow<Set<Long>>, Long>, Boolean>()

    /**
     * Flips the item at once. Quick repeated taps are coalesced and requests go out one at a time,
     * so SoundCloud always ends up matching what's on screen.
     */
    private fun toggle(
        set: MutableStateFlow<Set<Long>>,
        id: Long,
        onText: String,
        offText: String,
        action: suspend (Boolean) -> Unit,
    ) {
        val key = set to id
        val wasOn = id in set.value
        confirmed.putIfAbsent(key, wasOn)
        set.update { if (wasOn) it - id else it + id }
        pending.remove(key)?.cancel()
        pending[key] = scope.launch {
            kotlinx.coroutines.delay(350)
            writes.withLock {
                val want = id in set.value
                if (confirmed[key] == want) return@withLock
                runCatching { action(want) }
                    .onSuccess {
                        confirmed[key] = want
                        messages.show(if (want) onText else offText)
                    }
                    .onFailure { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        // put it back the way SoundCloud has it
                        set.update { if (confirmed[key] == true) it + id else it - id }
                        messages.show(error.friendly())
                    }
            }
            pending.remove(key)
        }
    }

    fun toggleLike(track: Track) {
        val meId = _me.value?.id ?: return messages.show("Sign in to like songs.")
        toggle(likedTracks, track.id, "Added to your likes", "Removed from your likes") { on ->
            api.likeTrack(meId, track.id, on)
            _likesVersion.update { it + 1 }
        }
    }

    fun togglePlaylistLike(playlist: Playlist) {
        val meId = _me.value?.id ?: return
        toggle(likedPlaylists, playlist.id, "Saved to your library", "Removed from your library") { on ->
            api.likePlaylist(meId, playlist.id, on)
        }
    }

    fun toggleRepost(track: Track) {
        toggle(repostedTracks, track.id, "Reposted", "Repost removed") { on -> api.repostTrack(track.id, on) }
    }

    fun toggleFollow(user: User) {
        toggle(following, user.id, "Following ${user.username}", "Unfollowed ${user.username}") { on -> api.follow(user.id, on) }
    }
}
