package app.quantm.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.Item
import app.quantm.android.data.Track
import app.quantm.android.data.friendly
import app.quantm.android.graph
import app.quantm.android.ui.DownloadsRoute
import app.quantm.android.ui.HistoryRoute
import app.quantm.android.ui.LikesRoute
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.LocalPlaylistRoute
import app.quantm.android.ui.LocalPlaylistsRoute
import app.quantm.android.ui.PlaylistsRoute
import app.quantm.android.ui.SettingsRoute
import app.quantm.android.ui.UsersRoute
import app.quantm.android.ui.components.Artwork
import app.quantm.android.ui.components.ConfirmDialog
import app.quantm.android.ui.components.ErrorState
import app.quantm.android.ui.components.ListRow
import app.quantm.android.ui.components.ListScreen
import app.quantm.android.ui.components.LocalBottomPadding
import app.quantm.android.ui.components.MessageState
import app.quantm.android.ui.components.NavRow
import app.quantm.android.ui.components.PlaylistRow
import app.quantm.android.ui.components.SectionHeader
import app.quantm.android.ui.components.SkeletonRows
import app.quantm.android.ui.components.TextInputDialog
import app.quantm.android.ui.components.TrackRow
import app.quantm.android.ui.components.UserRow
import app.quantm.android.ui.components.formatBytes
import app.quantm.android.ui.components.formatCount
import app.quantm.android.ui.components.pagedFooter
import app.quantm.android.ui.components.plural
import app.quantm.android.ui.rememberPaged
import app.quantm.android.ui.theme.lerpColor
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen() {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val bottom = LocalBottomPadding.current
    val me by graph.social.me.collectAsStateWithLifecycle()
    val liked by graph.social.likedTracks.collectAsStateWithLifecycle()
    val local by graph.localPlaylists.playlists.collectAsStateWithLifecycle()
    val downloads by graph.offline.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var shuffling by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = { actions.navigate(SettingsRoute) }) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        item {
            // Liked songs, with Shuffle all over the whole collection
            Box(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(MaterialTheme.colorScheme.primary.let { listOf(it, lerpColor(it, Color.Black, 0.3f)) }))
                    .clickable(onClickLabel = "Open liked songs") { actions.navigate(LikesRoute) }
                    .padding(20.dp)
            ) {
                Column {
                    Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Liked songs", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        if (liked.isEmpty()) "Songs you like on SoundCloud" else plural(liked.size.toLong(), "song"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val id = me?.id ?: return@Button
                            shuffling = true
                            scope.launch {
                                runCatching { graph.api.allTrackLikes(id) }
                                    .onSuccess { graph.player.play(it, shuffle = true, context = "Liked songs") }
                                    .onFailure { graph.messages.show(it.friendly()) }
                                shuffling = false
                            }
                        },
                        enabled = !shuffling && me != null,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (shuffling) "Loading every like…" else "Shuffle all")
                    }
                }
            }
        }
        item {
            NavRow(Icons.Rounded.QueueMusic, "Quantm playlists", "${plural(local.size.toLong(), "playlist")} · saved on this phone", { actions.navigate(LocalPlaylistsRoute) })
        }
        item { NavRow(Icons.Rounded.LibraryMusic, "SoundCloud playlists & albums", "Yours and the ones you saved", { actions.navigate(PlaylistsRoute) }) }
        item {
            NavRow(
                Icons.Rounded.Download, "Downloads",
                if (downloads.isEmpty()) "Plays with no internet" else "${plural(downloads.size.toLong(), "song")} · ${formatBytes(graph.offline.totalBytes)}",
                { actions.navigate(DownloadsRoute) },
            )
        }
        item { NavRow(Icons.Rounded.History, "Recently played", null, { actions.navigate(HistoryRoute) }) }
        me?.let { user ->
            item {
                NavRow(Icons.Rounded.People, "Following", plural(user.followingsCount, "artist"), {
                    actions.navigate(UsersRoute(user.id, followers = false, title = "Following"))
                })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Box(Modifier.weight(1f)) { SectionHeader("Quantm playlists") }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        if (local.isEmpty()) {
            item {
                Text(
                    "Make playlists that live on your phone: adding and removing songs is instant, and you can move them to desktop Quantm with Export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(local, key = { it.id }, contentType = { "playlist" }) { p ->
            ListRow(
                title = p.title,
                subtitle = "Quantm playlist · ${plural(p.tracks.size.toLong(), "song")}",
                onClick = { actions.navigate(LocalPlaylistRoute(p.id)) },
                leading = { Artwork(p.tracks.firstOrNull()?.artworkUrl, size = 56.dp, seed = p.id.hashCode().toLong()) },
            )
        }
    }

    if (creating) {
        TextInputDialog("New Quantm playlist", "Playlist name", "Create", onDismiss = { creating = false }) { name ->
            creating = false
            val p = graph.localPlaylists.create(name)
            actions.navigate(LocalPlaylistRoute(p.id))
        }
    }
}

/** Header with Play and Shuffle for a list of songs */
@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onPlay, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(6.dp))
            Text("Shuffle")
        }
    }
}

@Composable
fun LikesScreen() {
    val graph = LocalContext.current.graph
    val me by graph.social.me.collectAsStateWithLifecycle()
    val version by graph.social.likesVersion.collectAsStateWithLifecycle()
    val meId = me?.id
    if (meId == null) {
        ListScreen("Liked songs") { item { SkeletonRows() } }
        return
    }
    val likes = rememberPaged("likes") { graph.api.myTrackLikes(meId) }
    val scope = rememberCoroutineScope()
    var loadingAll by remember { mutableStateOf(false) }
    LaunchedEffect(version) { if (version > 0) likes.refresh() }

    fun playAll(shuffle: Boolean) {
        loadingAll = true
        scope.launch {
            runCatching { graph.api.allTrackLikes(meId) }
                .onSuccess { graph.player.play(it, shuffle = shuffle, context = "Liked songs") }
                .onFailure { graph.messages.show(it.friendly()) }
            loadingAll = false
        }
    }

    ListScreen("Liked songs", refreshing = likes.refreshing, onRefresh = likes::refresh) {
        item { PlayShuffleRow(onPlay = { playAll(false) }, onShuffle = { playAll(true) }, enabled = !loadingAll && likes.items.isNotEmpty()) }
        if (likes.initial) item { SkeletonRows() }
        if (likes.done && likes.items.isEmpty()) item {
            MessageState(Icons.Rounded.Favorite, "No liked songs yet", "Tap the heart on any song to save it here.")
        }
        itemsIndexed(likes.items, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { i, track ->
            TrackRow(track, onClick = { graph.player.play(likes.items, i, context = "Liked songs") })
        }
        pagedFooter(likes)
    }
}

@Composable
fun HistoryScreen() {
    val graph = LocalContext.current.graph
    val history = rememberPaged("history") { graph.api.myHistory() }
    ListScreen("Recently played", refreshing = history.refreshing, onRefresh = history::refresh) {
        if (history.initial) item { SkeletonRows() }
        if (history.done && history.items.isEmpty()) item {
            MessageState(Icons.Rounded.History, "Nothing played yet", "Songs you play on SoundCloud show up here.")
        }
        itemsIndexed(history.items, key = { i, t -> "$i:${t.id}" }, contentType = { _, _ -> "track" }) { i, track ->
            TrackRow(track, onClick = { graph.player.play(history.items, i, context = "Recently played") })
        }
        pagedFooter(history)
    }
}

@Composable
fun DownloadsScreen() {
    val graph = LocalContext.current.graph
    val entries by graph.offline.entries.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val tracks: List<Track> = entries.sortedByDescending { it.savedAt }.map { it.track }
    ListScreen("Downloads", actions = {
        if (entries.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Remove all") }
    }) {
        item {
            Text(
                if (entries.isEmpty()) "" else "${plural(entries.size.toLong(), "song")} · ${formatBytes(graph.offline.totalBytes)} · plays with no internet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (tracks.isNotEmpty()) item {
            PlayShuffleRow(
                onPlay = { graph.player.play(tracks, 0, context = "Downloads") },
                onShuffle = { graph.player.play(tracks, shuffle = true, context = "Downloads") },
            )
        }
        if (entries.isEmpty()) item {
            MessageState(
                Icons.Rounded.CloudDownload,
                "No downloads yet",
                "Songs whose artist allows downloads have a Download option in their menu. Downloaded songs play with no internet.",
            )
        }
        itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { i, track ->
            TrackRow(track, onClick = { graph.player.play(tracks, i, context = "Downloads") }, onRemove = { graph.offline.remove(track.id) })
        }
    }
    if (confirm) {
        ConfirmDialog("Remove all downloads?", "They'll need the internet again to play.", "Remove all", onDismiss = { confirm = false }) {
            graph.offline.removeAll()
        }
    }
}

@Composable
fun LocalPlaylistsScreen() {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val local by graph.localPlaylists.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        graph.scope.launch {
            // the file may be in the cloud: read it off the main thread
            runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                graph.localPlaylists.importJson(text)
            }.onSuccess { graph.messages.show("Imported ${plural(it.toLong(), "playlist")}") }
                .onFailure { graph.messages.show("Couldn't import that file. Pick a playlists file exported from Quantm.") }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(graph.localPlaylists.exportJson().toByteArray()) }
        }.onSuccess { graph.messages.show("Exported your Quantm playlists") }
            .onFailure { graph.messages.show("Couldn't save the file.") }
    }

    ListScreen("Quantm playlists", actions = {
        IconButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Icon(Icons.Rounded.FileUpload, "Import playlists") }
        IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "New playlist") }
    }) {
        item {
            Text(
                "Saved on this phone: adding, removing and reordering songs is instant and never blocked by SoundCloud. Import and Export use the same file as desktop Quantm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (local.isEmpty()) item {
            MessageState(Icons.AutoMirrored.Rounded.PlaylistPlay, "No Quantm playlists yet", null, action = "New playlist", onAction = { creating = true })
        }
        items(local, key = { it.id }, contentType = { "playlist" }) { p ->
            ListRow(
                title = p.title,
                subtitle = plural(p.tracks.size.toLong(), "song"),
                onClick = { actions.navigate(LocalPlaylistRoute(p.id)) },
                leading = { Artwork(p.tracks.firstOrNull()?.artworkUrl, size = 56.dp, seed = p.id.hashCode().toLong()) },
            )
        }
        if (local.isNotEmpty()) item {
            TextButton(onClick = { exporter.launch("quantm-playlists.json") }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Export all playlists")
            }
        }
    }
    if (creating) {
        TextInputDialog("New Quantm playlist", "Playlist name", "Create", onDismiss = { creating = false }) { name ->
            creating = false
            val p = graph.localPlaylists.create(name)
            actions.navigate(LocalPlaylistRoute(p.id))
        }
    }
}

@Composable
fun PlaylistsScreen() {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val library = rememberPaged("library") { graph.api.myLibrary() }
    ListScreen("SoundCloud playlists & albums", refreshing = library.refreshing, onRefresh = library::refresh) {
        if (library.initial) item { SkeletonRows() }
        if (library.done && library.items.isEmpty()) item {
            MessageState(Icons.Rounded.LibraryMusic, "Nothing here yet", "Playlists and albums you make or save on SoundCloud show up here.")
        }
        items(library.items, key = { it.key }, contentType = { "playlist" }) { item ->
            when (item) {
                is Item.PlaylistItem -> PlaylistRow(item.playlist, onClick = { actions.open(item) })
                is Item.SystemItem -> ListRow(
                    title = item.playlist.title,
                    subtitle = item.playlist.shortDescription ?: "Station",
                    onClick = { actions.open(item) },
                    leading = { Artwork(item.playlist.cover, size = 56.dp, seed = item.playlist.urn.hashCode().toLong()) },
                )
                else -> {}
            }
        }
        pagedFooter(library)
    }
}

@Composable
fun UsersScreen(userId: Long, followers: Boolean, title: String) {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val following by graph.social.following.collectAsStateWithLifecycle()
    val users = rememberPaged("users:$userId:$followers") { if (followers) graph.api.userFollowers(userId) else graph.api.userFollowings(userId) }
    ListScreen(title, refreshing = users.refreshing, onRefresh = users::refresh) {
        if (users.initial) item { SkeletonRows() }
        users.error?.takeIf { users.items.isEmpty() }?.let { item { ErrorState(it, users::retry) } }
        items(users.items, key = { it.id }, contentType = { "user" }) { user ->
            UserRow(user, onClick = { actions.openArtist(user) }, trailing = {
                val on = user.id in following
                TextButton(
                    onClick = { graph.social.toggleFollow(user) },
                    modifier = Modifier.semantics { contentDescription = (if (on) "Unfollow " else "Follow ") + user.username },
                ) {
                    Text(if (on) "Following" else "Follow", color = if (on) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
            })
        }
        pagedFooter(users)
    }
}
