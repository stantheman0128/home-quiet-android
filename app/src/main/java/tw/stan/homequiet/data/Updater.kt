package tw.stan.homequiet.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val downloading: Boolean = false,
    val filePath: String? = null,
)

/** Checks the public GitHub "latest" release; downloads the APK and hands it to the system installer. */
object Updater {
    private const val API = "https://api.github.com/repos/stantheman0128/home-quiet-android/releases/latest"
    private const val PREFS = "updater"
    private const val KEY_AWAIT = "await_install_permission"
    private const val THROTTLE_MS = 30_000L
    private const val UA = "HomeQuiet"

    private val _state = MutableStateFlow<UpdateInfo?>(null)
    val state = _state.asStateFlow()
    private var lastCheck = 0L

    /** null = not checked, "" = up to date, "error" = failed, else newer version. */
    private val _result = MutableStateFlow<String?>(null)
    val result = _result.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking = _checking.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _prompts = MutableSharedFlow<Intent>(extraBufferCapacity = 2)
    val prompts = _prompts.asSharedFlow()

    suspend fun check(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < THROTTLE_MS) return
        if (_checking.value) return
        _checking.value = true
        val found = withContext(Dispatchers.IO) { runCatching { fetchLatest(context) } }
        _checking.value = false
        if (found.isSuccess) {
            lastCheck = now
            _state.value = found.getOrNull()?.let { remote ->
                val kept = _state.value
                if (kept != null && kept.version == remote.version && kept.filePath != null) {
                    remote.copy(filePath = kept.filePath)
                } else {
                    remote
                }
            }
            _error.value = null
            _result.value = found.getOrNull()?.version ?: ""
        } else {
            // Failures stay retryable on the next resume. Keep a previously found update on screen.
            val message = explain(found.exceptionOrNull() ?: IllegalStateException("檢查更新失敗"))
            _error.value = message
            _result.value = "error"
        }
    }

    /**
     * @return a message to show when the download or install step failed; null on success.
     * The installer intent is emitted on [prompts] so the activity can launch it in the foreground.
     */
    suspend fun downloadAndInstall(context: Context): String? {
        val info = _state.value ?: return "沒有可安裝的更新"
        if (info.downloading) return null
        if (!context.packageManager.canRequestPackageInstalls()) {
            markAwaitingPermission(context)
            return "請允許安裝未知的應用程式"
        }
        clearAwaiting(context)
        val ready = info.filePath?.let(::File)?.takeIf { looksLikeApk(it) }
        if (ready != null) {
            emitInstall(context, ready)
            return null
        }
        _state.value = info.copy(downloading = true)
        _error.value = null
        var failure = "下載失敗"
        val file = withContext(Dispatchers.IO) {
            val managed = runCatching { downloadViaManager(context, info) }
                .onFailure { failure = explain(it) }
                .getOrNull()
            if (managed != null && looksLikeApk(managed)) return@withContext managed
            if (managed != null) failure = "下載的檔案不是安裝包"
            runCatching { downloadInApp(context, info) }
                .onFailure { failure = explain(it) }
                .getOrNull()
        }
        if (file == null || !looksLikeApk(file)) {
            _state.value = info.copy(downloading = false)
            _error.value = failure
            return failure
        }
        _state.value = info.copy(downloading = false, filePath = file.absolutePath)
        _error.value = null
        emitInstall(context, file)
        return null
    }

    fun markAwaitingPermission(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AWAIT, true).commit()
    }

    /** After the user returns from the unknown-apps screen, continue the download they asked for. */
    suspend fun continuePending(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_AWAIT, false)) return null
        if (!context.packageManager.canRequestPackageInstalls()) return null
        if (_state.value == null) check(context, force = true)
        if (_state.value == null) return _error.value
        return downloadAndInstall(context)
    }

    fun unknownSourcesIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
    }

    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        runCatching {
            context.grantUriPermission("com.android.packageinstaller", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.grantUriPermission("com.google.android.packageinstaller", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { clipData = android.content.ClipData.newRawUri("apk", uri) }
    }

    fun current(context: Context): String {
        val name = runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: "0"
    }

    fun newer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun explain(error: Throwable): String {
        val msg = error.message.orEmpty()
        return when (error) {
            is UnknownHostException, is ConnectException -> "沒有網路，請連線後再檢查更新"
            is SocketTimeoutException -> "檢查更新逾時，請稍後再試"
            is IOException -> when {
                msg.contains("HTTP 403") -> "GitHub 拒絕更新檢查（HTTP 403）"
                msg.contains("HTTP 404") -> "找不到已發布的版本"
                msg.startsWith("HTTP ") -> "檢查更新失敗（$msg）"
                msg.startsWith("下載") || msg.startsWith("檢查") || msg.contains("安裝包") -> msg
                msg.isBlank() -> "沒有網路，請連線後再檢查更新"
                else -> "檢查更新失敗：$msg"
            }
            else -> when {
                msg.contains("HTTP 403") -> "GitHub 拒絕更新檢查（HTTP 403）"
                msg.contains("HTTP 404") -> "找不到已發布的版本"
                msg.startsWith("HTTP ") -> "檢查更新失敗（$msg）"
                msg.isBlank() -> "檢查更新失敗"
                else -> msg
            }
        }
    }

    private fun fetchLatest(context: Context): UpdateInfo? {
        val connection = open(API, current(context))
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(160)
                throw IOException("HTTP $code ${detail}".trim())
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            var preferred: String? = null
            var fallback: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.getString("browser_download_url")
                if (name.startsWith("HomeQuiet", ignoreCase = true)) {
                    preferred = url
                    break
                }
                if (fallback == null) fallback = url
            }
            val url = preferred ?: fallback ?: return null
            return if (newer(tag, current(context))) UpdateInfo(tag, url) else null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadViaManager(context: Context, info: UpdateInfo): File? {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return null
        val name = "HomeQuiet-${info.version}.apk"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        dir.mkdirs()
        val out = File(dir, name)
        if (out.exists()) out.delete()
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("HomeQuiet ${info.version}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        request.addRequestHeader("User-Agent", "$UA/${current(context)}")
        val id = manager.enqueue(request)
        val deadline = System.currentTimeMillis() + 180_000L
        while (System.currentTimeMillis() < deadline) {
            delay(400)
            val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: continue
            cursor.use {
                if (!it.moveToFirst()) return@use
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> return out.takeIf { file -> file.exists() }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        throw IOException("下載失敗（代碼 $reason）")
                    }
                }
            }
        }
        runCatching { manager.remove(id) }
        return null
    }

    private fun downloadInApp(context: Context, info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "HomeQuiet-${info.version}.apk")
        val connection = open(info.apkUrl, current(context))
        try {
            connection.setRequestProperty("Accept", "application/octet-stream")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        if (!looksLikeApk(out)) {
            out.delete()
            throw IOException("下載的檔案不是安裝包")
        }
        return out
    }

    private fun open(url: String, version: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "$UA/$version")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return connection
    }

    private fun looksLikeApk(file: File): Boolean {
        if (!file.isFile || file.length() < 10_000L) return false
        val head = ByteArray(2)
        val read = file.inputStream().use { it.read(head) }
        return read == 2 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
    }

    private fun emitInstall(context: Context, file: File) {
        val emitted = _prompts.tryEmit(installIntent(context, file))
        if (!emitted) _error.value = "無法開啟安裝程式，請再按一次"
    }

    private fun clearAwaiting(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AWAIT, false).apply()
    }
}
