package app.quantm.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.Playlist
import app.quantm.android.data.Track
import app.quantm.android.data.friendly
import app.quantm.android.graph
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.LocalPlaylistRoute
import app.quantm.android.ui.components.Artwork
import app.quantm.android.ui.components.CenteredLoading
import app.quantm.android.ui.components.ConfirmDialog
import app.quantm.android.ui.components.ErrorState
import app.quantm.android.ui.components.LocalNowPlaying
import app.quantm.android.ui.components.MessageState
import app.quantm.android.ui.components.PlayingBars
import app.quantm.android.ui.components.SkeletonRows
import app.quantm.android.ui.components.TextInputDialog
import app.quantm.android.ui.components.TrackRow
import app.quantm.android.ui.components.formatDate
import app.quantm.android.ui.components.formatLength
import app.quantm.android.ui.components.formatTime
import app.quantm.android.ui.components.plural
import app.quantm.android.ui.rememberLoadable
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
private fun Subtitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
fun PlaylistScreen(id: Long) {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val loaded = rememberLoadable("playlist:$id") { graph.api.playlist(id) }
    val me by graph.social.me.collectAsStateWithLifecycle()
    val likedPlaylists by graph.social.likedPlaylists.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val playlist = loaded.value
    if (playlist == null) {
        if (loaded.error != null) DetailScaffold("", null) { item { ErrorState(loaded.error!!, loaded::reload) } }
        else CenteredLoading()
        return
    }
    val mine = me?.id != null && playlist.user?.id == me?.id
    val tracks = playlist.tracks
    val context = playlist.title

    fun edit(change: suspend (Playlist) -> Unit, done: String) {
        graph.scope.launch {
            runCatching { change(playlist) }
                .onSuccess { graph.messages.show(done); loaded.reload() }
                .onFailure { graph.messages.show("Couldn't update the playlist: " + it.friendly()) }
        }
    }

    DetailScaffold(
        title = playlist.title,
        tintImage = playlist.cover,
        refreshing = loaded.refreshing,
        onRefresh = loaded::reload,
        actions = {
            if (!mine) {
                val saved = playlist.id in likedPlaylists
                IconButton(onClick = { graph.social.togglePlaylistLike(playlist) }) {
                    Icon(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (saved) "Remove from library" else "Save to library",
                        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy to Quantm playlist") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                        menu = false
                        val copy = graph.localPlaylists.create(playlist.title, tracks)
                        graph.messages.show("Copied to Quantm playlists")
                        actions.navigate(LocalPlaylistRoute(copy.id))
                    })
                    DropdownMenuItem(text = { Text("Add all to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }, onClick = { menu = false; graph.player.addToQueue(tracks) })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; actions.share(playlist.title, playlist.permalinkUrl) })
                    DropdownMenuItem(text = { Text("Open in SoundCloud") }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) }, onClick = { menu = false; actions.openInSoundCloud(playlist.permalinkUrl) })
                    if (mine) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        },
    ) {
        item(key = "hero") {
            Hero(playlist.cover, playlist.id, playlist.title) {
                Text(
                    playlist.user?.username.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { actions.openArtist(playlist.user) },
                )
                Subtitle(
                    listOfNotNull(
                        playlist.kindLabel,
                        formatDate(playlist.releaseDate ?: playlist.publishedAt ?: playlist.createdAt).takeLast(4).takeIf { it.isNotBlank() },
                        plural(maxOf(playlist.trackCount, tracks.size).toLong(), "song"),
                        formatLength(playlist.duration.takeIf { it > 0 } ?: tracks.sumOf { it.duration }),
                        if (playlist.sharing == "private") "Private" else null,
                    ).joinToString(" · ")
                )
            }
        }
        item(key = "play") {
            PlayShuffleRow(
                onPlay = { graph.player.play(tracks, 0, context = context) },
                onShuffle = { graph.player.play(tracks, shuffle = true, context = context) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (!playlist.description.isNullOrBlank()) item(key = "desc") { ExpandableText(playlist.description, collapsedLines = 2) }
        if (tracks.isEmpty()) item { MessageState(Icons.Rounded.MusicNote, "This playlist is empty") }
        itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, track ->
            TrackRow(
                track,
                index = if (playlist.isAlbum) i + 1 else null,
                onClick = { graph.player.play(tracks, i, context = context) },
                onRemove = if (mine) ({
                    edit({ graph.api.setPlaylistTracks(it.id, tracks.map(Track::id).filterIndexed { j, _ -> j != i }) }, "Removed from ${playlist.title}")
                }) else null,
            )
        }
    }

    if (renaming) {
        TextInputDialog("Rename playlist", "Name", "Save", initial = playlist.title, onDismiss = { renaming = false }) { name ->
            renaming = false
            edit({ graph.api.renamePlaylist(it.id, name) }, "Renamed")
        }
    }
    if (deleting) {
        ConfirmDialog("Delete \"${playlist.title}\"?", "This deletes it from SoundCloud for everyone.", "Delete", onDismiss = { deleting = false }) {
            graph.scope.launch {
                runCatching { graph.api.deletePlaylist(playlist.id) }
                    .onSuccess { graph.messages.show("Deleted"); actions.back() }
                    .onFailure { graph.messages.show("Couldn't delete it: " + it.friendly()) }
            }
        }
    }
}

@Composable
fun StationScreen(urn: String, title: String) {
    val graph = LocalContext.current.graph
    val loaded = rememberLoadable("station:$urn") { graph.api.systemPlaylist(urn) }
    val station = loaded.value
    val name = station?.title?.takeIf { it.isNotBlank() } ?: title
    DetailScaffold(name, station?.cover, refreshing = loaded.refreshing, onRefresh = loaded::reload) {
        item(key = "hero") {
            Hero(station?.cover, urn.hashCode().toLong(), name) {
                Subtitle(station?.description ?: "A mix of songs like this, made by SoundCloud")
            }
        }
        val tracks = station?.tracks.orEmpty()
        item(key = "play") {
            PlayShuffleRow(
                onPlay = { graph.player.play(tracks, 0, context = name) },
                onShuffle = { graph.player.play(tracks, shuffle = true, context = name) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        when {
            station == null && loaded.error != null -> item { ErrorState(loaded.error!!, loaded::reload) }
            station == null -> item { SkeletonRows() }
            tracks.isEmpty() -> item { MessageState(Icons.Rounded.MusicNote, "This station is empty right now") }
        }
        itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, track ->
            TrackRow(track, onClick = { graph.player.play(tracks, i, context = name) })
        }
    }
}

@Composable
fun LocalPlaylistScreen(id: String) {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val now = LocalNowPlaying.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val all by graph.localPlaylists.playlists.collectAsStateWithLifecycle()
    val playlist = all.find { it.id == id }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(graph.localPlaylists.exportJson(id).toByteArray()) } }
            .onSuccess { graph.messages.show("Exported") }
            .onFailure { graph.messages.show("Couldn't save the file.") }
    }

    if (playlist == null) {
        LaunchedEffect(Unit) { actions.back() }
        return
    }
    var rows by remember { mutableStateOf(playlist.tracks) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(playlist.tracks) { if (!dragging) rows = playlist.tracks }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val a = rows.indexOfFirst { "lt" + it.id == from.key }
        val b = rows.indexOfFirst { "lt" + it.id == to.key }
        if (a < 0 || b < 0) return@rememberReorderableLazyListState
        rows = rows.toMutableList().apply { add(b, removeAt(a)) }
        graph.localPlaylists.move(id, a, b)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    DetailScaffold(
        title = playlist.title,
        tintImage = rows.firstOrNull()?.artworkUrl,
        listState = listState,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Add all to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }, onClick = { menu = false; graph.player.addToQueue(rows) })
                    DropdownMenuItem(text = { Text("Export") }, leadingIcon = { Icon(Icons.Rounded.FileDownload, null) }, onClick = {
                        menu = false
                        exporter.launch(playlist.title.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "playlist" } + ".json")
                    })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; deleting = true })
                }
            }
        },
    ) {
        item(key = "hero") {
            Hero(rows.firstOrNull()?.artworkUrl, id.hashCode().toLong(), playlist.title) {
                Subtitle("Quantm playlist · ${plural(rows.size.toLong(), "song")} · ${formatLength(rows.sumOf { it.duration })}")
            }
        }
        item(key = "play") {
            PlayShuffleRow(
                onPlay = { graph.player.play(rows, 0, context = playlist.title) },
                onShuffle = { graph.player.play(rows, shuffle = true, context = playlist.title) },
                enabled = rows.isNotEmpty(),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (rows.isEmpty()) item {
            MessageState(Icons.Rounded.MusicNote, "No songs yet", "Add songs from any song's menu with Add to playlist.")
        }
        itemsIndexed(rows, key = { _, t -> "lt" + t.id }) { i, track ->
            ReorderableItem(reorder, key = "lt" + track.id) { isDragging ->
                val current = now.trackId == track.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.background)
                        .clickable {
                            if (track.isProtected) actions.trackMenu(track) else graph.player.play(rows, i, context = playlist.title)
                        }
                        .semantics {
                            customActions = buildList {
                                add(CustomAccessibilityAction("Remove") { graph.localPlaylists.remove(id, track.id); true })
                                if (i > 0) add(CustomAccessibilityAction("Move up") { graph.localPlaylists.move(id, i, i - 1); true })
                                if (i < rows.lastIndex) add(CustomAccessibilityAction("Move down") { graph.localPlaylists.move(id, i, i + 1); true })
                            }
                        }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Artwork(track.artworkUrl ?: track.user?.avatarUrl, size = 52.dp, seed = track.id)
                        if (current) Box(Modifier.size(52.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                            PlayingBars(now.playing, Modifier.size(20.dp), androidx.compose.ui.graphics.Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodyLarge, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            track.artist + if (track.duration > 0) " · " + formatTime(track.duration) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { actions.trackMenu(track) { graph.localPlaylists.remove(id, track.id) } }) {
                        Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .clearAndSetSemantics {}
                            .draggableHandle(
                                onDragStarted = { dragging = true; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { dragging = false },
                            ),
                    ) { Icon(Icons.Rounded.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog("Rename playlist", "Name", "Save", initial = playlist.title, onDismiss = { renaming = false }) { name ->
            renaming = false
            graph.localPlaylists.rename(id, name)
        }
    }
    if (deleting) {
        ConfirmDialog("Delete \"${playlist.title}\"?", "It's only on this phone, so this can't be undone. Export it first to keep a copy.", "Delete", onDismiss = { deleting = false }) {
            graph.localPlaylists.delete(id)
        }
    }
}
