package app.quantm.android.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import app.quantm.android.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class EqInfo(
    /** Center frequency of each band in Hz */
    val bands: List<Int>,
    /** Allowed level range in millibels */
    val min: Int,
    val max: Int,
)

/** The equalizer presets, as levels in dB for 5 bands (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz) */
val EQ_PRESETS: Map<String, List<Float>> = linkedMapOf(
    "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
    "Bass boost" to listOf(6f, 4f, 0f, 0f, 0f),
    "Bass cut" to listOf(-6f, -3f, 0f, 0f, 0f),
    "Treble boost" to listOf(0f, 0f, 0f, 3f, 6f),
    "Vocal" to listOf(-2f, 0f, 4f, 3f, 0f),
    "Electronic" to listOf(5f, 3f, -1f, 2f, 4f),
    "Hip-hop" to listOf(5f, 3f, 0f, 1f, 2f),
    "Rock" to listOf(4f, 2f, -2f, 2f, 4f),
    "Acoustic" to listOf(3f, 1f, 1f, 2f, 3f),
    "Late night" to listOf(-3f, 0f, 2f, 0f, -3f),
)

/**
 * Equalizer and bass boost on the player's audio session. Settings live in [Settings], so the
 * EQ screen works while nothing is playing and changes apply live while something is.
 */
class AudioFx(private val settings: Settings, private val scope: CoroutineScope) {
    private val _info = MutableStateFlow(EqInfo(listOf(60, 230, 910, 3600, 14000), -1500, 1500))
    val info: StateFlow<EqInfo> = _info.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var job: Job? = null

    fun attach(sessionId: Int) {
        release()
        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()?.also { eq ->
            val range = eq.bandLevelRange
            _info.value = EqInfo(
                bands = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                min = range[0].toInt(),
                max = range[1].toInt(),
            )
        }
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        job = scope.launch {
            combine(settings.eqEnabled.flow, settings.eqLevels.flow, settings.bassBoost.flow) { on, levels, boost -> Triple(on, levels, boost) }
                .collect { (on, levels, boost) -> apply(on, levels, boost) }
        }
    }

    private fun apply(on: Boolean, levels: String, boost: Int) {
        val eq = equalizer
        val info = _info.value
        runCatching {
            if (eq != null) {
                val parsed = levelsFor(levels, info)
                parsed.forEachIndexed { band, level -> eq.setBandLevel(band.toShort(), level.toShort()) }
                eq.enabled = on
            }
            bass?.let { b ->
                if (b.strengthSupported) b.setStrength((boost.coerceIn(0, 1000)).toShort())
                b.enabled = on && boost > 0
            }
        }
    }

    fun release() {
        job?.cancel()
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        equalizer = null
        bass = null
    }

    companion object {
        /** Stored levels (mB) for each band, filling gaps with 0 */
        fun levelsFor(stored: String, info: EqInfo): List<Int> {
            val values = stored.split(',').mapNotNull { it.trim().toIntOrNull() }
            return info.bands.indices.map { values.getOrElse(it) { 0 }.coerceIn(info.min, info.max) }
        }

        /** Preset levels mapped onto however many bands this phone's equalizer has */
        fun presetLevels(name: String, info: EqInfo): List<Int> {
            val preset = EQ_PRESETS[name] ?: return info.bands.map { 0 }
            val reference = listOf(60, 230, 910, 3600, 14000)
            return info.bands.map { freq ->
                val nearest = reference.indices.minBy { kotlin.math.abs(kotlin.math.ln(reference[it].toDouble() / freq)) }
                (preset[nearest] * 100).toInt().coerceIn(info.min, info.max)
            }
        }
    }
}
