package app.quantm.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.quantm.android.MainActivity
import app.quantm.android.R
import app.quantm.android.data.Track
import app.quantm.android.graph
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Quantm's player. It owns the queue and keeps playing with the screen off; the notification,
 * lock screen, Bluetooth buttons, Android Auto and the app's own screens all control it through
 * its media session.
 */
class PlaybackService : MediaLibraryService() {
    private val graph by lazy { applicationContext.graph }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private var saveJob: Job? = null
    private var fadeJob: Job? = null
    /** Songs that turned out to be progressive-only, and one retry per song for expired URLs */
    private val forceProgressive = HashSet<Long>()
    private val retried = HashSet<String>()

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpDataSource.Factory(graph.http)
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http), graph.resolver)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(listener)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(open)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) }
        )

        restoreQueue()
        graph.audioFx.attach(player.audioSessionId)
        scope.launch { graph.settings.skipSilence.flow.collect { player.skipSilenceEnabled = it } }
        scope.launch { graph.social.likedTracks.collect { updateButtons() } }
        watchSleepTimer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // swiping Quantm away while paused closes it completely; while playing, music keeps going
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            saveQueue()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        scope.cancel()
        graph.audioFx.release()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ---- queue persistence ---------------------------------------------------------

    private fun currentTracks(): List<Track> = (0 until player.mediaItemCount).map { i ->
        val item = player.getMediaItemAt(i)
        val id = item.mediaId.toLongOrNull() ?: 0
        graph.api.cached(id) ?: MediaItems.trackOf(item)
    }

    private fun saveQueue() {
        if (player.mediaItemCount == 0) {
            graph.queueStore.clear()
            return
        }
        graph.queueStore.save(
            SavedQueue(currentTracks(), player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0), player.repeatMode)
        )
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            saveQueue()
        }
    }

    private fun restoreQueue() {
        val saved = graph.queueStore.load() ?: return
        saved.tracks.forEach { graph.api.cache(it) }
        player.setMediaItems(saved.tracks.map { playable(MediaItems.from(it)) }, saved.index.coerceIn(0, saved.tracks.lastIndex), saved.positionMs)
        player.repeatMode = saved.repeatMode
        player.prepare()
    }

    private fun playable(item: MediaItem): MediaItem {
        val id = item.mediaId.toLongOrNull() ?: return item
        return MediaItems.playable(item, graph.offline.fileFor(id), id in forceProgressive)
    }

    // ---- player events -----------------------------------------------------------

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) scheduleSave()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) updateButtons()
            // playing again: songs get a fresh retry next time their stream URL expires
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) retried.clear()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            graph.audioFx.attach(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && graph.sleepTimer.state.value == SleepState.EndOfTrack) {
                // the song that just ended was the last one: stop at the start of the next
                player.pause()
                graph.sleepTimer.cancel()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val index = player.currentMediaItemIndex
            val item = player.currentMediaItem ?: return
            val id = item.mediaId.toLongOrNull() ?: return
            val cause = generateSequence(error.cause) { it.cause }.toList()
            val position = player.currentPosition

            fun reload(newItem: MediaItem) {
                player.replaceMediaItem(index, newItem)
                player.seekTo(index, position)
                player.prepare()
                player.play()
            }

            when {
                // SoundCloud only has a progressive file: load that instead
                cause.any { it is ProgressiveOnlyException } -> {
                    forceProgressive += id
                    reload(playable(item))
                }
                // downloaded since it was queued, or a stream URL that expired during a long pause
                retried.add("$id") && (graph.offline.fileFor(id) != null ||
                    cause.any { it is HttpDataSource.InvalidResponseCodeException && it.responseCode in listOf(401, 403, 404, 410) }) -> {
                    graph.resolver.forget(id)
                    reload(playable(item))
                }
                else -> {
                    val title = item.mediaMetadata.title ?: "this song"
                    val offline = cause.any { it is java.net.UnknownHostException }
                    graph.messages.show(if (offline) "No internet connection" else "Couldn't play \"$title\", skipped it")
                    if (!offline && player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.play()
                    }
                }
            }
        }
    }

    // ---- sleep timer ---------------------------------------------------------------

    private fun watchSleepTimer() {
        scope.launch {
            graph.sleepTimer.state.collect { state ->
                fadeJob?.cancel()
                player.volume = 1f
                if (state is SleepState.At) {
                    fadeJob = scope.launch {
                        while (isActive) {
                            val left = state.endsAt - System.currentTimeMillis()
                            if (left <= 0) break
                            // fade out over the last 10 seconds
                            player.volume = if (left < 10_000) left / 10_000f else 1f
                            delay(if (left < 10_000) 100 else 1000)
                        }
                        player.pause()
                        player.volume = 1f
                        graph.sleepTimer.cancel()
                    }
                }
            }
        }
    }

    // ---- notification buttons and Android Auto -----------------------------------------

    private val likeCommand = SessionCommand(COMMAND_LIKE, Bundle.EMPTY)

    private fun likeButton(): CommandButton {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull()
        val liked = id != null && id in graph.social.likedTracks.value
        return CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (liked) "Remove from likes" else "Like")
            .setSessionCommand(likeCommand)
            .build()
    }

    private fun updateButtons() {
        if (::session.isInitialized) session.setMediaButtonPreferences(ImmutableList.of(likeButton()))
    }

    private val callback = object : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(likeCommand).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(ImmutableList.of(likeButton()))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_LIKE) {
                val item = player.currentMediaItem
                val id = item?.mediaId?.toLongOrNull()
                if (item != null && id != null) graph.social.toggleLike(graph.api.cached(id) ?: MediaItems.trackOf(item))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.map { item ->
                // Android Auto sends only the id: fill in the song's details
                if (item.mediaMetadata.title == null) {
                    val id = item.mediaId.toLongOrNull()
                    val track = id?.let { graph.api.cached(it) ?: runCatching { graph.api.track(it) }.getOrNull() }
                    playable(track?.let { MediaItems.from(it) } ?: item)
                } else playable(item)
            }.toMutableList()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = graph.queueStore.load()
            val result = if (saved == null) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            } else {
                saved.tracks.forEach { graph.api.cache(it) }
                MediaSession.MediaItemsWithStartPosition(saved.tracks.map { playable(MediaItems.from(it)) }, saved.index, saved.positionMs)
            }
            return Futures.immediateFuture(result)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Quantm"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val children: List<MediaItem> = when {
                parentId == ROOT -> listOf(folder(LIKES, "Likes"), folder(PLAYLISTS, "Quantm playlists"), folder(DOWNLOADS, "Downloads"))
                parentId == LIKES -> {
                    val me = graph.social.me.value
                    if (me == null) emptyList()
                    else runCatching { graph.api.myTrackLikes(me.id).next() }.getOrDefault(emptyList()).filter { !it.isProtected }.map(MediaItems::from)
                }
                parentId == DOWNLOADS -> graph.offline.entries.value.map { MediaItems.from(it.track) }
                parentId == PLAYLISTS -> graph.localPlaylists.playlists.value.map { folder(PLAYLIST_PREFIX + it.id, it.title) }
                parentId.startsWith(PLAYLIST_PREFIX) ->
                    graph.localPlaylists.get(parentId.removePrefix(PLAYLIST_PREFIX))?.tracks.orEmpty().map(MediaItems::from)
                else -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val track = mediaId.toLongOrNull()?.let { graph.api.cached(it) ?: runCatching { graph.api.track(it) }.getOrNull() }
            if (track != null) LibraryResult.ofItem(MediaItems.from(track), null)
            else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
        }
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    companion object {
        const val COMMAND_LIKE = "quantm.LIKE"
        private const val ROOT = "root"
        private const val LIKES = "likes"
        private const val PLAYLISTS = "playlists"
        private const val DOWNLOADS = "downloads"
        private const val PLAYLIST_PREFIX = "local:"
    }
}
