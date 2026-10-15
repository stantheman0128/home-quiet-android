package tw.stan.homequiet.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager

object WifiHelper {
    /**
     * SSID of the network currently connected, if the system will reveal it.
     * On Android 10+ this stays hidden unless location permission is granted
     * and location services are on. Works alongside mobile data: a failed read
     * just means Wi-Fi is not an extra "at home" signal.
     */
    @Suppress("DEPRECATION")
    fun currentSsid(context: Context): String? {
        val app = context.applicationContext
        val fromActive = runCatching { ssidFromActiveNetwork(app) }.getOrNull()
        if (!fromActive.isNullOrBlank()) return fromActive
        val manager = app.getSystemService(WifiManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return normalize(manager.connectionInfo?.ssid)
    }

    private fun ssidFromActiveNetwork(context: Context): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        val info = capabilities.transportInfo as? WifiInfo ?: return null
        return normalize(info.ssid)
    }

    private fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var value = raw.trim()
        if (value.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true)) return null
        if (value.equals("<unknown ssid>", ignoreCase = true)) return null
        if (value == "0x" || value.equals("null", ignoreCase = true)) return null
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
        }
        if (value.isBlank() || value.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true)) return null
        return value
    }
}
