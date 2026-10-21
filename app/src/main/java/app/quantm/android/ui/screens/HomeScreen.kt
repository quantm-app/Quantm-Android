package app.quantm.android.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.Item
import app.quantm.android.data.Track
import app.quantm.android.graph
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.SettingsRoute
import app.quantm.android.ui.components.Avatar
import app.quantm.android.ui.components.Carousel
import app.quantm.android.ui.components.ErrorState
import app.quantm.android.ui.components.LocalBottomPadding
import app.quantm.android.ui.components.MessageState
import app.quantm.android.ui.components.PlaylistRow
import app.quantm.android.ui.components.SectionHeader
import app.quantm.android.ui.components.Skeleton
import app.quantm.android.ui.components.SkeletonCarousel
import app.quantm.android.ui.components.SkeletonRows
import app.quantm.android.ui.components.TrackRow
import app.quantm.android.ui.components.pagedFooter
import app.quantm.android.ui.components.timeAgo
import app.quantm.android.ui.rememberLoadable
import app.quantm.android.ui.rememberPaged
import java.time.LocalTime

@Composable
fun HomeScreen() {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val me by graph.social.me.collectAsStateWithLifecycle()
    val tab by graph.settings.homeTab.flow.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current

    val sections = rememberLoadable("home") { graph.api.mixedSelections() }
    val trending = rememberPaged("trending") { graph.api.trending() }
    val feed = rememberPaged("feed") { graph.api.stream() }
    val forYouList = rememberLazyListState()
    val feedList = rememberLazyListState()

    val refreshing = if (tab == 0) sections.refreshing || trending.refreshing else feed.refreshing
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (tab == 0) { sections.reload(); trending.refresh() } else feed.refresh()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = if (tab == 0) forYouList else feedList,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottom + 16.dp),
        ) {
            item(key = "top") {
                Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(greeting(), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                            me?.let {
                                Text(it.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Settings", role = Role.Button) { actions.navigate(SettingsRoute) }
                                .semantics { contentDescription = "Your profile" }
                        ) {
                            Avatar(me?.avatarUrl, 40.dp, seed = me?.id ?: 0)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("For you", "Feed").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = tab == i,
                                onClick = { graph.settings.homeTab.value = i },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(label) }
                        }
                    }
                }
            }

            if (tab == 0) {
                val value = sections.value
                when {
                    value == null && sections.error != null -> item { ErrorState(sections.error!!, onRetry = sections::reload) }
                    value == null -> item {
                        Column {
                            repeat(2) {
                                Skeleton(Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp).width(160.dp).height(20.dp))
                                SkeletonCarousel()
                            }
                        }
                    }
                    else -> value.forEach { section ->
                        item(key = "s:" + section.title, contentType = "section") {
                            SectionHeader(section.title, subtitle = section.description)
                            Carousel(section.items)
                        }
                    }
                }
                item(key = "trendingHeader") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                        Box(Modifier.weight(1f)) { SectionHeader("Trending on SoundCloud") }
                        if (trending.items.isNotEmpty()) {
                            FilledTonalButton(onClick = { graph.player.play(trending.items, 0, context = "Trending") }) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Play")
                            }
                        }
                    }
                }
                if (trending.initial) item { SkeletonRows(6) }
                itemsIndexed(trending.items.take(20), key = { _, t -> "tr" + t.id }, contentType = { _, _ -> "track" }) { i, track ->
                    TrackRow(track, index = i + 1, onClick = { graph.player.play(trending.items, i, context = "Trending") })
                }
                trending.error?.let { item { ErrorState(it, trending::retry) } }
            } else {
                if (feed.initial) item { SkeletonRows(8) }
                if (feed.done && feed.items.isEmpty()) item {
                    MessageState(Icons.Rounded.DynamicFeed, "Your feed is empty", "Follow artists on SoundCloud and their new tracks and reposts show up here.")
                }
                val feedTracks: List<Track> = feed.items.mapNotNull { (it.item as? Item.TrackItem)?.track }
                itemsIndexed(feed.items, key = { _, post -> post.key }, contentType = { _, post -> if (post.item is Item.TrackItem) "track" else "playlist" }) { _, post ->
                    val header = post.reposter?.let { "${it.username} reposted · ${timeAgo(post.createdAt)}" }
                    when (val item = post.item) {
                        is Item.TrackItem -> TrackRow(
                            item.track,
                            header = header,
                            onClick = { graph.player.play(feedTracks, feedTracks.indexOfFirst { it.id == item.track.id }, context = "Your feed") },
                        )
                        is Item.PlaylistItem -> Column {
                            if (header != null) {
                                Text(header, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                            }
                            PlaylistRow(item.playlist, onClick = { actions.openPlaylist(item.playlist) })
                        }
                        else -> {}
                    }
                }
                pagedFooter(feed)
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
