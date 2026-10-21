package app.quantm.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.Paged

/** Room the tabs and the mini player take at the bottom of the screen */
val LocalBottomPadding = compositionLocalOf { 0.dp }

/** Loads the next page when the end of the list comes into view */
fun <T> LazyListScope.pagedFooter(paged: Paged<T>) {
    if (!paged.done || paged.error != null) {
        item(key = "footer", contentType = "footer") {
            LaunchedEffect(paged.items.size) { if (paged.error == null) paged.loadMore() }
            LoadMoreFooter(paged.loading, paged.error, paged::retry)
        }
    }
}

@Composable
fun BackButton() {
    val actions = LocalActions.current
    IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
}

/**
 * A screen with a large title that shrinks into the top bar as the list scrolls, and pull to
 * refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    title: String,
    modifier: Modifier = Modifier,
    back: Boolean = true,
    large: Boolean = true,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomPadding: Dp = LocalBottomPadding.current,
    content: LazyListScope.() -> Unit,
) {
    val behavior = if (large) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            val nav: @Composable () -> Unit = { if (back) BackButton() }
            if (large) {
                LargeTopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = nav,
                    actions = actions,
                    scrollBehavior = behavior,
                    colors = colors,
                )
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = nav,
                    actions = actions,
                    scrollBehavior = behavior,
                    colors = colors,
                )
            }
        },
    ) { padding ->
        val list: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                content = content,
            )
        }
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (onRefresh != null) {
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { list() }
            } else list()
        }
    }
}
