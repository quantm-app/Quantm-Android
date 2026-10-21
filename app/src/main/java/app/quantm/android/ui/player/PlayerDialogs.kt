package app.quantm.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.Track
import app.quantm.android.graph
import app.quantm.android.playback.SleepState
import app.quantm.android.ui.components.Artwork
import app.quantm.android.ui.components.PlayingBars
import app.quantm.android.ui.components.formatLength
import app.quantm.android.ui.components.formatTime
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class QueueRow(val key: String, val track: Track, val queueIndex: Int)

/** Stable keys even when the same song is queued twice */
private fun rowsOf(queue: List<Track>, from: Int): List<QueueRow> {
    val seen = HashMap<Long, Int>()
    return queue.mapIndexedNotNull { i, t ->
        val n = seen.merge(t.id, 1, Int::plus)!!
        if (i < from) null else QueueRow("${t.id}#$n", t, i)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val graph = LocalContext.current.graph
    val state by graph.player.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = state.current
    var rows by remember { mutableStateOf(rowsOf(state.queue, state.index + 1)) }
    var dragging by remember { mutableStateOf(false) }
    // while dragging, keep the local order; catch up with the player as soon as the drag ends
    LaunchedEffect(state.queue, state.index, dragging) { if (!dragging) rows = rowsOf(state.queue, state.index + 1) }

    val listState = rememberLazyListState()
    // rows start after the two header items
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val a = rows.indexOfFirst { it.key == from.key }
        val b = rows.indexOfFirst { it.key == to.key }
        if (a < 0 || b < 0) return@rememberReorderableLazyListState
        val qa = rows[a].queueIndex
        val qb = rows[b].queueIndex
        rows = rows.toMutableList().apply { add(b, removeAt(a)) }.mapIndexed { i, r -> r.copy(queueIndex = state.index + 1 + i) }
        graph.player.move(qa, qb)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "now") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Now playing", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(10.dp))
                    if (current != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(current.artworkUrl ?: current.user?.avatarUrl, size = 52.dp, seed = current.id)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(current.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            PlayingBars(state.isPlaying, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        if (rows.isNotEmpty()) {
                            Text(
                                "${rows.size} songs · ${formatLength(rows.sumOf { it.track.duration })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (rows.isNotEmpty()) TextButton(onClick = { graph.player.clearUpNext() }) { Text("Clear") }
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing else is queued. Add songs with Play next or Add to queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            itemsIndexed(rows, key = { _, r -> r.key }) { _, row ->
                ReorderableItem(reorder, key = row.key) { isDragging ->
                    val dismiss = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) {
                                graph.player.removeAt(row.queueIndex)
                                true
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismiss,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = if (dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                            ) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                        },
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { graph.player.jump(row.queueIndex) }
                                .semantics {
                                    customActions = buildList {
                                        add(CustomAccessibilityAction("Remove") { graph.player.removeAt(row.queueIndex); true })
                                        if (row.queueIndex > state.index + 1) add(CustomAccessibilityAction("Move up") { graph.player.move(row.queueIndex, row.queueIndex - 1); true })
                                        if (row.queueIndex < state.queue.lastIndex) add(CustomAccessibilityAction("Move down") { graph.player.move(row.queueIndex, row.queueIndex + 1); true })
                                    }
                                }
                                .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(row.track.artworkUrl ?: row.track.user?.avatarUrl, size = 46.dp, seed = row.track.id)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    row.track.artist + if (row.track.duration > 0) " · " + formatTime(row.track.duration) else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .clearAndSetSemantics {}
                                    .draggableHandle(
                                        onDragStarted = {
                                            dragging = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = { dragging = false },
                                    ),
                            ) { Icon(Icons.Rounded.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepDialog(current: SleepState, onDismiss: () -> Unit) {
    val graph = LocalContext.current.graph
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text("Music fades out and pauses.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    DialogOption(if (minutes < 60) "$minutes minutes" else if (minutes == 60) "1 hour" else "1 hour 30 minutes") {
                        graph.sleepTimer.start(minutes)
                        onDismiss()
                    }
                }
                DialogOption("End of this song", selected = current == SleepState.EndOfTrack) {
                    graph.sleepTimer.endOfTrack()
                    onDismiss()
                }
            }
        },
        confirmButton = {
            if (current != SleepState.Off) TextButton(onClick = { graph.sleepTimer.cancel(); onDismiss() }) { Text("Turn off") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DialogOption(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedDialog(current: Float, onDismiss: () -> Unit) {
    val graph = LocalContext.current.graph
    var speed by remember { mutableFloatStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                Text("${trimSpeed(speed)}×", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = speed,
                    onValueChange = {
                        speed = (Math.round(it * 20) / 20f)
                        graph.player.setSpeed(speed)
                    },
                    valueRange = 0.5f..2f,
                    steps = 29,
                    modifier = Modifier.semantics { contentDescription = "Playback speed" },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.1f, 1.25f, 1.5f, 2f).forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = {
                                speed = s
                                graph.player.setSpeed(s)
                            },
                            label = { Text("${trimSpeed(s)}×") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
