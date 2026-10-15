package tw.stan.homequiet.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager

data class WifiRead(val ssid: String?, val connected: Boolean)

object WifiHelper {
    /**
     * SSID of the network currently connected, if the system will reveal it.
     * On Android 10+ this stays hidden unless location permission is granted
     * and location services are on. Works alongside mobile data: a failed read
     * just means Wi-Fi is not an extra "at home" signal.
     */
    fun currentSsid(context: Context): String? = read(context).ssid

    /**
     * Mobile data is often the active network while Wi-Fi stays associated.
     * Read every Wi-Fi transport, not only [ConnectivityManager.getActiveNetwork].
     * [WifiRead.connected] is false only when Wi-Fi is off or not associated,
     * so a hidden SSID does not look like "left home".
     */
    @Suppress("DEPRECATION")
    fun read(context: Context): WifiRead {
        val app = context.applicationContext
        val wifi = app.getSystemService(WifiManager::class.java)
        if (wifi != null && !wifi.isWifiEnabled) return WifiRead(ssid = null, connected = false)

        var sawWifi = false
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        if (connectivity != null) {
            val networks = runCatching { connectivity.allNetworks }.getOrNull().orEmpty()
            for (network in networks) {
                val caps = runCatching { connectivity.getNetworkCapabilities(network) }.getOrNull() ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                sawWifi = true
                val info = caps.transportInfo as? WifiInfo
                normalize(info?.ssid)?.let { return WifiRead(it, connected = true) }
            }
        }
        normalize(wifi?.connectionInfo?.ssid)?.let { return WifiRead(it, connected = true) }
        val networkId = wifi?.connectionInfo?.networkId ?: -1
        if (sawWifi || networkId != -1) return WifiRead(ssid = null, connected = true)
        return WifiRead(ssid = null, connected = false)
    }

    private fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var value = raw.trim()
        if (value.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true)) return null
        if (value.equals("<unknown ssid>", ignoreCase = true)) return null
        if (value == "0x" || value.equals("null", ignoreCase = true)) return null
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1).trim()
        }
        if (value.isBlank() || value.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true)) return null
        return value
    }
}
