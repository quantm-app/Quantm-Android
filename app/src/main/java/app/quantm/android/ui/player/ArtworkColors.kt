package app.quantm.android.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.quantm.android.ui.components.artworkMedium
import app.quantm.android.ui.theme.QuantmColors
import app.quantm.android.ui.theme.colorsFromArtwork
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = object : LinkedHashMap<String, QuantmColors>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuantmColors>?) = size > 64
}

/** Colors picked from a song's artwork (Now Playing background, Album Match theme) */
@Composable
fun rememberArtworkColors(url: String?): QuantmColors? {
    val context = LocalContext.current
    var colors by remember(url) { mutableStateOf(url?.let { synchronized(cache) { cache[it] } }) }
    LaunchedEffect(url) {
        if (url == null || colors != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(artworkMedium(url))
            .allowHardware(false)
            .size(160)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        val picked = withContext(Dispatchers.Default) { runCatching { colorsFromArtwork(result.image.toBitmap()) }.getOrNull() }
        if (picked != null) {
            synchronized(cache) { cache[url] = picked }
            colors = picked
        }
    }
    return colors
}
