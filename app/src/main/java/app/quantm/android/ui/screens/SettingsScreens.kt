package app.quantm.android.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quantm.android.data.UpdateState
import app.quantm.android.graph
import app.quantm.android.playback.AudioFx
import app.quantm.android.playback.EQ_PRESETS
import app.quantm.android.ui.EqualizerRoute
import app.quantm.android.ui.LocalActions
import app.quantm.android.ui.ThemesRoute
import app.quantm.android.ui.components.Avatar
import app.quantm.android.ui.components.BackButton
import app.quantm.android.ui.components.ConfirmDialog
import app.quantm.android.ui.components.ListScreen
import app.quantm.android.ui.components.LocalBottomPadding
import app.quantm.android.ui.components.formatBytes
import app.quantm.android.ui.theme.ALBUM_MATCH
import app.quantm.android.ui.theme.PRESETS
import app.quantm.android.ui.theme.QuantmColors
import kotlinx.coroutines.launch

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(icon: ImageVector?, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun SwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }) { Switch(checked, onCheckedChange = onChange) }
}

@Composable
fun SettingsScreen() {
    val graph = LocalContext.current.graph
    val actions = LocalActions.current
    val me by graph.social.me.collectAsStateWithLifecycle()
    val theme by graph.settings.theme.flow.collectAsStateWithLifecycle()
    val dynamic by graph.settings.dynamicColor.flow.collectAsStateWithLifecycle()
    val skipSilence by graph.settings.skipSilence.flow.collectAsStateWithLifecycle()
    val checkUpdates by graph.settings.checkUpdates.flow.collectAsStateWithLifecycle()
    val downloads by graph.offline.entries.collectAsStateWithLifecycle()
    val update by graph.updates.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }

    ListScreen("Settings") {
        item {
            me?.let { user ->
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.avatarUrl, 56.dp, seed = user.id)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.username, style = MaterialTheme.typography.titleMedium)
                        Text("Signed in with SoundCloud", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { signingOut = true }) { Text("Sign out") }
                }
            }
        }
        item { SettingsGroup("Appearance") }
        item { SettingRow(Icons.Rounded.Palette, "Theme", if (dynamic) "Material You" else theme, onClick = { actions.navigate(ThemesRoute) }) }
        if (Build.VERSION.SDK_INT >= 31) item {
            SwitchRow(Icons.Rounded.ColorLens, "Material You", "Use your wallpaper's colors", dynamic) { graph.settings.dynamicColor.value = it }
        }
        item { SettingsGroup("Playback") }
        item { SettingRow(Icons.Rounded.Tune, "Equalizer", "Bass, treble and presets", onClick = { actions.navigate(EqualizerRoute) }) }
        item { SwitchRow(null, "Skip silence", "Skips quiet gaps, like long intros and outros", skipSilence) { graph.settings.skipSilence.value = it } }
        item { SettingsGroup("Downloads") }
        item {
            SettingRow(
                Icons.Rounded.Download,
                "Downloaded songs",
                if (downloads.isEmpty()) "None yet. Only songs whose artist allows downloads." else "${downloads.size} songs · ${formatBytes(graph.offline.totalBytes)}",
            )
        }
        item { SettingsGroup("Updates") }
        item {
            val subtitle = when (val s = update) {
                UpdateState.Checking -> "Checking…"
                UpdateState.UpToDate -> "You have the latest version (${graph.updates.currentVersion})"
                is UpdateState.Available -> "Version ${s.release.version} is ready"
                is UpdateState.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
                is UpdateState.Installing -> "Installing…"
                is UpdateState.Failed -> s.message
                UpdateState.Idle -> "Version ${graph.updates.currentVersion}"
            }
            SettingRow(Icons.Rounded.SystemUpdate, "Check for updates", subtitle, onClick = {
                when (val s = update) {
                    is UpdateState.Available -> scope.launch { graph.updates.downloadAndInstall(s.release) }
                    is UpdateState.Downloading, is UpdateState.Installing, UpdateState.Checking -> {}
                    else -> scope.launch { graph.updates.check() }
                }
            })
            (update as? UpdateState.Downloading)?.let {
                LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }
        item { SwitchRow(null, "Check automatically", "Look for a new version when Quantm starts", checkUpdates) { graph.settings.checkUpdates.value = it } }
        item { SettingsGroup("About") }
        item {
            SettingRow(Icons.Rounded.Code, "Quantm for Android ${graph.updates.currentVersion}", "Free and open source, no ads. github.com/quantm-app", onClick = {
                actions.openInSoundCloud("https://github.com/quantm-app/Quantm-Android")
            })
        }
        item {
            Text(
                "Quantm isn't made by or affiliated with SoundCloud. Copy-protected label songs can't play in Quantm; they open in SoundCloud instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (signingOut) {
        ConfirmDialog("Sign out?", "Quantm stops playing and forgets your SoundCloud login on this phone. Quantm playlists and downloads stay.", "Sign out", onDismiss = { signingOut = false }) {
            graph.player.stop()
            graph.queueStore.clear()
            graph.auth.clear()
            graph.social.clear()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen() {
    val graph = LocalContext.current.graph
    val theme by graph.settings.theme.flow.collectAsStateWithLifecycle()
    val dynamic by graph.settings.dynamicColor.flow.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp),
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottom + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "The same themes as desktop Quantm. Album Match takes its colors from the song that's playing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            items(PRESETS.entries.toList(), key = { it.key }) { (name, colors) ->
                ThemeSwatch(name, colors, selected = !dynamic && name == theme) {
                    graph.settings.dynamicColor.value = false
                    graph.settings.theme.value = name
                    graph.settings.albumColors.value = name == ALBUM_MATCH
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(name: String, q: QuantmColors, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
    ) {
        // a tiny preview of the app in this theme
        Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(q.bg).padding(10.dp)) {
            Column {
                Box(Modifier.fillMaxWidth(0.6f).height(8.dp).clip(CircleShape).background(q.text))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(6.dp).clip(CircleShape).background(q.muted))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { Box(Modifier.size(26.dp).clip(RoundedSmall).background(q.surface)) }
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(22.dp).clip(RoundedSmall).background(q.bar).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(q.wave)) {
                    Box(Modifier.fillMaxWidth(0.45f).fillMaxHeight().background(q.wavePlayed))
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(12.dp).clip(CircleShape).background(q.accent))
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
            }
        }
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
    }
}

private val RoundedSmall = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)

@Composable
fun EqualizerScreen() {
    val graph = LocalContext.current.graph
    val enabled by graph.settings.eqEnabled.flow.collectAsStateWithLifecycle()
    val stored by graph.settings.eqLevels.flow.collectAsStateWithLifecycle()
    val preset by graph.settings.eqPreset.flow.collectAsStateWithLifecycle()
    val bass by graph.settings.bassBoost.flow.collectAsStateWithLifecycle()
    val info by graph.audioFx.info.collectAsStateWithLifecycle()
    val levels = AudioFx.levelsFor(stored, info)

    ListScreen("Equalizer", large = false) {
        item {
            SwitchRow(Icons.Rounded.Tune, "Equalizer", if (enabled) "On" else "Off", enabled) { graph.settings.eqEnabled.value = it }
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(EQ_PRESETS.keys.toList().size) { i ->
                    val name = EQ_PRESETS.keys.elementAt(i)
                    FilterChip(
                        selected = preset == name,
                        enabled = enabled,
                        onClick = {
                            graph.settings.eqPreset.value = name
                            graph.settings.eqLevels.value = AudioFx.presetLevels(name, info).joinToString(",")
                        },
                        label = { Text(name) },
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                info.bands.forEachIndexed { band, freq ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "%+.0f".format(levels[band] / 100f),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VerticalSlider(
                            value = levels[band].toFloat(),
                            range = info.min.toFloat()..info.max.toFloat(),
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { v ->
                            val next = levels.toMutableList().also { it[band] = v.toInt() }
                            graph.settings.eqPreset.value = "Custom"
                            graph.settings.eqLevels.value = next.joinToString(",")
                        }
                        Text(if (freq >= 1000) "${freq / 1000}k" else "$freq", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Bass boost", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = bass.toFloat(),
                    onValueChange = { graph.settings.bassBoost.value = it.toInt() },
                    valueRange = 0f..1000f,
                    enabled = enabled,
                )
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(onClick = {
                    graph.settings.eqPreset.value = "Flat"
                    graph.settings.eqLevels.value = ""
                    graph.settings.bassBoost.value = 0
                }, enabled = enabled) { Text("Reset") }
            }
        }
        item {
            Text(
                "Uses your phone's built-in equalizer, so it works with headphones, speakers and Bluetooth.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** A Material slider turned upright, for the EQ bands */
@Composable
private fun VerticalSlider(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val length = maxHeight
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            // laid out sideways at the column's height, then turned upright around its center
            modifier = Modifier
                .requiredWidth(length)
                .graphicsLayer { rotationZ = -90f },
        )
    }
}
