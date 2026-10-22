package app.quantm.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.quantm.android.data.Item
import app.quantm.android.data.Playlist
import app.quantm.android.data.Track
import app.quantm.android.data.User
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.theme.SectionTitle

/** What's playing, for highlighting rows without each row watching the player */
@Immutable
data class NowPlaying(val trackId: Long? = null, val playing: Boolean = false)

val LocalNowPlaying = compositionLocalOf { NowPlaying() }
val LocalDownloaded = compositionLocalOf<Set<Long>> { emptySet() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    header: String? = null,
    onRemove: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val actions = LocalActions.current
    val now = LocalNowPlaying.current
    val downloaded = track.id in LocalDownloaded.current
    val haptics = LocalHapticFeedback.current
    val current = now.trackId == track.id
    val locked = track.isProtected
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (locked) actions.trackMenu(track) else onClick() },
                onLongClickLabel = "More options",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.trackMenu(track, onRemove)
                },
            )
            .semantics { if (current) stateDescription = if (now.playing) "Playing" else "Paused" }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        if (header != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Icon(Icons.Rounded.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(header, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (index != null) {
                Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                    if (current) PlayingBars(now.playing, Modifier.size(16.dp))
                    else Text("$index", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
            }
            Box {
                Artwork(track.artworkUrl ?: track.user?.avatarUrl, size = 52.dp, seed = track.id)
                if (current && index == null) {
                    Box(
                        Modifier.size(52.dp).clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { PlayingBars(now.playing, Modifier.size(20.dp), Color.White) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        current -> MaterialTheme.colorScheme.primary
                        locked -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (locked) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    } else if (downloaded) {
                        Icon(Icons.Rounded.DownloadDone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        buildString {
                            append(track.artist)
                            if (locked) append(" · Copy-protected")
                            else if (track.isPreview) append(" · Preview")
                            else if (track.duration > 0) append(" · ").append(formatTime(track.duration))
                            if (downloaded && !locked) append(" · Downloaded")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) trailing()
            IconButton(onClick = { actions.trackMenu(track, onRemove) }) {
                Icon(Icons.Rounded.MoreVert, "More options for ${track.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The animated equalizer bars next to the playing song */
@Composable
fun PlayingBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = listOf(0.9f, 0.5f, 0.75f).mapIndexed { i, start ->
        transition.animateFloat(
            initialValue = start,
            targetValue = 0.25f + (i * 0.2f) % 0.5f,
            animationSpec = infiniteRepeatable(tween(380 + i * 130), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            val value by h
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(if (playing) value else 0.3f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onMore: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onMore != null) Modifier.clickable(onClickLabel = "See all $title", role = Role.Button, onClick = onMore) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SectionTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onMore != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A square card for carousels: playlists, albums, stations, songs and artists */
@Composable
fun ItemCard(item: Item, modifier: Modifier = Modifier, width: Dp = 150.dp) {
    val actions = LocalActions.current
    val (image, title, subtitle, seed) = when (item) {
        is Item.TrackItem -> Quad(item.track.artworkUrl ?: item.track.user?.avatarUrl, item.track.title, item.track.artist, item.track.id)
        is Item.PlaylistItem -> Quad(item.playlist.cover, item.playlist.title, item.playlist.user?.username ?: item.playlist.kindLabel, item.playlist.id)
        is Item.SystemItem -> Quad(item.playlist.cover, item.playlist.shortTitle ?: item.playlist.title, item.playlist.shortDescription ?: item.playlist.description ?: "", item.playlist.urn.hashCode().toLong())
        is Item.UserItem -> Quad(item.user.avatarUrl, item.user.username, plural(item.user.followersCount, "follower"), item.user.id)
    }
    val person = item is Item.UserItem
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.medium)
            .clickable { actions.open(item) }
            .padding(6.dp)
    ) {
        Artwork(
            image,
            Modifier.fillMaxWidth().aspectRatio(1f),
            shape = if (person) CircleShape else MaterialTheme.shapes.medium,
            seed = seed,
            person = person,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = if (person) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (person) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (person) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class Quad(val image: String?, val title: String, val subtitle: String, val seed: Long)

@Composable
fun Carousel(items: List<Item>, modifier: Modifier = Modifier, width: Dp = 150.dp) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(app.quantm.android.ui.unique(items), key = { it.key }) { ItemCard(it, width = width) }
    }
}

@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    ListRow(
        title = playlist.title,
        subtitle = subtitle ?: listOfNotNull(playlist.kindLabel, playlist.user?.username, if (playlist.trackCount > 0) plural(playlist.trackCount.toLong(), "song") else null).joinToString(" · "),
        onClick = onClick,
        modifier = modifier,
        leading = { Artwork(playlist.cover, size = 56.dp, seed = playlist.id) },
    )
}

@Composable
fun UserRow(user: User, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    ListRow(
        title = user.username,
        subtitle = listOfNotNull(user.city?.takeIf { it.isNotBlank() }, plural(user.followersCount, "follower")).joinToString(" · "),
        onClick = onClick,
        modifier = modifier,
        verified = user.verified,
        leading = { Avatar(user.avatarUrl, 56.dp, seed = user.id) },
        trailing = trailing,
    )
}

@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Verified, "Verified", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        leading = {
            Box(
                Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp)) }
        },
        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

// ---- loading, empty and error states ------------------------------------------------

@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.05f, 0.11f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)))
}

@Composable
fun SkeletonRows(count: Int = 8) {
    Column {
        repeat(count) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Skeleton(Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    Skeleton(Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
        }
    }
}

@Composable
fun SkeletonCarousel() {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Column {
                Skeleton(Modifier.size(138.dp), MaterialTheme.shapes.medium)
                Spacer(Modifier.height(8.dp))
                Skeleton(Modifier.width(110.dp).height(12.dp))
            }
        }
    }
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageState(Icons.Rounded.CloudOff, "Couldn't load this", message, modifier, "Try again", onRetry)

/** Footer of a paged list: a spinner while loading, or a retry button */
@Composable
fun LoadMoreFooter(loading: Boolean, error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        when {
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) { Text("Try again") }
            }
            loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
