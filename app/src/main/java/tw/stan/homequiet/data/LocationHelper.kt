package tw.stan.homequiet.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object LocationHelper {
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!Permissions.hasFine(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: Location? = null
        for (provider in providers) {
            val fix = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || fix.time > best.time) best = fix
        }
        return best
    }

    /**
     * A single fresh fix from GPS or the network provider (Wi-Fi or mobile data).
     * Returns a recent last-known fix when one exists, so the button still works indoors.
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchFresh(context: Context, timeoutMs: Long = 8_000L): Location? {
        if (!Permissions.hasFine(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!manager.isLocationEnabled) return null
        val cached = lastKnown(context)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.time in 0..20_000L) return cached

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) return cached

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val listeners = mutableListOf<LocationListener>()
                val handler = Handler(Looper.getMainLooper())
                val once = AtomicBoolean(false)
                var best = cached

                fun cleanup() {
                    handler.removeCallbacksAndMessages(null)
                    listeners.forEach { listener ->
                        runCatching { manager.removeUpdates(listener) }
                    }
                }

                fun finish(location: Location?) {
                    if (!once.compareAndSet(false, true)) return
                    cleanup()
                    if (continuation.isActive) continuation.resume(location)
                }

                continuation.invokeOnCancellation {
                    if (once.compareAndSet(false, true)) cleanup()
                }
                handler.postDelayed({ finish(best) }, timeoutMs)

                var registered = 0
                for (provider in providers) {
                    if (once.get()) break
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val current = best
                            if (current == null || location.time >= current.time) best = location
                            finish(best)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                        override fun onProviderEnabled(provider: String) = Unit

                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    listeners += listener
                    val ok = runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }.isSuccess
                    if (!ok || once.get()) {
                        listeners.remove(listener)
                        if (!ok) continue
                        break
                    }
                    registered += 1
                }
                if (!once.get() && registered == 0) finish(cached)
            }
        }
    }
}
