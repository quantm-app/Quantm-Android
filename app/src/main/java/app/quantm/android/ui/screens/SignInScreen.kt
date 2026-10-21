package app.quantm.android.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.quantm.android.BuildConfig
import app.quantm.android.R
import app.quantm.android.data.SoundCloud
import app.quantm.android.data.friendly
import app.quantm.android.graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** First run: what Quantm is, and sign in with SoundCloud */
@Composable
fun SignInScreen() {
    var web by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    if (web) {
        SoundCloudLogin(onClose = { web = false })
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1458), Color(0xFF0B2E3A), MaterialTheme.colorScheme.background)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Box(
                Modifier
                    .size(112.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF06B6D4))), MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_notification), null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Quantm", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "SoundCloud without the ads. Your feed, likes and playlists, themes, an equalizer and offline downloads.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(48.dp))
            Button(onClick = { web = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Sign in with SoundCloud", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { manual = true }) { Text("Enter a token manually", color = Color.White.copy(alpha = 0.8f)) }
            if (BuildConfig.DEBUG) {
                // for testing public pages and playback without a SoundCloud login; not in release builds
                val graph = LocalContext.current.graph
                val scope = rememberCoroutineScope()
                TextButton(onClick = {
                    scope.launch {
                        runCatching { graph.api.scrapeClientId() }
                            .onSuccess { graph.auth.save("", it) }
                            .onFailure { graph.messages.show(it.friendly()) }
                    }
                }) { Text("Debug: continue as guest", color = Color.White.copy(alpha = 0.5f)) }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Free and open source. Quantm talks only to SoundCloud and keeps your login on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (manual) ManualTokenDialog(onDismiss = { manual = false })
}

/**
 * soundcloud.com's own sign-in page in a web view. Signing in sets SoundCloud's oauth_token
 * cookie, which is the login Quantm keeps; nothing else is read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SoundCloudLogin(onClose: () -> Unit) {
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(0) }
    var finishing by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun tokenFromCookies(): String? {
        val cookies = CookieManager.getInstance().getCookie("https://soundcloud.com") ?: return null
        return cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("oauth_token=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
    }

    fun finish(token: String) {
        if (finishing) return
        finishing = true
        scope.launch {
            runCatching { graph.api.scrapeClientId() }
                .onSuccess { clientId ->
                    graph.auth.save(token, clientId)
                    graph.social.load()
                }
                .onFailure {
                    finishing = false
                    graph.messages.show("Signed in, but couldn't reach SoundCloud: " + it.friendly())
                }
        }
    }

    // the cookie can appear without a page load (sign-in happens in scripts), so also check now and then
    LaunchedEffect(Unit) {
        while (!finishing) {
            tokenFromCookies()?.let { finish(it) }
            delay(700)
        }
    }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text("Sign in to SoundCloud") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
            modifier = Modifier.statusBarsPadding(),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        )
        if (progress in 1..99 || finishing) LinearProgressIndicator(progress = { if (finishing) 1f else progress / 100f }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).navigationBarsPadding()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // soundcloud.com only shows its sign-in form to desktop browsers
                        settings.userAgentString = SoundCloud.DESKTOP_UA
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.scheme != "https"
                            override fun onPageFinished(view: WebView?, url: String?) {
                                tokenFromCookies()?.let { finish(it) }
                                // the desktop page is wider than a phone: lay it out at tablet width and zoom to fit
                                view?.evaluateJavascript(FIT_PAGE, null)
                            }
                        }
                        loadUrl("https://soundcloud.com/signin")
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (finishing) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Signing you in…", color = Color.Black)
                    }
                }
            }
        }
    }
}

private const val FIT_PAGE = """(function(){
  var m = document.querySelector('meta[name=viewport]');
  if (!m) { m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
  if (m.content !== 'width=820') m.content = 'width=820';
})()"""

@Composable
fun ManualTokenDialog(onDismiss: () -> Unit) {
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter a token") },
        text = {
            Column {
                Text(
                    "On soundcloud.com in a computer browser, open the developer tools and copy the oauth_token cookie. The client_id is optional: Quantm finds one itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(token, { token = it.trim() }, label = { Text("oauth_token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(clientId, { clientId = it.trim() }, label = { Text("client_id (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = token.isNotBlank() && !busy,
                onClick = {
                    if (clientId.isNotEmpty() && !Regex("^[a-zA-Z0-9]{32}$").matches(clientId)) {
                        error = "A client_id is 32 letters and numbers."
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        runCatching { clientId.ifEmpty { graph.api.scrapeClientId() } }
                            .onSuccess { id ->
                                graph.auth.save(token, id)
                                runCatching { graph.api.me() }
                                    .onSuccess { graph.social.load(); onDismiss() }
                                    .onFailure {
                                        graph.auth.clear()
                                        error = "SoundCloud didn't accept that token."
                                    }
                            }
                            .onFailure { error = it.friendly() }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Checking…" else "Sign in") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
