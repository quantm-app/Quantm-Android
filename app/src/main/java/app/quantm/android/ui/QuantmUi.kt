package app.quantm.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.quantm.android.data.Track
import app.quantm.android.data.UpdateState
import app.quantm.android.data.friendly
import app.quantm.android.graph
import app.quantm.android.ui.components.AddToPlaylistSheet
import app.quantm.android.ui.components.LocalBottomPadding
import app.quantm.android.ui.components.LocalDownloaded
import app.quantm.android.ui.components.LocalNowPlaying
import app.quantm.android.ui.components.NowPlaying
import app.quantm.android.ui.components.TrackMenuSheet
import app.quantm.android.ui.player.MiniPlayerHeight
import app.quantm.android.ui.player.PlayerSheet
import app.quantm.android.ui.player.rememberArtworkColors
import app.quantm.android.ui.screens.ArtistScreen
import app.quantm.android.ui.screens.DownloadsScreen
import app.quantm.android.ui.screens.EqualizerScreen
import app.quantm.android.ui.screens.HistoryScreen
import app.quantm.android.ui.screens.HomeScreen
import app.quantm.android.ui.screens.LibraryScreen
import app.quantm.android.ui.screens.LikesScreen
import app.quantm.android.ui.screens.LocalPlaylistScreen
import app.quantm.android.ui.screens.LocalPlaylistsScreen
import app.quantm.android.ui.screens.PlaylistScreen
import app.quantm.android.ui.screens.PlaylistsScreen
import app.quantm.android.ui.screens.SearchScreen
import app.quantm.android.ui.screens.SettingsScreen
import app.quantm.android.ui.screens.SignInScreen
import app.quantm.android.ui.screens.StationScreen
import app.quantm.android.ui.screens.ThemesScreen
import app.quantm.android.ui.screens.TrackScreen
import app.quantm.android.ui.screens.UsersScreen
import app.quantm.android.ui.theme.QuantmTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Links and "open the player" requests from intents, handled once the UI is up */
object Incoming {
    val link = MutableStateFlow<String?>(null)
    val openPlayer = MutableStateFlow(false)
    /** Now Playing is open: its dark artwork wash needs light status bar icons */
    val playerOpen = MutableStateFlow(false)
}

@Composable
fun QuantmRoot() {
    val graph = LocalContext.current.graph
    val auth by graph.auth.auth.collectAsStateWithLifecycle()
    val preset by graph.settings.theme.flow.collectAsStateWithLifecycle()
    val dynamic by graph.settings.dynamicColor.flow.collectAsStateWithLifecycle()
    val albumColors by graph.settings.albumColors.flow.collectAsStateWithLifecycle()
    val player by graph.player.state.collectAsStateWithLifecycle()
    val current = player.current
    val artwork = if (albumColors && !dynamic) rememberArtworkColors(current?.artworkUrl ?: current?.user?.avatarUrl) else null

    QuantmTheme(preset, dynamic, artwork) {
        val light = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f > 0.5f }
        val view = LocalView.current
        val playerOpen by Incoming.playerOpen.collectAsStateWithLifecycle()
        val lightBars = light && auth != null && !playerOpen
        // the splash screen and edge-to-edge both set bar colors, so set them again whenever the theme flips
        LaunchedEffect(lightBars) {
            val activity = view.context as ComponentActivity
            val style = if (lightBars) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            if (auth == null) {
                // the main screens have their own snackbars; sign-in errors need one too
                val snackbar = remember { SnackbarHostState() }
                val graphMessages = LocalContext.current.graph.messages
                LaunchedEffect(Unit) { graphMessages.messages.collect { snackbar.showSnackbar(it) } }
                Box(Modifier.fillMaxSize()) {
                    SignInScreen()
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
                }
            } else MainUi()
        }
    }
}

private data class Tab(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(HomeRoute, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(SearchRoute, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Tab(LibraryRoute, "Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
)

private fun NavBackStackEntry.isTab(tab: Any) = when (tab) {
    HomeRoute -> destination.hasRoute<HomeRoute>()
    SearchRoute -> destination.hasRoute<SearchRoute>()
    else -> destination.hasRoute<LibraryRoute>()
}

@Composable
private fun MainUi() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerProgress = remember { Animatable(0f) }
    val snackbar = remember { SnackbarHostState() }
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloads by graph.offline.entries.collectAsStateWithLifecycle()
    val update by graph.updates.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<Pair<Track, (() -> Unit)?>?>(null) }
    var adding by remember { mutableStateOf<List<Track>?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }

    val actions = remember(nav) {
        object : Actions {
            override fun navigate(route: Any) {
                if (playerProgress.value > 0f) scope.launch { playerProgress.animateTo(0f, tween(250)) }
                nav.navigate(route) { launchSingleTop = true }
            }
            override fun back() { nav.popBackStack() }
            override fun trackMenu(track: Track, removable: (() -> Unit)?) { menu = track to removable }
            override fun addToPlaylist(tracks: List<Track>) { adding = tracks }
            override fun share(title: String, url: String?) {
                if (url == null) return
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title)
                context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            override fun openInSoundCloud(url: String?) {
                if (url == null) return
                // a chooser, so the link opens in SoundCloud's app or the browser, not back in Quantm
                try {
                    val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
                    context.startActivity(Intent.createChooser(view, "Open with").putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(context, app.quantm.android.MainActivity::class.java))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    graph.messages.show("No app can open that link.")
                }
            }
            override fun expandPlayer() { scope.launch { playerProgress.animateTo(1f) } }
        }
    }

    LaunchedEffect(Unit) {
        graph.player.connect()
        graph.messages.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        if (graph.settings.checkUpdates.value) graph.updates.check()
    }
    LaunchedEffect(Unit) {
        Incoming.openPlayer.collect { open -> if (open) { Incoming.openPlayer.value = false; actions.expandPlayer() } }
    }
    LaunchedEffect(Unit) {
        Incoming.link.collect { link ->
            if (link == null) return@collect
            Incoming.link.value = null
            runCatching { graph.api.resolve(link) }
                .onSuccess { item -> if (item != null) actions.open(item) else graph.messages.show("Quantm can't open that link.") }
                .onFailure { graph.messages.show(it.friendly()) }
        }
    }

    val hasPlayer = player.current != null
    val navBarHeight = 80.dp + with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomPadding = navBarHeight + if (hasPlayer) MiniPlayerHeight + 8.dp else 0.dp
    val p = playerProgress.value
    val open = p > 0.5f
    LaunchedEffect(open) { Incoming.playerOpen.value = open }
    // screen readers shouldn't wander into the screens hidden behind Now Playing
    val hide = if (open) Modifier.clearAndSetSemantics {} else Modifier

    CompositionLocalProvider(
        LocalActions provides actions,
        LocalBottomPadding provides bottomPadding,
        LocalNowPlaying provides NowPlaying(player.current?.id, player.isPlaying),
        LocalDownloaded provides downloads.map { it.track.id }.toHashSet(),
    ) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = HomeRoute,
                modifier = Modifier.fillMaxSize().then(hide),
                enterTransition = { enter() },
                exitTransition = { exit() },
                popEnterTransition = { popEnter() },
                popExitTransition = { popExit() },
            ) {
                composable<HomeRoute> { HomeScreen() }
                composable<SearchRoute> { SearchScreen() }
                composable<LibraryRoute> { LibraryScreen() }
                composable<TrackRoute> { TrackScreen(it.toRoute<TrackRoute>().id) }
                composable<ArtistRoute> { ArtistScreen(it.toRoute<ArtistRoute>().id) }
                composable<PlaylistRoute> { PlaylistScreen(it.toRoute<PlaylistRoute>().id) }
                composable<StationRoute> { val r = it.toRoute<StationRoute>(); StationScreen(r.urn, r.title) }
                composable<LocalPlaylistRoute> { LocalPlaylistScreen(it.toRoute<LocalPlaylistRoute>().id) }
                composable<LikesRoute> { LikesScreen() }
                composable<HistoryRoute> { HistoryScreen() }
                composable<DownloadsRoute> { DownloadsScreen() }
                composable<PlaylistsRoute> { PlaylistsScreen() }
                composable<LocalPlaylistsRoute> { LocalPlaylistsScreen() }
                composable<UsersRoute> { val r = it.toRoute<UsersRoute>(); UsersScreen(r.userId, r.followers, r.title) }
                composable<SettingsRoute> { SettingsScreen() }
                composable<EqualizerRoute> { EqualizerScreen() }
                composable<ThemesRoute> { ThemesScreen() }
            }

            // tabs, sliding away as Now Playing opens
            val stack = nav.currentBackStack.collectAsStateWithLifecycle().value
            val selected = TABS.lastOrNull { tab -> stack.any { it.isTab(tab.route) } } ?: TABS[0]
            // on the tab screens (which have no top bar), keep scrolled content out from under the clock
            if (stack.lastOrNull()?.let(::isTabRoute) == true) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                )
            }
            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = size.height * p }
                    .then(hide),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                TABS.forEach { tab ->
                    val isSelected = tab == selected
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                // tapping the current tab goes back to its top
                                nav.popBackStack(tab.route, inclusive = false)
                            } else {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding * (1f - p) + 8.dp),
            )

            PlayerSheet(playerProgress, bottomInset = navBarHeight)
        }

        menu?.let { (track, remove) -> TrackMenuSheet(track, remove, onDismiss = { menu = null }) }
        adding?.let { tracks -> AddToPlaylistSheet(tracks, onDismiss = { adding = null }) }

        val available = update as? UpdateState.Available
        if (available != null && !updateDismissed && available.release.version != graph.settings.skippedUpdate.value) {
            AlertDialog(
                onDismissRequest = { updateDismissed = true },
                title = { Text("Quantm ${available.release.version} is ready") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(available.release.notes.ifBlank { "A new version of Quantm." }.take(1200), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        updateDismissed = true
                        scope.launch { graph.updates.downloadAndInstall(available.release) }
                        graph.messages.show("Downloading the update…")
                    }) { Text("Update") }
                },
                dismissButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { updateDismissed = true }) { Text("Later") }
                    }
                },
            )
        }
    }
}

private fun isTabRoute(entry: NavBackStackEntry) = TABS.any { entry.isTab(it.route) }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (isTabRoute(targetState)) fadeIn(tween(220)) else slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (isTabRoute(targetState)) fadeOut(tween(160)) else slideOutHorizontally(tween(300)) { -it / 8 } + fadeOut(tween(250))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(tween(300)) { -it / 8 } + fadeIn(tween(300))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(250))
