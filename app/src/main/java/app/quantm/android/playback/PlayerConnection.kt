package app.quantm.android.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.quantm.android.Graph
import app.quantm.android.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
    /** "Likes", a playlist's title... shown as "Playing from" */
    val context: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
    val upNext: List<Track> get() = if (index < 0) emptyList() else queue.drop(index + 1)
}

/**
 * The screens' handle on the playback service. Commands go through a MediaController, so the
 * notification and lock screen stay in sync with whatever the app does.
 */
class PlayerConnection(private val context: Context, private val graph: Graph) {
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var connecting = false

    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PlayerState(context = prefs.getString("context", null)))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** The order before shuffle was turned on, to restore it when it's turned off */
    private var unshuffled: List<Track>? = null

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh(c, timeline = true)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) action(c) else {
            controller = null
            pending += action
            connect()
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(player, timeline = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    private fun refresh(player: Player, timeline: Boolean) {
        _state.update { old ->
            old.copy(
                queue = if (timeline || old.queue.size != player.mediaItemCount) queueOf(player) else old.queue,
                index = if (player.mediaItemCount == 0) -1 else player.currentMediaItemIndex,
                isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING),
                buffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.takeIf { it > 0 } ?: old.current?.duration ?: 0,
                repeatMode = player.repeatMode,
                speed = player.playbackParameters.speed,
            )
        }
    }

    private fun queueOf(player: Player): List<Track> = (0 until player.mediaItemCount).map { i ->
        val item = player.getMediaItemAt(i)
        item.mediaId.toLongOrNull()?.let(graph.api::cached) ?: MediaItems.trackOf(item)
    }

    /** Position in ms, ticking while collected */
    fun position(): Flow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition ?: 0)
            delay(200)
        }
    }

    fun bufferedPosition(): Long = controller?.bufferedPosition ?: 0

    fun currentPosition(): Long = controller?.currentPosition ?: 0

    fun isCurrent(trackId: Long) = _state.value.current?.id == trackId

    // ---- queue ---------------------------------------------------------------------

    /**
     * Play a list of songs starting at [start]. Copy-protected songs are left out (Quantm can't
     * play them). With [shuffle] the order is random, starting anywhere.
     */
    fun play(tracks: List<Track>, start: Int = 0, shuffle: Boolean = false, context: String? = null) {
        val chosen = tracks.getOrNull(start)
        val playable = tracks.filter { !it.isProtected && !it.isStub }
        if (playable.isEmpty()) {
            graph.messages.show(if (tracks.isNotEmpty()) "These songs are copy-protected. Open them in SoundCloud." else "Nothing to play")
            return
        }
        playable.forEach { graph.api.cache(it) }
        val list: List<Track>
        val index: Int
        if (shuffle) {
            list = playable.shuffled()
            index = 0
            unshuffled = playable
        } else {
            list = playable
            index = playable.indexOfFirst { it.id == chosen?.id }.coerceAtLeast(0)
            unshuffled = null
        }
        _state.update { it.copy(shuffle = shuffle, context = context) }
        prefs.edit().putString("context", context).apply()
        withController { c ->
            c.setMediaItems(list.map(MediaItems::from), index, 0)
            c.prepare()
            c.play()
        }
    }

    fun playNext(tracks: List<Track>) {
        val playable = tracks.filter { !it.isProtected && !it.isStub }.onEach { graph.api.cache(it) }
        if (playable.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) return@withController play(playable)
            c.addMediaItems(c.currentMediaItemIndex + 1, playable.map(MediaItems::from))
        }
        graph.messages.show(if (playable.size == 1) "Playing \"${playable[0].title}\" next" else "Playing ${playable.size} songs next")
    }

    fun addToQueue(tracks: List<Track>) {
        val playable = tracks.filter { !it.isProtected && !it.isStub }.onEach { graph.api.cache(it) }
        if (playable.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) return@withController play(playable)
            c.addMediaItems(playable.map(MediaItems::from))
        }
        graph.messages.show(if (playable.size == 1) "Added \"${playable[0].title}\" to the queue" else "Added ${playable.size} songs to the queue")
    }

    fun jump(index: Int) = withController { c ->
        c.seekTo(index, 0)
        c.play()
    }

    fun move(from: Int, to: Int) = withController { c -> c.moveMediaItem(from, to) }

    fun removeAt(index: Int) = withController { c -> c.removeMediaItem(index) }

    fun clearUpNext() = withController { c ->
        val next = c.currentMediaItemIndex + 1
        if (next < c.mediaItemCount) c.removeMediaItems(next, c.mediaItemCount)
    }

    fun stop() = withController { c ->
        c.stop()
        c.clearMediaItems()
        _state.update { it.copy(context = null, shuffle = false) }
    }

    // ---- transport -----------------------------------------------------------------

    fun toggle() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }

    fun next() = withController { c -> if (c.hasNextMediaItem()) c.seekToNextMediaItem() }

    /** Back to the start of the song, or to the previous one in the first 3 seconds */
    fun previous() = withController { c -> c.seekToPrevious() }

    fun seekTo(ms: Long) = withController { c -> c.seekTo(ms.coerceAtLeast(0)) }

    fun seekBy(deltaMs: Long) = withController { c -> c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0)) }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = withController { c -> c.playbackParameters = PlaybackParameters(speed) }

    /**
     * Shuffle reorders what's coming up (the queue always shows the real order). Turning it off
     * puts the remaining songs back in their original order.
     */
    fun toggleShuffle() = withController { c ->
        val index = c.currentMediaItemIndex
        val queue = _state.value.queue
        if (index < 0 || queue.isEmpty()) return@withController
        val upcoming = queue.drop(index + 1)
        val on = !_state.value.shuffle
        val reordered: List<Track> = if (on) {
            unshuffled = queue
            upcoming.shuffled()
        } else {
            val original = unshuffled ?: queue
            // how many of each song are still to come (the same song can be queued twice)
            val remaining = HashMap<Long, Int>()
            upcoming.forEach { remaining.merge(it.id, 1, Int::plus) }
            val currentId = queue[index].id
            val from = original.indexOfFirst { it.id == currentId }
            val ordered = (original.drop(from + 1) + original.take(from + 1)).filter { t ->
                val left = remaining[t.id] ?: 0
                if (left > 0) remaining[t.id] = left - 1
                left > 0
            }
            unshuffled = null
            ordered + upcoming.filter { u ->
                val left = remaining[u.id] ?: 0
                if (left > 0) remaining[u.id] = left - 1
                left > 0
            }
        }
        _state.update { it.copy(shuffle = on) }
        if (upcoming.isNotEmpty()) c.replaceMediaItems(index + 1, c.mediaItemCount, reordered.map(MediaItems::from))
    }

    fun currentItem(): MediaItem? = controller?.currentMediaItem
}
