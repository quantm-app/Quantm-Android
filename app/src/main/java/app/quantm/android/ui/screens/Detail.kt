package app.quantm.android.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.quantm.android.ui.components.Artwork
import app.quantm.android.ui.components.BackButton
import app.quantm.android.ui.components.LocalBottomPadding
import app.quantm.android.ui.player.rememberArtworkColors

/**
 * Song, artist and playlist pages: a big header tinted with the artwork's colors, and a top
 * bar whose title fades in once the header scrolls away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    tintImage: String?,
    listState: LazyListState = rememberLazyListState(),
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val bottom = LocalBottomPadding.current
    val colors = rememberArtworkColors(tintImage)
    val tint = colors?.accent ?: MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420 } }

    Box(Modifier.fillMaxSize().background(bg)) {
        // tint behind the header
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .graphicsLayer {
                    val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
                    translationY = -offset * 0.5f
                    alpha = (1f - offset / 900f).coerceIn(0f, 1f)
                }
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.42f), bg)))
        )
        val list: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottom + 24.dp),
            ) {
                item(key = "topSpace") { Spacer(Modifier.statusBarsPadding().height(56.dp)) }
                content()
            }
        }
        if (onRefresh != null) {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { list() }
        } else list()

        TopAppBar(
            title = {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = if (scrolled) 1f else 0f },
                )
            },
            navigationIcon = { BackButton() },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (scrolled) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            ),
        )
    }
}

/** Centered artwork, title and subtitle */
@Composable
fun Hero(
    image: String?,
    seed: Long,
    title: String,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    size: Dp = 232.dp,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(
            image,
            size = size,
            shape = if (circle) CircleShape else MaterialTheme.shapes.large,
            seed = seed,
            large = true,
            person = circle,
        )
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        below()
    }
}

/** Description text that expands when tapped */
@Composable
fun ExpandableText(text: String, modifier: Modifier = Modifier, collapsedLines: Int = 3) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text.trim(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .animateContentSize()
            .clickable(onClickLabel = if (expanded) "Show less" else "Show more", role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun StatRow(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Text(
        stats.joinToString(" · ") { (value, label) -> "$value $label".trim() },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(top = 8.dp),
    )
}
