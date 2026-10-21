package app.quantm.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quantm.android.data.Playlist
import app.quantm.android.data.Track
import app.quantm.android.data.friendly
import app.quantm.android.graph
import app.quantm.android.ui.ArtistRoute
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.StationRoute
import app.quantm.android.ui.TrackRoute
import kotlinx.coroutines.launch

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, tint: androidx.compose.ui.graphics.Color? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun stationUrn(track: Track) = track.stationUrn ?: "soundcloud:system-playlists:track-stations:${track.id}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(track: Track, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val graph = LocalContext.current.graph
    val liked by graph.social.likedTracks.collectAsState()
    val reposted by graph.social.repostedTracks.collectAsState()
    val downloads by graph.offline.entries.collectAsState()
    val progress by graph.offline.progress.collectAsState()
    val isLiked = track.id in liked
    val isReposted = track.id in reposted
    val isDownloaded = downloads.any { it.track.id == track.id }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun run(action: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
        action()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl ?: track.user?.avatarUrl, size = 56.dp, seed = track.id)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (track.isProtected) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(20.dp))
                    Text(
                        "This label song is copy-protected, so Quantm can't play it. You can still listen in SoundCloud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SheetAction(Icons.Rounded.OpenInNew, "Open in SoundCloud", { run { actions.openInSoundCloud(track.permalinkUrl) } }, tint = MaterialTheme.colorScheme.primary)
            } else {
                SheetAction(Icons.Rounded.PlaylistPlay, "Play next", { run { graph.player.playNext(listOf(track)) } })
                SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", { run { graph.player.addToQueue(listOf(track)) } })
            }
            SheetAction(
                if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isLiked) "Remove from likes" else "Like",
                { run { graph.social.toggleLike(track) } },
                tint = if (isLiked) MaterialTheme.colorScheme.primary else null,
            )
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", { run { actions.addToPlaylist(listOf(track)) } })
            SheetAction(
                Icons.Rounded.Repeat,
                if (isReposted) "Remove repost" else "Repost",
                { run { graph.social.toggleRepost(track) } },
                tint = if (isReposted) MaterialTheme.colorScheme.primary else null,
            )
            if (track.isDownloadable && !track.isProtected) {
                val p = progress[track.id]
                when {
                    isDownloaded -> SheetAction(Icons.Rounded.DownloadDone, "Remove download", { run { graph.offline.remove(track.id) } }, tint = MaterialTheme.colorScheme.primary)
                    p != null -> SheetAction(Icons.Rounded.Download, "Downloading ${(p * 100).toInt()}%… tap to cancel", { run { graph.offline.cancel(track.id) } })
                    else -> SheetAction(Icons.Rounded.Download, "Download", { run { graph.offline.download(track) } })
                }
            }
            if (!track.isProtected) SheetAction(Icons.Rounded.Sensors, "Start station", { run { actions.navigate(StationRoute(stationUrn(track), "Based on " + track.title)) } })
            SheetAction(Icons.Rounded.Album, "Go to song", { run { actions.navigate(TrackRoute(track.id)) } })
            if (track.user != null) SheetAction(Icons.Rounded.Person, "Go to ${track.artist}", { run { actions.navigate(ArtistRoute(track.user.id)) } })
            SheetAction(Icons.Rounded.Share, "Share", { run { actions.share(track.title, track.permalinkUrl) } })
            if (!track.isProtected) SheetAction(Icons.Rounded.OpenInNew, "Open in SoundCloud", { run { actions.openInSoundCloud(track.permalinkUrl) } })
            if (onRemove != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SheetAction(Icons.Rounded.RemoveCircleOutline, "Remove from this list", { run(onRemove) }, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(tracks: List<Track>, onDismiss: () -> Unit) {
    val graph = LocalContext.current.graph
    val local by graph.localPlaylists.playlists.collectAsState()
    val me by graph.social.me.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf<String?>(null) }
    var remote by remember { mutableStateOf<List<Playlist>?>(null) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(me?.id) {
        val id = me?.id ?: return@LaunchedEffect
        runCatching { graph.api.myPlaylists(id) }
            .onSuccess { remote = it }
            .onFailure { remoteError = it.friendly() }
    }

    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    if (tracks.size == 1) "Add to playlist" else "Add ${tracks.size} songs to a playlist",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                )
            }
            item { SectionLabel("Quantm playlists (saved on this phone)") }
            item {
                SheetAction(Icons.Rounded.Add, "New Quantm playlist", { creating = "local" }, tint = MaterialTheme.colorScheme.primary)
            }
            items(local, key = { it.id }) { playlist ->
                val all = tracks.all { t -> playlist.tracks.any { it.id == t.id } }
                ListRow(
                    title = playlist.title,
                    subtitle = plural(playlist.tracks.size.toLong(), "song"),
                    modifier = Modifier.semantics {
                        stateDescription = when {
                            !all -> "Not added"
                            tracks.size == 1 -> "Added, tap to remove"
                            else -> "Added"
                        }
                    },
                    onClick = {
                        if (tracks.size == 1 && all) {
                            graph.localPlaylists.remove(playlist.id, tracks[0].id)
                            graph.messages.show("Removed from ${playlist.title}")
                        } else {
                            graph.localPlaylists.addAll(playlist.id, tracks)
                            graph.messages.show("Added to ${playlist.title}")
                            close()
                        }
                    },
                    leading = { Artwork(playlist.tracks.firstOrNull()?.artworkUrl, size = 48.dp, seed = playlist.id.hashCode().toLong()) },
                    trailing = {
                        Icon(
                            if (all) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    },
                )
            }
            if (me != null) {
                item { SectionLabel("SoundCloud playlists") }
                item { SheetAction(Icons.Rounded.Add, "New private SoundCloud playlist", { creating = "remote" }) }
                when {
                    remoteError != null -> item {
                        Text(remoteError!!, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    remote == null -> item { SkeletonRows(3) }
                    else -> items(remote!!.filter { !it.isAlbum }, key = { "r" + it.id }) { playlist ->
                        ListRow(
                            title = playlist.title,
                            subtitle = listOfNotNull(plural(playlist.trackCount.toLong(), "song"), if (playlist.sharing == "private") "Private" else null).joinToString(" · "),
                            onClick = {
                                close()
                                graph.scope.launch {
                                    runCatching {
                                        val full = graph.api.playlist(playlist.id)
                                        val ids = full.tracks.map { it.id }
                                        val add = tracks.map { it.id }.filter { it !in ids }
                                        if (add.isEmpty()) return@runCatching false
                                        graph.api.setPlaylistTracks(playlist.id, ids + add)
                                        true
                                    }.onSuccess { added ->
                                        graph.messages.show(if (added) "Added to ${playlist.title}" else "Already in ${playlist.title}")
                                    }.onFailure {
                                        graph.messages.show("Couldn't update the playlist: " + it.friendly() + " Try again, or add it to a Quantm playlist instead.")
                                    }
                                }
                            },
                            leading = { Artwork(playlist.cover, size = 48.dp, seed = playlist.id) },
                        )
                    }
                }
            }
        }
    }

    creating?.let { kind ->
        TextInputDialog(
            title = if (kind == "local") "New Quantm playlist" else "New private SoundCloud playlist",
            label = "Playlist name",
            confirm = "Create",
            onDismiss = { creating = null },
        ) { name ->
            creating = null
            if (kind == "local") {
                val p = graph.localPlaylists.create(name, tracks)
                graph.messages.show("Created ${p.title}")
                close()
            } else {
                close()
                graph.scope.launch {
                    runCatching { graph.api.createPlaylist(name, tracks.map { it.id }, private = true) }
                        .onSuccess { graph.messages.show("Created ${it.title}") }
                        .onFailure { graph.messages.show("Couldn't create it: " + it.friendly()) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirm: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 300) text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
