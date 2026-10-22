package app.quantm.android.ui.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.quantm.android.data.Track
import app.quantm.android.graph
import app.quantm.android.playback.PlayerState
import app.quantm.android.playback.SleepState
import app.quantm.android.ui.EqualizerRoute
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.components.Artwork
import app.quantm.android.ui.components.Waveform
import app.quantm.android.ui.components.formatTime
import app.quantm.android.ui.theme.Quantm
import app.quantm.android.ui.theme.lerpColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val MiniPlayerHeight = 64.dp

/**
 * The player: a mini player above the tabs that expands into Now Playing. Drag it up or down,
 * tap it, or use the back gesture to close it.
 */
@Composable
fun PlayerSheet(progress: Animatable<Float, AnimationVector1D>, bottomInset: Dp, modifier: Modifier = Modifier) {
    val graph = LocalContext.current.graph
    val state by graph.player.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val track = state.current ?: return
    val p = progress.value

    fun settle(target: Float) = scope.launch {
        progress.animateTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }

    PredictiveBackHandler(enabled = p > 0.5f) { events ->
        try {
            events.collect { e -> progress.snapTo(1f - e.progress * 0.2f) }
            settle(0f)
        } catch (e: CancellationException) {
            settle(1f)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val full = constraints.maxHeight.toFloat()
        val miniPx = with(density) { (MiniPlayerHeight + 8.dp + bottomInset).toPx() }
        val collapsedTop = full - miniPx
        val top = collapsedTop * (1f - p)
        val dragState = rememberDraggableState { delta ->
            scope.launch { progress.snapTo((progress.value - delta / collapsedTop).coerceIn(0f, 1f)) }
        }
        val drag = Modifier.draggable(
            dragState,
            Orientation.Vertical,
            onDragStopped = { velocity ->
                val target = when {
                    velocity < -900f -> 1f
                    velocity > 900f -> 0f
                    progress.value > 0.5f -> 1f
                    else -> 0f
                }
                settle(target)
            },
        )

        Box(
            Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { full.toDp() })
        ) {
            if (p > 0.1f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((p - 0.1f) / 0.6f).coerceIn(0f, 1f) }
                        .then(drag)
                ) {
                    NowPlaying(state, track, onCollapse = { settle(0f) })
                }
            }
            if (p < 0.5f) {
                MiniPlayer(
                    state,
                    track,
                    onOpen = { settle(1f) },
                    modifier = Modifier
                        .graphicsLayer { alpha = (1f - p * 2.5f).coerceIn(0f, 1f) }
                        .then(drag),
                )
            }
        }
    }
}

@Composable
private fun rememberPosition(): Long {
    val graph = LocalContext.current.graph
    val flow = remember { graph.player.position() }
    val position by flow.collectAsStateWithLifecycle(0L)
    return position
}

@Composable
fun MiniPlayer(state: PlayerState, track: Track, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalContext.current.graph
    val haptics = LocalHapticFeedback.current
    val position = rememberPosition()
    val duration = state.durationMs.coerceAtLeast(1)
    var swipe by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(swipe, label = "swipe")
    // big system font sizes would overflow the fixed-height bar
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, 1.3f))) {
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(MiniPlayerHeight),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Box {
            Row(
                Modifier
                    .fillMaxSize()
                    .clickable(onClickLabel = "Open player", onClick = onOpen)
                    .semantics { customActions = listOf(CustomAccessibilityAction("Previous song") { graph.player.previous(); true }) }
                    .pointerInput(Unit) {
                        // swipe sideways for the next or previous song
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(swipe) > size.width * 0.25f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (swipe < 0) graph.player.next() else graph.player.previous()
                                }
                                swipe = 0f
                            },
                            onDragCancel = { swipe = 0f },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                swipe += delta
                            },
                        )
                    }
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).graphicsLayer {
                        translationX = offset * 0.6f
                        alpha = 1f - (abs(offset) / 600f).coerceIn(0f, 0.7f)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(track.artworkUrl ?: track.user?.avatarUrl, size = 46.dp, seed = track.id)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PlayPauseIcon(state, onClick = { graph.player.toggle() })
                IconButton(onClick = { graph.player.next() }, enabled = state.index < state.queue.lastIndex || state.repeatMode == Player.REPEAT_MODE_ALL) {
                    Icon(Icons.Rounded.SkipNext, "Next song", Modifier.size(28.dp))
                }
            }
            // progress line along the bottom edge
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
    }
}

@Composable
private fun PlayPauseIcon(state: PlayerState, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (state.buffering && state.isPlaying) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
        } else {
            AnimatedContent(state.isPlaying, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pp") { playing ->
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlaying(state: PlayerState, track: Track, onCollapse: () -> Unit) {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val haptics = LocalHapticFeedback.current
    val q = Quantm.colors
    val art = rememberArtworkColors(track.artworkUrl ?: track.user?.avatarUrl)
    val tint = art?.bg ?: lerpColor(q.accent, Color.Black, 0.75f)
    val liked by graph.social.likedTracks.collectAsStateWithLifecycle()
    val sleep by graph.sleepTimer.state.collectAsStateWithLifecycle()
    val position = rememberPosition()
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    val samples by produceState<FloatArray?>(null, track.id) {
        value = null // don't show the previous song's waveform while this one loads
        value = graph.api.waveform(graph.api.cached(track.id) ?: track)
    }
    val duration = state.durationMs.coerceAtLeast(1)
    val shown = scrub?.let { (it * duration).toLong() } ?: position

    // Now Playing is always dark, light themes included, so text stays readable
    // a rich wash of the artwork's main color at the top, fading to near black
    val bgTop = if (art != null) lerpColor(lerpColor(art.accent, art.surface, 0.35f), Color.Black, 0.5f) else lerpColor(q.accent, Color.Black, 0.6f)
    val content = Color(0xFFF5F5F5)
    val muted = content.copy(alpha = 0.65f)
    val accent = art?.accent ?: q.accent

    Box(
        Modifier
            .fillMaxSize()
            .semantics { paneTitle = "Now playing" }
            .background(Brush.verticalGradient(listOf(bgTop, lerpColor(tint, Color.Black, 0.7f))))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val landscape = maxWidth > maxHeight
            val header: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Minimize player", tint = content, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = muted, letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f)
                        Text(state.context ?: "Your queue", style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { actions.trackMenu(track) }) { Icon(Icons.Rounded.MoreVert, "More", tint = content) }
                }
            }
            val artwork: @Composable (Modifier) -> Unit = { mod ->
                var swipe by remember(track.id) { mutableFloatStateOf(0f) }
                val offset by animateFloatAsState(swipe, label = "artSwipe")
                Box(
                    mod
                        .aspectRatio(1f)
                        .graphicsLayer {
                            translationX = offset
                            rotationZ = offset / 60f
                        }
                        .pointerInput(track.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (abs(swipe) > size.width * 0.28f) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (swipe < 0) graph.player.next() else graph.player.previous()
                                    }
                                    swipe = 0f
                                },
                                onDragCancel = { swipe = 0f },
                                onHorizontalDrag = { change, delta ->
                                    change.consume()
                                    swipe += delta
                                },
                            )
                        }
                        .shadow(24.dp, MaterialTheme.shapes.large, spotColor = Color.Black)
                ) {
                    AnimatedContent(track.id, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "art") {
                        Artwork(track.artworkUrl ?: track.user?.avatarUrl, Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, seed = track.id, large = true)
                    }
                }
            }
            val controls: @Composable () -> Unit = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = content,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable(role = Role.Button) {
                                        onCollapse()
                                        actions.openArtist(track.user)
                                    },
                            )
                        }
                        val isLiked = track.id in liked
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            graph.social.toggleLike(graph.api.cached(track.id) ?: track)
                        }) {
                            AnimatedContent(isLiked, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "like") { on ->
                                Icon(
                                    if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    if (on) "Remove from likes" else "Like",
                                    // the theme accent, so a like stands out even on black-and-white artwork
                                    tint = if (on) q.accent else content,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Waveform(
                        samples = samples,
                        progress = (position.toFloat() / duration).coerceIn(0f, 1f),
                        played = accent,
                        unplayed = content.copy(alpha = 0.28f),
                        buffered = (graph.player.bufferedPosition().toFloat() / duration).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        durationMs = duration,
                        positionText = formatTime(shown),
                        onScrub = { scrub = it },
                        onSeek = { graph.player.seekTo((it * duration).toLong()) },
                    )
                    // the seek bar already announces the time
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {}) {
                        Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = if (scrub != null) accent else muted)
                        Spacer(Modifier.weight(1f))
                        Text("-" + formatTime(duration - shown), style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { graph.player.toggleShuffle() },
                            modifier = Modifier.semantics { stateDescription = if (state.shuffle) "On" else "Off" },
                        ) {
                            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) accent else muted)
                        }
                        IconButton(onClick = { graph.player.previous() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = content, modifier = Modifier.size(40.dp))
                        }
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                graph.player.toggle()
                            },
                            shape = CircleShape,
                            color = content,
                            modifier = Modifier.size(76.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.buffering && state.isPlaying) {
                                    CircularProgressIndicator(Modifier.size(32.dp), color = tint, strokeWidth = 3.dp)
                                } else {
                                    AnimatedContent(state.isPlaying, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "big") { playing ->
                                        Icon(
                                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            if (playing) "Pause" else "Play",
                                            tint = lerpColor(tint, Color.Black, 0.3f),
                                            modifier = Modifier.size(42.dp),
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { graph.player.next() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipNext, "Next", tint = content, modifier = Modifier.size(40.dp))
                        }
                        IconButton(onClick = { graph.player.cycleRepeat() }) {
                            Icon(
                                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                when (state.repeatMode) {
                                    Player.REPEAT_MODE_ONE -> "Repeat this song"
                                    Player.REPEAT_MODE_ALL -> "Repeat the queue"
                                    else -> "Repeat off"
                                },
                                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else muted,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        val speedLabel = if (state.speed == 1f) "1×" else "${trimSpeed(state.speed)}×"
                        PillButton(
                            icon = Icons.Rounded.Speed,
                            label = speedLabel,
                            active = state.speed != 1f,
                            content = content,
                            accent = accent,
                            modifier = Modifier.semantics { contentDescription = "Playback speed $speedLabel" },
                        ) { showSpeed = true }
                        PillButton(
                            icon = Icons.Rounded.Bedtime,
                            label = sleepLabel(sleep),
                            active = sleep != SleepState.Off,
                            content = content,
                            accent = accent,
                        ) { showSleep = true }
                        PillButton(icon = Icons.Rounded.Tune, label = "Equalizer", active = false, content = content, accent = accent) {
                            onCollapse()
                            actions.navigate(EqualizerRoute)
                        }
                        PillButton(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            label = "Queue",
                            active = false,
                            content = content,
                            accent = accent,
                        ) { showQueue = true }
                    }
                }
            }

            if (landscape) {
                Column(Modifier.fillMaxSize()) {
                    header()
                    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        artwork(Modifier.fillMaxHeight(0.92f))
                        Spacer(Modifier.width(32.dp))
                        Box(Modifier.weight(1f)) { controls() }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    header()
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                        artwork(Modifier.widthIn(max = 520.dp).fillMaxWidth())
                    }
                    Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp)) { controls() }
                }
            }
        }
    }

    if (showQueue) QueueSheet(onDismiss = { showQueue = false })
    if (showSleep) SleepDialog(sleep, onDismiss = { showSleep = false })
    if (showSpeed) SpeedDialog(state.speed, onDismiss = { showSpeed = false })
}

@Composable
private fun PillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    content: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (active) accent.copy(alpha = 0.22f) else content.copy(alpha = 0.08f),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) accent else content.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) accent else content.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        }
    }
}

fun trimSpeed(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else String.format(java.util.Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')

@Composable
private fun sleepLabel(sleep: SleepState): String {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep) {
        while (sleep is SleepState.At) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return when (sleep) {
        SleepState.Off -> "Sleep"
        SleepState.EndOfTrack -> "End of song"
        is SleepState.At -> formatTime((sleep.endsAt - now).coerceAtLeast(0))
    }
}
