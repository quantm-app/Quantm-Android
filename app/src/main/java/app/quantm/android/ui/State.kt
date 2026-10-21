package app.quantm.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quantm.android.data.Pager
import app.quantm.android.data.friendly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps a screen's data while it's in the back stack, so going back shows it instantly at the
 * same scroll position instead of loading it again.
 */
class ScreenStore : ViewModel() {
    val values = HashMap<String, Any>()
}

@Composable
fun <T : Any> retained(key: String, create: (CoroutineScope) -> T): T {
    val store = viewModel<ScreenStore>()
    @Suppress("UNCHECKED_CAST")
    return store.values.getOrPut(key) { create(store.viewModelScope) } as T
}

/** A list that loads page by page */
@Stable
class Paged<T>(private val scope: CoroutineScope, private val factory: () -> Pager<T>) {
    private var pager = factory()
    private var job: Job? = null

    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(false)
        private set
    /** True until the first page arrives */
    val initial: Boolean get() = items.isEmpty() && !done && error == null

    init { loadMore() }

    fun loadMore() {
        if (loading || done) return
        loading = true
        error = null
        job = scope.launch {
            runCatching { pager.next() }
                .onSuccess { page ->
                    items = items + page
                    done = pager.done
                }
                .onFailure { error = it.friendly() }
            loading = false
            refreshing = false
        }
    }

    fun refresh() {
        job?.cancel()
        pager = factory()
        refreshing = items.isNotEmpty()
        loading = false
        done = false
        error = null
        val keep = items
        loading = true
        job = scope.launch {
            runCatching { pager.next() }
                .onSuccess { page ->
                    items = page
                    done = pager.done
                }
                .onFailure {
                    items = keep
                    error = it.friendly()
                }
            loading = false
            refreshing = false
        }
    }

    fun retry() {
        error = null
        if (items.isEmpty()) refresh() else loadMore()
    }

    /** Edit the loaded items in place (removing a song from a list, say) */
    fun update(change: (List<T>) -> List<T>) { items = change(items) }
}

/** One value that loads once (and on refresh) */
@Stable
class Loadable<T>(private val scope: CoroutineScope, private val load: suspend () -> T) {
    var value by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set

    init { reload() }

    fun reload() {
        if (loading) return
        loading = true
        refreshing = value != null
        error = null
        scope.launch {
            runCatching { load() }
                .onSuccess { value = it }
                .onFailure { error = it.friendly() }
            loading = false
            refreshing = false
        }
    }
}

@Composable
fun <T> rememberPaged(key: String, factory: () -> Pager<T>): Paged<T> = retained("paged:$key") { Paged(it, factory) }

@Composable
fun <T> rememberLoadable(key: String, load: suspend () -> T): Loadable<T> = retained("load:$key") { Loadable(it, load) }
