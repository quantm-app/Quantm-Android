package app.quantm.android

import android.app.Application
import android.content.Context
import app.quantm.android.data.AuthStore
import app.quantm.android.data.LocalPlaylists
import app.quantm.android.data.Messages
import app.quantm.android.data.Offline
import app.quantm.android.data.Settings
import app.quantm.android.data.Social
import app.quantm.android.data.SoundCloud
import app.quantm.android.data.Updates
import app.quantm.android.playback.AudioFx
import app.quantm.android.playback.PlayerConnection
import app.quantm.android.playback.QueueStore
import app.quantm.android.playback.SleepTimer
import app.quantm.android.playback.StreamResolver
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Everything the screens and the playback service share. Both run in the app's one process. */
class Graph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") != null) request
                else request.newBuilder().header("User-Agent", "Quantm/${BuildConfig.VERSION_NAME} (Android)").build()
            )
        }
        .build()

    val settings = Settings(context)
    val messages = Messages()
    val auth = AuthStore(context)
    val api = SoundCloud(http, auth)
    val social = Social(context, api, scope, messages)
    val localPlaylists = LocalPlaylists(context).apply {
        onSaveFailed = { messages.show("Couldn't save that change: your phone's storage is full.") }
    }
    val offline = Offline(context, api, scope, messages)
    val updates = Updates(context, http)
    val sleepTimer = SleepTimer()
    val audioFx = AudioFx(settings, scope)
    val queueStore = QueueStore(context)
    val resolver = StreamResolver(api, offline)
    val player = PlayerConnection(context, this)
}

class QuantmApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        if (graph.auth.current != null) graph.social.load()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache { DiskCache.Builder().directory(File(cacheDir, "images")).maxSizeBytes(150L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
}

val Context.graph: Graph get() = (applicationContext as QuantmApp).graph
