package app.quantm.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** SoundCloud artwork URLs end in -large.jpg (100px); ask for the size that's needed */
private fun sized(url: String?, size: String): String? =
    url?.replace(Regex("-(large|t\\d+x\\d+|crop|small|badge|tiny|mini|original)\\.(jpg|png)"), "-$size.$2")

fun artworkLarge(url: String?) = sized(url, "t500x500")
fun artworkMedium(url: String?) = sized(url, "t300x300")

/** A placeholder gradient that's always the same for the same song */
private val placeholders = listOf(
    listOf(Color(0xFF7C3AED), Color(0xFF06B6D4)),
    listOf(Color(0xFFFF5500), Color(0xFFFF2E97)),
    listOf(Color(0xFF3DDC84), Color(0xFF0A84FF)),
    listOf(Color(0xFFF2B705), Color(0xFFFF5500)),
    listOf(Color(0xFF5B8CFF), Color(0xFF7C3AED)),
    listOf(Color(0xFF0AC8B9), Color(0xFF3D9BFF)),
)

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    shape: Shape = androidx.compose.material3.MaterialTheme.shapes.small,
    seed: Long = 0,
    large: Boolean = false,
    person: Boolean = false,
) {
    val sizedUrl = remember(url, large) { if (large) artworkLarge(url) else artworkMedium(url) }
    val base = if (size != null) modifier.size(size) else modifier
    // the placeholder sits underneath and the image fades in over it (cheaper than subcomposition in lists)
    Box(base.clip(shape)) {
        Placeholder(seed, person)
        if (sizedUrl != null) {
            AsyncImage(
                model = sizedUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Placeholder(seed: Long, person: Boolean) {
    val colors = placeholders[(seed.mod(placeholders.size.toLong())).toInt()]
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(colors.map { it.copy(alpha = 0.55f) })),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (person) Icons.Rounded.Person else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier, seed: Long = 0) =
    Artwork(url, modifier, size = size, shape = CircleShape, seed = seed, person = true, large = size > 120.dp)
