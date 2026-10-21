package app.quantm.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.Comment
import app.quantm.android.data.Track
import app.quantm.android.data.friendly
import app.quantm.android.graph
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.components.Avatar
import app.quantm.android.ui.components.CenteredLoading
import app.quantm.android.ui.components.ErrorState
import app.quantm.android.ui.components.LoadMoreFooter
import app.quantm.android.ui.components.LocalNowPlaying
import app.quantm.android.ui.components.SectionHeader
import app.quantm.android.ui.components.SkeletonRows
import app.quantm.android.ui.components.TrackRow
import app.quantm.android.ui.components.formatCount
import app.quantm.android.ui.components.formatDate
import app.quantm.android.ui.components.formatTime
import app.quantm.android.ui.components.timeAgo
import app.quantm.android.ui.rememberLoadable
import app.quantm.android.ui.rememberPaged
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackScreen(id: Long) {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val now = LocalNowPlaying.current
    val loaded = rememberLoadable("track:$id") { graph.api.track(id) }
    val track = loaded.value ?: graph.api.cached(id)
    val related = rememberPaged("related:$id") { graph.api.related(id) }
    val comments = rememberPaged("comments:$id") { graph.api.comments(id) }
    val liked by graph.social.likedTracks.collectAsStateWithLifecycle()
    val reposted by graph.social.repostedTracks.collectAsStateWithLifecycle()
    val downloads by graph.offline.entries.collectAsStateWithLifecycle()
    var showAllComments by remember { mutableStateOf(false) }

    if (track == null) {
        if (loaded.error != null) DetailScaffold("", null) { item { ErrorState(loaded.error!!, loaded::reload) } }
        else CenteredLoading()
        return
    }

    val isCurrent = now.trackId == track.id
    fun playHere() {
        if (isCurrent) graph.player.toggle()
        // this song, then the related ones: like a station that starts with it
        else graph.player.play(listOf(track) + related.items.filter { it.id != track.id }, 0, context = track.title)
    }

    DetailScaffold(
        title = track.title,
        tintImage = track.artworkUrl ?: track.user?.avatarUrl,
        refreshing = loaded.refreshing,
        onRefresh = { loaded.reload(); comments.refresh() },
        actions = { IconButton(onClick = { actions.trackMenu(track) }) { Icon(Icons.Rounded.MoreVert, "More") } },
    ) {
        item(key = "hero") {
            Hero(track.artworkUrl ?: track.user?.avatarUrl, track.id, track.title, size = 260.dp) {
                Row(
                    Modifier.minimumInteractiveComponentSize().clickable(onClickLabel = "Open artist", role = Role.Button) { actions.openArtist(track.user) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(track.user?.avatarUrl, 24.dp, seed = track.user?.id ?: 0)
                    Spacer(Modifier.width(8.dp))
                    Text(track.artist, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                StatRow(
                    listOfNotNull(
                        track.playbackCount?.let { formatCount(it) to "plays" },
                        formatTime(track.duration) to "",
                        formatDate(track.displayDate ?: track.createdAt).takeIf { it.isNotEmpty() }?.let { it to "" },
                    )
                )
            }
        }
        item(key = "actions") {
            if (track.isProtected) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy-protected label song: Quantm can't play it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { actions.openInSoundCloud(track.permalinkUrl) }) {
                        Icon(Icons.Rounded.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open in SoundCloud")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isLiked = track.id in liked
                    IconButton(onClick = { graph.social.toggleLike(track) }) {
                        Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isLiked) "Remove from likes" else "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { graph.social.toggleRepost(track) }) {
                        Icon(Icons.Rounded.Repeat, if (track.id in reposted) "Remove repost" else "Repost", tint = if (track.id in reposted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    FilledIconButton(
                        onClick = ::playHere,
                        modifier = Modifier.size(68.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        val pausing = isCurrent && now.playing
                        Icon(if (pausing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (pausing) "Pause" else "Play", Modifier.size(38.dp))
                    }
                    IconButton(onClick = { actions.addToPlaylist(listOf(track)) }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") }
                    if (track.isDownloadable) {
                        val done = downloads.any { it.track.id == track.id }
                        IconButton(onClick = { if (done) graph.offline.remove(track.id) else graph.offline.download(track) }) {
                            Icon(if (done) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (done) "Remove download" else "Download",
                                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        IconButton(onClick = { actions.share(track.title, track.permalinkUrl) }) { Icon(Icons.Rounded.Share, "Share") }
                    }
                }
            }
        }
        item(key = "stats") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
                Text(
                    listOfNotNull(
                        track.likesCount?.let { "${formatCount(it)} likes" },
                        track.repostsCount?.let { "${formatCount(it)} reposts" },
                        track.commentCount?.let { "${formatCount(it)} comments" },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!track.description.isNullOrBlank()) item(key = "desc") { ExpandableText(track.description) }
        val tags = listOfNotNull(track.genre?.takeIf { it.isNotBlank() }) + tagsOf(track.tagList).take(6)
        if (tags.isNotEmpty()) item(key = "tags") {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // plain labels: there is nothing for a tag to open
                tags.distinct().forEach { tag ->
                    Surface(shape = MaterialTheme.shapes.small, color = Color.Transparent, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Text("#$tag", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }

        val noRelated = related.done && related.items.isEmpty()
        if (!noRelated) item(key = "relatedHeader") { SectionHeader("Fans also like") }
        if (related.initial) item { SkeletonRows(4) }
        itemsIndexed(related.items.take(10), key = { _, t -> "r" + t.id }, contentType = { _, _ -> "track" }) { i, t ->
            TrackRow(t, onClick = { graph.player.play(related.items, i, context = "Related to " + track.title) })
        }

        item(key = "commentsHeader") { SectionHeader("Comments", subtitle = track.commentCount?.let { "${formatCount(it)} total" }) }
        item(key = "compose") { CommentBox(track) { comments.refresh() } }
        if (comments.initial) item(key = "commentsLoading") { SkeletonRows(3) }
        if (comments.done && comments.items.isEmpty()) item(key = "noComments") {
            Text(
                "No comments yet. Be the first to say something.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        comments.error?.takeIf { comments.items.isEmpty() }?.let { item(key = "commentsError") { ErrorState(it, comments::retry) } }
        val shown = if (showAllComments) comments.items else comments.items.take(4)
        items(shown, key = { "c" + it.id }, contentType = { "comment" }) { c ->
            CommentRow(c) { ms ->
                if (isCurrent) graph.player.seekTo(ms) else graph.messages.show("Play the song to jump to this moment.")
            }
        }
        if (!showAllComments && comments.items.size > 4) item(key = "moreComments") {
            TextButton(onClick = { showAllComments = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Show all comments") }
        }
        if (showAllComments && !comments.done) item(key = "commentsFooter") {
            androidx.compose.runtime.LaunchedEffect(comments.items.size) { comments.loadMore() }
            LoadMoreFooter(comments.loading, comments.error, comments::retry)
        }
    }
}

private fun tagsOf(list: String?): List<String> =
    Regex("\"([^\"]+)\"|(\\S+)").findAll(list.orEmpty()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.filter { it.isNotBlank() }.toList()

@Composable
private fun CommentRow(comment: Comment, onTimestamp: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Avatar(comment.user?.avatarUrl, 36.dp, seed = comment.user?.id ?: 0)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.user?.username.orEmpty(), style = MaterialTheme.typography.labelLarge)
                comment.timestamp?.let { ms ->
                    Text(
                        " at ${formatTime(ms)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClickLabel = "Jump to ${formatTime(ms)}", role = Role.Button) { onTimestamp(ms) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(timeAgo(comment.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CommentBox(track: Track, onPosted: () -> Unit) {
    val graph = LocalContext.current.graph
    val now = LocalNowPlaying.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    fun send() {
        val body = text.trim()
        if (body.isEmpty() || sending) return
        sending = true
        scope.launch {
            // comment at the current position when this song is playing, like on soundcloud.com
            val at = if (now.trackId == track.id) graph.player.currentPosition() else 0L
            runCatching { graph.api.postComment(track.id, body, at) }
                .onSuccess {
                    text = ""
                    graph.messages.show("Comment posted")
                    onPosted()
                }
                .onFailure { graph.messages.show("Couldn't post: " + it.friendly()) }
            sending = false
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= 1000) text = it },
        placeholder = { Text("Add a comment") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        singleLine = false,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = ::send, enabled = text.isNotBlank() && !sending) { Icon(Icons.AutoMirrored.Rounded.Send, "Post") }
        },
    )
}
