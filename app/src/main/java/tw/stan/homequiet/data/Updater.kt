package tw.stan.homequiet.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(val version: String, val apkUrl: String, val downloading: Boolean = false)

/** Checks the public GitHub "latest" release; downloads the APK and hands it to the system installer. */
object Updater {
    private const val API = "https://api.github.com/repos/stantheman0128/home-quiet-android/releases/latest"
    private val _state = MutableStateFlow<UpdateInfo?>(null)
    val state = _state.asStateFlow()
    private var lastCheck = 0L

    suspend fun check(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 30 * 60_000L) return
        lastCheck = now
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
                }
                if (url != null && newer(tag, current(context))) UpdateInfo(tag, url) else null
            }.getOrNull()
        }
        _state.value = found
    }

    suspend fun downloadAndInstall(context: Context) {
        val info = _state.value ?: return
        if (info.downloading) return
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        _state.value = info.copy(downloading = true)
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, "HomeQuiet-${info.version}.apk")
                val c = URL(info.apkUrl).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true; c.connectTimeout = 10000; c.readTimeout = 30000
                c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                out
            }.getOrNull()
        }
        _state.value = info.copy(downloading = false)
        if (file == null) return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun current(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    fun newer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
