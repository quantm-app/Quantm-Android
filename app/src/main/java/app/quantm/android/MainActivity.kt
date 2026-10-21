package app.quantm.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.quantm.android.ui.Incoming
import app.quantm.android.ui.QuantmRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { QuantmRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        graph.player.connect()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) Incoming.openPlayer.value = true
        val link = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            // shared text often has words around the link ("Listen to X on #SoundCloud https://...")
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { LINK.find(it)?.value }
            else -> null
        }
        if (link != null && LINK.matches(link)) Incoming.link.value = link
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "openPlayer"
        private val LINK = Regex("""https://(m\.|on\.|www\.)?soundcloud\.com/\S+""")
    }
}
