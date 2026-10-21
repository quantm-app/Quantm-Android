package app.quantm.android.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SleepState {
    data object Off : SleepState
    /** Pauses (with a fade) at this wall-clock time */
    data class At(val endsAt: Long) : SleepState
    /** Pauses when the current song ends */
    data object EndOfTrack : SleepState
}

/** Set from the UI, carried out by the playback service, so it works with the screen off */
class SleepTimer {
    private val _state = MutableStateFlow<SleepState>(SleepState.Off)
    val state: StateFlow<SleepState> = _state.asStateFlow()

    fun start(minutes: Int) { _state.value = SleepState.At(System.currentTimeMillis() + minutes * 60_000L) }
    fun endOfTrack() { _state.value = SleepState.EndOfTrack }
    fun cancel() { _state.value = SleepState.Off }
}
