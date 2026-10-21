package app.quantm.android.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.Item
import app.quantm.android.data.friendly
import app.quantm.android.graph
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.components.Artwork
import app.quantm.android.ui.components.ListRow
import app.quantm.android.ui.components.LocalBottomPadding
import app.quantm.android.ui.components.MessageState
import app.quantm.android.ui.components.PlaylistRow
import app.quantm.android.ui.components.SectionHeader
import app.quantm.android.ui.components.SkeletonRows
import app.quantm.android.ui.components.TrackRow
import app.quantm.android.ui.components.UserRow
import app.quantm.android.ui.components.pagedFooter
import app.quantm.android.ui.rememberPaged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FILTERS = listOf("" to "All", "tracks" to "Songs", "users" to "Artists", "playlists_without_albums" to "Playlists", "albums" to "Albums")

private val GENRES = listOf(
    "Hip-hop & Rap", "Electronic", "House", "Techno", "Drum & Bass", "Dubstep", "Pop", "R&B & Soul",
    "Indie", "Rock", "Ambient", "Lo-fi", "Trap", "Phonk", "Deep House", "Jazz", "Classical", "Latin", "Afrobeats", "Country",
)

fun isSoundCloudLink(text: String) = Regex("""^https?://(m\.|on\.|www\.)?soundcloud\.com/\S+""").matches(text.trim())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen() {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val bottom = LocalBottomPadding.current
    val history by graph.settings.searchHistory.flow.collectAsStateWithLifecycle()

    var text by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var resolving by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        if (text.isBlank() || text == submitted || isSoundCloudLink(text)) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = runCatching { graph.api.suggestions(text) }.getOrDefault(emptyList())
    }

    fun submit(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        keyboard?.hide()
        suggestions = emptyList()
        if (isSoundCloudLink(q)) {
            resolving = true
            scope.launch {
                runCatching { graph.api.resolve(q) }
                    .onSuccess { item -> if (item != null) actions.open(item) else graph.messages.show("That link isn't a song, artist or playlist.") }
                    .onFailure { graph.messages.show(it.friendly()) }
                resolving = false
            }
            return
        }
        text = q
        submitted = q
        graph.settings.addSearch(q)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.padding(top = 12.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Songs, artists, playlists or a link") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = ""; submitted = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    } else {
                        IconButton(onClick = {
                            val clip = clipboard.getText()?.text.orEmpty()
                            if (isSoundCloudLink(clip)) submit(clip) else graph.messages.show("Copy a SoundCloud link first, then tap paste.")
                        }) { Icon(Icons.Rounded.ContentPaste, "Paste a SoundCloud link") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search songs, artists, playlists or a link" },
            )
        }

        when {
            resolving -> SkeletonRows(4)
            suggestions.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottom)) {
                items(suggestions, key = { it }) { s ->
                    ListRow(
                        title = s,
                        subtitle = null,
                        onClick = { submit(s) },
                        leading = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
            }
            submitted.isNotEmpty() -> {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FILTERS) { (kind, label) ->
                        FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(label) })
                    }
                }
                Results(submitted, filter)
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
                val recent = history.split('\n').filter { it.isNotBlank() }
                if (recent.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Box(Modifier.weight(1f)) { SectionHeader("Recent searches") }
                            TextButton(onClick = { graph.settings.searchHistory.value = "" }) { Text("Clear") }
                        }
                    }
                    items(recent, key = { "h$it" }) { q ->
                        ListRow(
                            title = q,
                            subtitle = null,
                            onClick = { submit(q) },
                            leading = { Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailing = {
                                IconButton(onClick = { graph.settings.removeSearch(q) }) { Icon(Icons.Rounded.Close, "Remove $q from recent searches", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            },
                        )
                    }
                }
                item { SectionHeader("Browse genres") }
                item {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GENRES.forEach { g -> SuggestionChip(onClick = { submit(g) }, label = { Text(g) }) }
                    }
                }
                item {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Tip: paste a SoundCloud link to open it here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Results(query: String, kind: String) {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val bottom = LocalBottomPadding.current
    val results = rememberPaged("search:$kind:$query") { graph.api.search(query, kind) }
    val tracks = results.items.mapNotNull { (it as? Item.TrackItem)?.track }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        if (results.initial) item { SkeletonRows(8) }
        if (results.done && results.items.isEmpty()) item {
            MessageState(Icons.Rounded.SearchOff, "No results for \"$query\"", "Check the spelling or try fewer words.")
        }
        itemsIndexed(
            results.items,
            key = { _, item -> item.key },
            contentType = { _, item -> when (item) { is Item.TrackItem -> "track"; is Item.UserItem -> "user"; else -> "playlist" } },
        ) { _, item ->
            when (item) {
                is Item.TrackItem -> TrackRow(item.track, onClick = {
                    graph.player.play(tracks, tracks.indexOfFirst { it.id == item.track.id }, context = "Search: $query")
                })
                is Item.UserItem -> UserRow(item.user, onClick = { actions.open(item) })
                is Item.PlaylistItem -> PlaylistRow(item.playlist, onClick = { actions.open(item) })
                is Item.SystemItem -> ListRow(
                    item.playlist.title,
                    item.playlist.description,
                    onClick = { actions.open(item) },
                    leading = { Artwork(item.playlist.cover, size = 56.dp, seed = item.playlist.urn.hashCode().toLong()) },
                )
            }
        }
        pagedFooter(results)
    }
}
