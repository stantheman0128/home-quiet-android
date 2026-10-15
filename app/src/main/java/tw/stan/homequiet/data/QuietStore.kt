package tw.stan.homequiet.data

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class QuietStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<QuietSnapshot> = _snapshot.asStateFlow()

    fun setOnboardingDone() {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
            _snapshot.value = _snapshot.value.copy(onboardingDone = true)
        }
    }

    fun setPaused(paused: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_PAUSED, paused).apply()
            _snapshot.value = _snapshot.value.copy(paused = paused)
        }
    }

    fun setRadius(meters: Int) {
        synchronized(lock) {
            val cur = _snapshot.value
            val radius = meters.coerceIn(QuietSnapshot.MIN_RADIUS, QuietSnapshot.MAX_RADIUS)
            val inside = insideAt(cur, cur.currentLat, cur.currentLon, radius, hard = true)
            prefs.edit()
                .putInt(KEY_RADIUS, radius)
                .putBoolean(KEY_INSIDE, inside)
                .apply()
            _snapshot.value = cur.copy(radiusMeters = radius, geofenceInside = inside)
        }
    }

    fun setWifiAlso(enabled: Boolean, captureSsid: String?) {
        synchronized(lock) {
            val cur = _snapshot.value
            val ssid = captureSsid ?: cur.homeSsid
            val set = if (captureSsid != null) cur.homeSsids + captureSsid else cur.homeSsids
            prefs.edit()
                .putBoolean(KEY_WIFI, enabled)
                .putString(KEY_HOME_SSID, ssid)
                .putStringSet(KEY_SSIDS, set)
                .apply()
            _snapshot.value = cur.copy(wifiAlsoCounts = enabled, homeSsid = ssid, homeSsids = set)
        }
    }

    fun addSsid(ssid: String) = editSsids { it + ssid }
    fun removeSsid(ssid: String) = editSsids { it - ssid }
    private fun editSsids(f: (Set<String>) -> Set<String>) {
        synchronized(lock) {
            val next = f(_snapshot.value.homeSsids).filter { it.isNotBlank() }.toSet()
            prefs.edit().putStringSet(KEY_SSIDS, next).apply()
            _snapshot.value = _snapshot.value.copy(homeSsids = next)
        }
    }

    fun setSelected(packages: Set<String>) {
        synchronized(lock) {
            prefs.edit().putStringSet(KEY_PKGS, packages.toSet()).apply()
            _snapshot.value = _snapshot.value.copy(selectedPackages = packages.toSet())
        }
    }

    fun setHome(lat: Double, lon: Double, ssid: String?, fixTime: Long) {
        synchronized(lock) {
            val cur = _snapshot.value
            val homeSsid = ssid ?: cur.homeSsid
            val currentSsid = ssid ?: cur.currentSsid
            prefs.edit()
                .putString(KEY_HOME_LAT, lat.toPref())
                .putString(KEY_HOME_LON, lon.toPref())
                .putString(KEY_HOME_SSID, homeSsid)
                .putStringSet(KEY_SSIDS, if (ssid != null) cur.homeSsids + ssid else cur.homeSsids)
                .putString(KEY_CUR_LAT, lat.toPref())
                .putString(KEY_CUR_LON, lon.toPref())
                .putLong(KEY_CUR_TIME, fixTime)
                .putString(KEY_CUR_SSID, currentSsid)
                .putBoolean(KEY_INSIDE, true)
                .apply()
            _snapshot.value = cur.copy(
                homeLat = lat,
                homeLon = lon,
                homeSsid = homeSsid,
                homeSsids = if (ssid != null) cur.homeSsids + ssid else cur.homeSsids,
                currentLat = lat,
                currentLon = lon,
                fixTime = fixTime,
                currentSsid = currentSsid,
                geofenceInside = true,
            )
        }
    }

    fun setFix(lat: Double, lon: Double, time: Long, source: String = "定位", accuracy: Float = 0f) {
        synchronized(lock) {
            val cur = _snapshot.value
            if (cur.currentLat != null && cur.currentLon != null) {
                val moved = Geo.meters(cur.currentLat, cur.currentLon, lat, lon)
                if (moved < 8.0 && time - cur.fixTime < 30_000L && time >= cur.fixTime) return
            }
            // An older fix must not override a newer geofence event.
            if (time < cur.fixTime) return
            // Coarse fixes (e.g. cell-tower, 500 m+) can't decide anything near a 120 m radius.
            if (accuracy > maxOf(cur.radiusMeters * 2f, 300f)) return
            val inside = decide(cur, lat, lon, accuracy)
            val meters = cur.homeLat?.let { Geo.meters(it, cur.homeLon ?: 0.0, lat, lon) }
            logState(cur, inside, source + (if (accuracy > 0f) " ±${accuracy.toInt()}" else ""), meters)
            prefs.edit()
                .putString(KEY_CUR_LAT, lat.toPref())
                .putString(KEY_CUR_LON, lon.toPref())
                .putLong(KEY_CUR_TIME, time)
                .putBoolean(KEY_INSIDE, inside)
                .apply()
            _snapshot.value = _snapshot.value.copy(
                currentLat = lat,
                currentLon = lon,
                fixTime = time,
                geofenceInside = inside,
            )
        }
    }

    /** Play Services geofence transition: authoritative inside/outside, counts as a fresh fix. */
    fun setFenceEvent(inside: Boolean, lat: Double?, lon: Double?, time: Long) {
        synchronized(lock) {
            val cur = _snapshot.value
            if (!inside && cur.homeLat != null && lat != null && lon != null) {
                val d = Geo.meters(cur.homeLat, cur.homeLon ?: 0.0, lat, lon)
                if (d <= cur.radiusMeters + QuietSnapshot.EXIT_MARGIN_M) {
                    // Jittery EXIT: the triggering fix is still near home. Ignore.
                    val n = DismissedNote(System.nanoTime(), STATE_PKG, "忽略離開", "地理圍欄 · ${d.toInt()} m", "", time, "")
                    val next = (listOf(n) + cur.log).take(QuietSnapshot.LOG_LIMIT)
                    prefs.edit().putString(KEY_LOG, encodeLog(next)).apply()
                    _snapshot.value = cur.copy(log = next)
                    return
                }
            }
            logState(cur, inside, "地理圍欄", null)
            val e = prefs.edit().putBoolean(KEY_INSIDE, inside).putLong(KEY_CUR_TIME, time)
            if (lat != null && lon != null) e.putString(KEY_CUR_LAT, lat.toPref()).putString(KEY_CUR_LON, lon.toPref())
            e.apply()
            _snapshot.value = _snapshot.value.copy(
                geofenceInside = inside,
                fixTime = time,
                currentLat = lat ?: cur.currentLat,
                currentLon = lon ?: cur.currentLon,
            )
        }
    }

    private var lastLogged: Boolean? = null

    /** Writes home/away changes to the Log tab (caller holds lock). */
    private fun logState(cur: QuietSnapshot, inside: Boolean, source: String, meters: Double?) {
        if (cur.homeLat == null) return
        val known = lastLogged ?: prefs.getString(KEY_LOGGED, null)?.toBooleanStrictOrNull()
        if (known == inside) return
        lastLogged = inside
        prefs.edit().putString(KEY_LOGGED, inside.toString()).apply()
        val note = DismissedNote(
            id = System.nanoTime(),
            packageName = STATE_PKG,
            appLabel = if (inside) "到家" else "離家",
            title = source + (meters?.let { " · ${it.toInt()} m" } ?: ""),
            text = "",
            timestamp = System.currentTimeMillis(),
            reason = "",
        )
        val next = (listOf(note) + _snapshot.value.log).take(QuietSnapshot.LOG_LIMIT)
        prefs.edit().putString(KEY_LOG, encodeLog(next)).apply()
        _snapshot.value = _snapshot.value.copy(log = next)
    }

    fun setPlayFence(on: Boolean) {
        synchronized(lock) {
            if (_snapshot.value.playFence == on) return
            prefs.edit().putBoolean(KEY_PLAY, on).apply()
            _snapshot.value = _snapshot.value.copy(playFence = on)
        }
    }

    fun setSsid(ssid: String?) {
        synchronized(lock) {
            val cur = _snapshot.value
            if (cur.currentSsid == ssid) return
            prefs.edit().putString(KEY_CUR_SSID, ssid).apply()
            val next = cur.copy(currentSsid = ssid)
            _snapshot.value = next
            if (cur.wifiAlsoCounts && cur.wifiMatch != next.wifiMatch) {
                val n = DismissedNote(System.nanoTime(), STATE_PKG, if (next.wifiMatch) "住家 Wi-Fi" else "離開 Wi-Fi", ssid ?: "", "", System.currentTimeMillis(), "")
                val log = (listOf(n) + next.log).take(QuietSnapshot.LOG_LIMIT)
                prefs.edit().putString(KEY_LOG, encodeLog(log)).apply()
                _snapshot.value = next.copy(log = log)
            }
        }
    }

    fun appendDismissed(note: DismissedNote) {
        synchronized(lock) {
            val cur = _snapshot.value
            val next = (listOf(note) + cur.log).take(QuietSnapshot.LOG_LIMIT)
            prefs.edit().putString(KEY_LOG, encodeLog(next)).apply()
            _snapshot.value = cur.copy(log = next)
        }
    }

    fun clearLog() {
        synchronized(lock) {
            prefs.edit().remove(KEY_LOG).apply()
            _snapshot.value = _snapshot.value.copy(log = emptyList())
        }
    }

    /**
     * Hysteresis: enter when within radius; once inside, leave only when the fix is clearly
     * outside (distance minus accuracy beyond radius + margin).
     */
    private fun decide(cur: QuietSnapshot, lat: Double, lon: Double, accuracy: Float): Boolean {
        val homeLat = cur.homeLat ?: return false
        val homeLon = cur.homeLon ?: return false
        val d = Geo.meters(homeLat, homeLon, lat, lon)
        return if (cur.geofenceInside) d - accuracy <= cur.radiusMeters + QuietSnapshot.EXIT_MARGIN_M
        else d <= cur.radiusMeters
    }

    fun note(label: String, detail: String) {
        synchronized(lock) {
            val n = DismissedNote(System.nanoTime(), STATE_PKG, label, detail, "", System.currentTimeMillis(), "")
            val next = (listOf(n) + _snapshot.value.log).take(QuietSnapshot.LOG_LIMIT)
            prefs.edit().putString(KEY_LOG, encodeLog(next)).apply()
            _snapshot.value = _snapshot.value.copy(log = next)
        }
    }

    private fun insideAt(
        cur: QuietSnapshot,
        lat: Double?,
        lon: Double?,
        radius: Int,
        hard: Boolean,
    ): Boolean {
        val homeLat = cur.homeLat ?: return false
        val homeLon = cur.homeLon ?: return false
        if (lat == null || lon == null) return false
        val distance = Geo.meters(homeLat, homeLon, lat, lon)
        val limit = if (hard || !cur.geofenceInside) {
            radius.toDouble()
        } else {
            radius + QuietSnapshot.EXIT_HYSTERESIS_M
        }
        return distance <= limit
    }

    private fun readSnapshot(): QuietSnapshot {
        val selected = if (!prefs.contains(KEY_PKGS)) {
            setOf(AppCatalog.GROK_PACKAGE)
        } else {
            prefs.getStringSet(KEY_PKGS, emptySet())?.toSet() ?: emptySet()
        }
        return QuietSnapshot(
            onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
            homeLat = prefs.getString(KEY_HOME_LAT, null).prefDouble(),
            homeLon = prefs.getString(KEY_HOME_LON, null).prefDouble(),
            radiusMeters = prefs.getInt(KEY_RADIUS, QuietSnapshot.DEFAULT_RADIUS)
                .coerceIn(QuietSnapshot.MIN_RADIUS, QuietSnapshot.MAX_RADIUS),
            paused = prefs.getBoolean(KEY_PAUSED, false),
            wifiAlsoCounts = prefs.getBoolean(KEY_WIFI, false),
            homeSsid = prefs.getString(KEY_HOME_SSID, null)?.takeIf { it.isNotBlank() },
            homeSsids = prefs.getStringSet(KEY_SSIDS, null)?.toSet()
                ?: setOfNotNull(prefs.getString(KEY_HOME_SSID, null)?.takeIf { it.isNotBlank() }),
            currentLat = prefs.getString(KEY_CUR_LAT, null).prefDouble(),
            currentLon = prefs.getString(KEY_CUR_LON, null).prefDouble(),
            fixTime = prefs.getLong(KEY_CUR_TIME, 0L),
            currentSsid = prefs.getString(KEY_CUR_SSID, null)?.takeIf { it.isNotBlank() },
            geofenceInside = prefs.getBoolean(KEY_INSIDE, false),
            playFence = prefs.getBoolean(KEY_PLAY, false),
            selectedPackages = selected,
            log = parseLog(prefs.getString(KEY_LOG, null)),
        )
    }

    companion object {
        private const val PREFS = "quiet"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_PAUSED = "paused"
        private const val KEY_RADIUS = "radius"
        private const val KEY_WIFI = "wifi_also"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LON = "home_lon"
        private const val KEY_HOME_SSID = "home_ssid"
        private const val KEY_CUR_LAT = "cur_lat"
        private const val KEY_CUR_LON = "cur_lon"
        private const val KEY_CUR_TIME = "cur_time"
        private const val KEY_CUR_SSID = "cur_ssid"
        private const val KEY_INSIDE = "inside"
        private const val KEY_PLAY = "play_fence"
        private const val KEY_SSIDS = "home_ssids"
        private const val KEY_LOGGED = "logged_inside"
        const val STATE_PKG = "tw.stan.homequiet.state"
        private const val KEY_PKGS = "packages"
        private const val KEY_LOG = "log"

        @Volatile
        private var instance: QuietStore? = null

        fun get(context: Context): QuietStore {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: QuietStore(context.applicationContext).also { instance = it }
            }
        }

        private fun Double.toPref(): String = String.format(Locale.US, "%.7f", this)

        private fun String?.prefDouble(): Double? = this?.toDoubleOrNull()

        private fun parseLog(raw: String?): List<DismissedNote> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            DismissedNote(
                                id = item.optLong("id"),
                                packageName = item.optString("pkg"),
                                appLabel = item.optString("label"),
                                title = item.optString("title"),
                                text = item.optString("text"),
                                timestamp = item.optLong("ts"),
                                reason = item.optString("reason"),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        private fun encodeLog(items: List<DismissedNote>): String {
            val array = JSONArray()
            for (note in items) {
                array.put(
                    JSONObject()
                        .put("id", note.id)
                        .put("pkg", note.packageName)
                        .put("label", note.appLabel)
                        .put("title", note.title)
                        .put("text", note.text)
                        .put("ts", note.timestamp)
                        .put("reason", note.reason),
                )
            }
            return array.toString()
        }
    }
}
