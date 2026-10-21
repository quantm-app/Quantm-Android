package app.quantm.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.Item
import app.quantm.android.data.Track
import app.quantm.android.graph
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.StationRoute
import app.quantm.android.ui.UsersRoute
import app.quantm.android.ui.components.Avatar
import app.quantm.android.ui.components.CenteredLoading
import app.quantm.android.ui.components.ErrorState
import app.quantm.android.ui.components.MessageState
import app.quantm.android.ui.components.PlaylistRow
import app.quantm.android.ui.components.SkeletonRows
import app.quantm.android.ui.components.TrackRow
import app.quantm.android.ui.components.formatCount
import app.quantm.android.ui.components.pagedFooter
import app.quantm.android.ui.components.plural
import app.quantm.android.ui.rememberLoadable
import app.quantm.android.ui.rememberPaged
import coil3.compose.AsyncImage

private val TABS = listOf("Popular", "Tracks", "Albums", "Playlists", "Reposts")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistScreen(id: Long) {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val loaded = rememberLoadable("user:$id") { graph.api.user(id) }
    val following by graph.social.following.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    val popular = rememberPaged("top:$id") { graph.api.userTopTracks(id) }
    val user = loaded.value
    if (user == null) {
        if (loaded.error != null) DetailScaffold("", null) { item { ErrorState(loaded.error!!, loaded::reload) } }
        else CenteredLoading()
        return
    }
    val banner = user.visuals?.visuals?.firstOrNull()?.visualUrl
    // each tab's list is created when the tab is first opened, and kept afterwards
    val tracks = if (tab == 1) rememberPaged("tracks:$id") { graph.api.userTracks(id) } else null
    val playlists = when (tab) {
        2 -> rememberPaged("albums:$id") { graph.api.userAlbums(id) }
        3 -> rememberPaged("playlists:$id") { graph.api.userPlaylists(id) }
        else -> null
    }
    val reposts = if (tab == 4) rememberPaged("reposts:$id") { graph.api.userReposts(id) } else null

    DetailScaffold(
        title = user.username,
        tintImage = user.avatarUrl,
        refreshing = loaded.refreshing,
        onRefresh = loaded::reload,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; actions.share(user.username, user.permalinkUrl) })
                    DropdownMenuItem(text = { Text("Open in SoundCloud") }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) }, onClick = { menu = false; actions.openInSoundCloud(user.permalinkUrl) })
                }
            }
        },
    ) {
        item(key = "hero") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (banner != null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(3.2f).clip(MaterialTheme.shapes.large)) {
                        AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
                    }
                }
                Avatar(
                    user.avatarUrl, 120.dp, seed = user.id,
                    modifier = if (banner != null) Modifier.offset(y = (-40).dp) else Modifier.padding(top = 8.dp),
                )
                Column(
                    Modifier.offset(y = if (banner != null) (-28).dp else 0.dp).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.username, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        if (user.verified) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Verified, "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }
                    val place = listOfNotNull(user.fullName?.takeIf { it.isNotBlank() && it != user.username }, user.city?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    if (place.isNotEmpty()) Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            plural(user.followersCount, "follower"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable(onClickLabel = "Show followers", role = Role.Button) { actions.navigate(UsersRoute(user.id, true, "${user.username}'s followers")) },
                        )
                        Text(
                            "${formatCount(user.followingsCount)} following",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable(onClickLabel = "Show following", role = Role.Button) { actions.navigate(UsersRoute(user.id, false, "${user.username} follows")) },
                        )
                        Text(plural(user.trackCount, "song"), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isFollowing = user.id in following
                        if (isFollowing) {
                            OutlinedButton(onClick = { graph.social.toggleFollow(user) }) { Text("Following") }
                        } else {
                            Button(onClick = { graph.social.toggleFollow(user) }) { Text("Follow") }
                        }
                        FilledTonalButton(onClick = {
                            val urn = user.stationUrn ?: "soundcloud:system-playlists:artist-stations:${user.id}"
                            actions.navigate(StationRoute(urn, "${user.username} station"))
                        }) {
                            Icon(Icons.Rounded.Sensors, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Station")
                        }
                        if (popular.items.isNotEmpty()) {
                            IconButton(
                                onClick = { graph.player.play(popular.items, 0, context = user.username) },
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                    }
                }
            }
        }
        if (!user.description.isNullOrBlank()) item(key = "bio") { ExpandableText(user.description, collapsedLines = 2) }
        item(key = "tabs") {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = Color.Transparent, divider = {}) {
                TABS.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }, selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        val play = { list: List<Track>, i: Int -> graph.player.play(list, i, context = user.username) }
        when {
            tab == 0 -> {
                trackTab(this, popular.items, popular.initial, popular.done, "No popular songs yet", play)
                pagedFooter(popular)
            }
            tracks != null -> {
                trackTab(this, tracks.items, tracks.initial, tracks.done, "${user.username} hasn't posted songs yet", play)
                pagedFooter(tracks)
            }
            playlists != null -> {
                if (playlists.initial) item { SkeletonRows(4) }
                if (playlists.done && playlists.items.isEmpty()) item {
                    if (tab == 2) MessageState(Icons.Rounded.Album, "No albums") else MessageState(Icons.AutoMirrored.Rounded.QueueMusic, "No playlists")
                }
                items(playlists.items, key = { "p" + it.id }, contentType = { "playlist" }) { p -> PlaylistRow(p, onClick = { actions.openPlaylist(p) }) }
                pagedFooter(playlists)
            }
            reposts != null -> {
                if (reposts.initial) item { SkeletonRows(4) }
                if (reposts.done && reposts.items.isEmpty()) item { MessageState(Icons.Rounded.Repeat, "No reposts yet") }
                val list = reposts.items.mapNotNull { (it.item as? Item.TrackItem)?.track }
                items(reposts.items, key = { "rp" + it.key }) { post ->
                    when (val item = post.item) {
                        is Item.TrackItem -> TrackRow(item.track, onClick = { graph.player.play(list, list.indexOfFirst { it.id == item.track.id }, context = "${user.username}'s reposts") })
                        is Item.PlaylistItem -> PlaylistRow(item.playlist, onClick = { actions.openPlaylist(item.playlist) })
                        else -> {}
                    }
                }
                pagedFooter(reposts)
            }
        }
    }
}

private fun trackTab(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    tracks: List<Track>,
    initial: Boolean,
    done: Boolean,
    emptyText: String,
    play: (List<Track>, Int) -> Unit,
) = with(scope) {
    if (initial) item { SkeletonRows(5) }
    if (done && tracks.isEmpty()) item { MessageState(Icons.Rounded.MusicNote, emptyText) }
    itemsIndexed(tracks, key = { _, t -> "t" + t.id }, contentType = { _, _ -> "track" }) { i, t -> TrackRow(t, onClick = { play(tracks, i) }) }
}
