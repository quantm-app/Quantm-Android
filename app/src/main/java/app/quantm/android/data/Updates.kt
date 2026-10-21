package app.quantm.android.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import app.quantm.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val page: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates from GitHub releases. Public builds read quantm-app/Quantm-Android; dev builds read
 * the private dev repository, so a dev build only ever updates to another dev build.
 * The APK is installed with Android's package installer, which asks you to confirm.
 */
class Updates(private val context: Context, private val http: OkHttpClient) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME.removeSuffix("-debug")
    private val isDev = BuildConfig.UPDATE_TOKEN.isNotEmpty()

    suspend fun check(): Release? {
        _state.value = UpdateState.Checking
        return try {
            val release = withContext(Dispatchers.IO) { latest() }
            _state.value = if (release != null && newer(release.version, currentVersion)) UpdateState.Available(release) else UpdateState.UpToDate
            (state.value as? UpdateState.Available)?.release
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.friendly())
            null
        }
    }

    private fun latest(): Release? {
        val request = github("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("GitHub answered ${it.code}")
            it.body.string()
        }
        val releases = QuantmJson.parseToJsonElement(body) as? JsonArray ?: return null
        return releases.mapNotNull { el ->
            val r = el as? JsonObject ?: return@mapNotNull null
            if (r["draft"]?.jsonPrimitive?.boolean == true) return@mapNotNull null
            // public builds take only full releases; dev builds are themselves prereleases
            if (!isDev && r["prerelease"]?.jsonPrimitive?.boolean == true) return@mapNotNull null
            val tag = r.str("tag_name") ?: return@mapNotNull null
            // the dev repository also holds desktop builds: Android ones are tagged android-*
            if (isDev && !tag.startsWith("android-")) return@mapNotNull null
            val apk = r.arr("assets").mapNotNull { it as? JsonObject }.firstOrNull { it.str("name")?.endsWith(".apk") == true }
                ?: return@mapNotNull null
            Release(
                version = tag.removePrefix("android-").removePrefix("v"),
                notes = r.str("body").orEmpty(),
                // private assets download through the API url; public ones through the browser url
                apkUrl = (if (isDev) apk.str("url") else apk.str("browser_download_url")) ?: return@mapNotNull null,
                size = apk["size"]?.jsonPrimitive?.longOrNull ?: 0,
                page = r.str("html_url").orEmpty(),
            )
        }.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    private fun github(url: String) = Request.Builder().url(url).apply {
        if (isDev) header("Authorization", "Bearer " + BuildConfig.UPDATE_TOKEN)
    }

    suspend fun downloadAndInstall(release: Release) {
        try {
            _state.value = UpdateState.Downloading(release, 0f)
            val apk = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "update.apk")
                val request = github(release.apkUrl).header("Accept", "application/octet-stream").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
                    val total = response.body.contentLength().takeIf { it > 0 } ?: release.size
                    var received = 0L
                    response.body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                received += n
                                if (total > 0) _state.value = UpdateState.Downloading(release, received.toFloat() / total)
                            }
                        }
                    }
                }
                file
            }
            _state.value = UpdateState.Installing(release)
            withContext(Dispatchers.IO) { install(apk) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Update failed: " + e.friendly())
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("quantm.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val action = context.packageName + ".UPDATE_STATUS"
            ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(c::startActivity)
                        }
                        PackageInstaller.STATUS_SUCCESS -> c.unregisterReceiver(this)
                        else -> {
                            c.unregisterReceiver(this)
                            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "cancelled"
                            _state.value = UpdateState.Failed("Update not installed: $message")
                        }
                    }
                }
            }, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            val intent = Intent(action).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    companion object {
        fun newer(candidate: String, current: String) = compareVersions(candidate, current) > 0

        /** 1.2.0 > 1.2.0-dev.5 > 1.2.0-dev.4 > 1.1.9 */
        fun compareVersions(a: String, b: String): Int {
            fun parse(v: String): Pair<List<Int>, List<String>> {
                val main = v.substringBefore('-')
                val pre = v.substringAfter('-', "")
                return main.split('.').map { it.toIntOrNull() ?: 0 } to (if (pre.isEmpty()) emptyList() else pre.split('.'))
            }
            val (am, ap) = parse(a)
            val (bm, bp) = parse(b)
            for (i in 0 until maxOf(am.size, bm.size)) {
                val d = am.getOrElse(i) { 0 } - bm.getOrElse(i) { 0 }
                if (d != 0) return d
            }
            if (ap.isEmpty() && bp.isEmpty()) return 0
            if (ap.isEmpty()) return 1
            if (bp.isEmpty()) return -1
            for (i in 0 until maxOf(ap.size, bp.size)) {
                val x = ap.getOrNull(i) ?: return -1
                val y = bp.getOrNull(i) ?: return 1
                val xi = x.toIntOrNull()
                val yi = y.toIntOrNull()
                val d = if (xi != null && yi != null) xi - yi else x.compareTo(y)
                if (d != 0) return d
            }
            return 0
        }
    }
}
