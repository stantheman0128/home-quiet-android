package tw.stan.homequiet.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Off-main-thread app icon decoding with an in-memory LRU cache (~ 8 MB). */
object AppIcons {
    private const val PX = 96
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val d = context.packageManager.getApplicationIcon(packageName)
                d.toBitmap(PX, PX).asImageBitmap().also { it.prepareToDraw(); cache.put(packageName, it) }
            }.getOrNull()
        }
    }

    /** Warm the cache for the first rows right after the list loads. */
    suspend fun prefetch(context: Context, packages: List<String>) = coroutineScope {
        packages.chunked(8).forEach { chunk -> chunk.map { async { load(context, it) } }.forEach { it.await() } }
    }
}
