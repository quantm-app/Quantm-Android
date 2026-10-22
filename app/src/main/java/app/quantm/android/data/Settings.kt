package app.quantm.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App settings. SharedPreferences is read synchronously at startup, so the first frame
 * already has the right theme (no flash of the default colors).
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val theme = Pref(prefs, "theme", "SoundCloud Dark")
    /** Material You: colors from the phone's wallpaper (Android 12+) */
    val dynamicColor = Pref(prefs, "dynamicColor", false)
    /** The whole app takes its colors from the playing song's artwork */
    val albumColors = Pref(prefs, "albumColors", false)
    val skipSilence = Pref(prefs, "skipSilence", false)
    val checkUpdates = Pref(prefs, "checkUpdates", true)
    val eqEnabled = Pref(prefs, "eqEnabled", false)
    /** Band levels in millibels, comma separated */
    val eqLevels = Pref(prefs, "eqLevels", "")
    val eqPreset = Pref(prefs, "eqPreset", "Flat")
    val bassBoost = Pref(prefs, "bassBoost", 0)
    val searchHistory = Pref(prefs, "searchHistory", "")
    val homeTab = Pref(prefs, "homeTab", 0)
    val skippedUpdate = Pref(prefs, "skippedUpdate", "")
    /** The version that last ran, to say "Updated to X" once after an update */
    val lastVersion = Pref(prefs, "lastVersion", "")

    fun recentSearches(): List<String> = searchHistory.value.split('\n').filter { it.isNotBlank() }

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchHistory.value = (listOf(q) + recentSearches().filter { !it.equals(q, ignoreCase = true) }).take(12).joinToString("\n")
    }

    fun removeSearch(query: String) {
        searchHistory.value = recentSearches().filter { it != query }.joinToString("\n")
    }
}

class Pref<T>(private val prefs: SharedPreferences, private val key: String, default: T) {
    private val state = MutableStateFlow(read(default))
    val flow: StateFlow<T> = state.asStateFlow()

    var value: T
        get() = state.value
        set(v) {
            state.value = v
            prefs.edit().apply {
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is Boolean -> putBoolean(key, v)
                    is Int -> putInt(key, v)
                    is Long -> putLong(key, v)
                    is Float -> putFloat(key, v)
                    is String -> putString(key, v)
                    else -> error("Unsupported pref type")
                }
            }.apply()
        }

    @Suppress("UNCHECKED_CAST")
    private fun read(default: T): T = when (default) {
        is Boolean -> prefs.getBoolean(key, default) as T
        is Int -> prefs.getInt(key, default) as T
        is Long -> prefs.getLong(key, default) as T
        is Float -> prefs.getFloat(key, default) as T
        is String -> (prefs.getString(key, default) ?: default) as T
        else -> default
    }
}
