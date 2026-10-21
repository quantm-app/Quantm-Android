package app.quantm.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * SoundCloud-style waveform that doubles as the seek bar. Drag or tap to seek; [onScrub] reports
 * the position while dragging so the time label can follow the finger.
 */
@Composable
fun Waveform(
    samples: FloatArray?,
    progress: Float,
    played: Color,
    unplayed: Color,
    modifier: Modifier = Modifier,
    durationMs: Long,
    positionText: String,
    buffered: Float = 0f,
    onScrub: (Float?) -> Unit = {},
    onSeek: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val haptics = LocalHapticFeedback.current
    // the pointer blocks below never restart, so they must read the latest callbacks, not the first ones
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    val current by rememberUpdatedState(progress)
    val skip = if (durationMs > 0) 10_000f / durationMs else 0f
    val total = formatTime(durationMs)
    Canvas(
        modifier
            .semantics {
                contentDescription = "Seek bar"
                stateDescription = "$positionText of $total"
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                setProgress { seek(it.coerceIn(0f, 1f)); true }
                customActions = listOf(
                    CustomAccessibilityAction("Back 10 seconds") { seek((current - skip).coerceIn(0f, 1f)); true },
                    CustomAccessibilityAction("Forward 10 seconds") { seek((current + skip).coerceIn(0f, 1f)); true },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    seek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = (offset.x / size.width).coerceIn(0f, 1f)
                        scrub(dragging)
                    },
                    onDragEnd = {
                        dragging?.let(seek)
                        dragging = null
                        scrub(null)
                    },
                    onDragCancel = {
                        dragging = null
                        scrub(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                        scrub(dragging)
                    },
                )
            }
    ) {
        val p = dragging ?: progress
        val bar = 2.dp.toPx()
        val gap = 1.5.dp.toPx()
        val count = max(1, ((size.width + gap) / (bar + gap)).toInt())
        val mid = size.height * 0.62f
        for (i in 0 until count) {
            val f = i / count.toFloat()
            val raw = if (samples == null || samples.isEmpty()) {
                // gentle placeholder shape while the real waveform loads
                0.35f + 0.25f * kotlin.math.sin(i * 0.37f).toFloat() * kotlin.math.cos(i * 0.11f).toFloat()
            } else {
                // average the samples under this bar
                val from = (f * samples.size).toInt()
                val to = (((i + 1) / count.toFloat()) * samples.size).toInt().coerceAtLeast(from + 1).coerceAtMost(samples.size)
                var sum = 0f
                for (s in from until to) sum += samples[s]
                sum / (to - from)
            }
            val h = (raw.coerceIn(0.06f, 1f)) * mid
            val x = i * (bar + gap)
            val color = when {
                f < p -> played
                f < buffered -> unplayed.copy(alpha = unplayed.alpha * 1.4f)
                else -> unplayed
            }
            drawRoundRect(color, Offset(x, mid - h), Size(bar, h), CornerRadius(bar / 2))
            // the reflection under the line, like SoundCloud's
            drawRoundRect(color.copy(alpha = color.alpha * 0.35f), Offset(x, mid + 1.dp.toPx()), Size(bar, h * 0.45f), CornerRadius(bar / 2))
        }
    }
}
