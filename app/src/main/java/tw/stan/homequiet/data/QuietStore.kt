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
    private val _snapshot = MutableStateFlow(readSnapshot(context))
    val snapshot: StateFlow<QuietSnapshot> = _snapshot.asStateFlow()
    /** Consecutive outside fixes required before the location latch flips off. */
    private var outsideStreak = 0

    fun setOnboardingDone() {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
            _snapshot.value = _snapshot.value.copy(onboardingDone = true)
        }
    }

    fun setPaused(paused: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_PAUSED, paused).commit()
            _snapshot.value = _snapshot.value.copy(paused = paused)
        }
    }

    fun setRadius(meters: Int) {
        synchronized(lock) {
            val cur = _snapshot.value
            val radius = meters.coerceIn(QuietSnapshot.MIN_RADIUS, QuietSnapshot.MAX_RADIUS)
            val hasPoint = cur.currentLat != null && cur.currentLon != null
            val inside = if (hasPoint) insideAt(cur, cur.currentLat, cur.currentLon, radius, hard = true) else cur.geofenceInside
            val fence = if (hasPoint) inside else cur.fenceInside
            val edit = prefs.edit()
                .putInt(KEY_RADIUS, radius)
                .putBoolean(KEY_INSIDE, inside)
            if (fence != null) edit.putBoolean(KEY_FENCE, fence)
            edit.commit()
            _snapshot.value = cur.copy(radiusMeters = radius, geofenceInside = inside, fenceInside = fence)
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
            // Copy the set. putStringSet ignores the instance getStringSet just returned.
            prefs.edit().putStringSet(KEY_PKGS, packages.toSet()).commit()
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
                .putBoolean(KEY_FENCE, true)
                .commit()
            outsideStreak = 0
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
                fenceInside = true,
            )
        }
    }

    // ---------- extra places ----------
    private fun savePlaces(list: List<Place>) {
        prefs.edit().putString(KEY_PLACES, Place.encode(list)).commit()
        _snapshot.value = _snapshot.value.copy(places = list)
    }

    fun addPlace(name: String, lat: Double, lon: Double, radius: Int, ssid: String?): Place {
        synchronized(lock) {
            val p = Place(
                id = System.currentTimeMillis().toString(36), name = name.trim().ifBlank { "地點" },
                lat = lat, lon = lon, radius = radius.coerceIn(QuietSnapshot.MIN_RADIUS, QuietSnapshot.MAX_RADIUS),
                ssids = setOfNotNull(ssid), fixInside = true,
            )
            savePlaces(_snapshot.value.places + p)
            note("新增地點", "${p.name} · ${p.radius} m")
            return p
        }
    }

    fun updatePlace(id: String, f: (Place) -> Place) {
        synchronized(lock) { savePlaces(_snapshot.value.places.map { if (it.id == id) f(it) else it }) }
    }

    fun removePlace(id: String) {
        synchronized(lock) { savePlaces(_snapshot.value.places.filterNot { it.id == id }) }
    }

    /** Play geofence transition for an extra place. */
    fun setPlaceFence(id: String, inside: Boolean, lat: Double?, lon: Double?) {
        synchronized(lock) {
            val p = _snapshot.value.places.firstOrNull { it.id == id } ?: return
            if (!inside && lat != null && lon != null && p.distanceTo(lat, lon) <= p.radius + QuietSnapshot.EXIT_MARGIN_M) {
                note("忽略離開", "${p.name} · 地理圍欄 · ${p.distanceTo(lat, lon).toInt()} m"); return
            }
            if (p.fenceInside != inside) note(if (inside) "到達" else "離開", "${p.name} · 地理圍欄")
            savePlaces(_snapshot.value.places.map { if (it.id == id) it.copy(fenceInside = inside, fixInside = inside) else it })
        }
    }

    /** Location decision for each extra place (same hysteresis as home, no streak). Caller holds lock. */
    private fun applyFixToPlaces(lat: Double, lon: Double, accuracy: Float, source: String) {
        val list = _snapshot.value.places
        if (list.isEmpty()) return
        var changed = false
        val next = list.map { p ->
            val d = p.distanceTo(lat, lon)
            val dec = HomeSignals.locationInside(p.fixInside, d, accuracy, p.radius) ?: return@map p
            val far = HomeSignals.hardExit(d, accuracy)
            val fence = if (far && !dec) false else p.fenceInside
            if (dec != p.fixInside || fence != p.fenceInside) {
                changed = true
                if ((dec || fence) != (p.fixInside || p.fenceInside)) {
                    note(if (dec || fence) "到達" else "離開", "${p.name} · $source · ${d.toInt()} m")
                }
                p.copy(fixInside = dec, fenceInside = fence)
            } else p
        }
        if (changed) savePlaces(next)
    }

    fun setFix(lat: Double, lon: Double, time: Long, source: String = "定位", accuracy: Float = 0f) {
        synchronized(lock) {
            applyFixToPlaces(lat, lon, accuracy, source)
            if (_snapshot.value.homeLat == null) {
                // No home: still keep the current point for the radar / places.
                prefs.edit().putString(KEY_CUR_LAT, lat.toPref()).putString(KEY_CUR_LON, lon.toPref()).putLong(KEY_CUR_TIME, time).apply()
                _snapshot.value = _snapshot.value.copy(currentLat = lat, currentLon = lon, fixTime = time)
                return
            }
            val cur = _snapshot.value
            val homeLat = cur.homeLat ?: return
            val homeLon = cur.homeLon ?: return
            // A clearly older fix must not override a newer geofence event. Allow a few seconds of skew.
            if (cur.fixTime > 0L && time + 5_000L < cur.fixTime) return
            val distance = Geo.meters(homeLat, homeLon, lat, lon)
            val decision = HomeSignals.locationInside(cur.geofenceInside, distance, accuracy, cur.radiusMeters) ?: return
            val far = HomeSignals.hardExit(distance, accuracy)
            val changed = decision != cur.geofenceInside || (far && cur.fenceInside != false)
            if (!changed && cur.currentLat != null && cur.currentLon != null) {
                val moved = Geo.meters(cur.currentLat, cur.currentLon, lat, lon)
                if (moved < 8.0 && time - cur.fixTime < 30_000L && time >= cur.fixTime) return
            }
            if (decision) {
                outsideStreak = 0
            } else if (cur.geofenceInside) {
                outsideStreak += 1
                // One drifted sample stays "home". A second sample, or a fix kilometers away, may leave.
                if (outsideStreak < 2 && !far) {
                    prefs.edit()
                        .putString(KEY_CUR_LAT, lat.toPref())
                        .putString(KEY_CUR_LON, lon.toPref())
                        .putLong(KEY_CUR_TIME, time)
                        .commit()
                    _snapshot.value = cur.copy(currentLat = lat, currentLon = lon, fixTime = time)
                    return
                }
            }
            // GPS updates the location latch only. The Play geofence latch stays until EXIT,
            // unless this fix is confidently kilometers away (the fence may have been missed).
            val fence = if (far && !decision) false else cur.fenceInside
            val next = cur.copy(
                currentLat = lat,
                currentLon = lon,
                fixTime = time,
                geofenceInside = decision,
                fenceInside = fence,
            )
            logState(cur, next.atHomeOnly, source + (if (accuracy > 0f) " ±${accuracy.toInt()}" else ""), distance)
            val edit = prefs.edit()
                .putString(KEY_CUR_LAT, lat.toPref())
                .putString(KEY_CUR_LON, lon.toPref())
                .putLong(KEY_CUR_TIME, time)
                .putBoolean(KEY_INSIDE, decision)
            if (fence != null) edit.putBoolean(KEY_FENCE, fence)
            edit.commit()
            _snapshot.value = _snapshot.value.copy(
                currentLat = lat,
                currentLon = lon,
                fixTime = time,
                geofenceInside = decision,
                fenceInside = fence,
            )
        }
    }

    /** Play Services geofence transition. A GPS fix does not clear [QuietSnapshot.fenceInside] unless it is kilometers away. */
    fun setFenceEvent(inside: Boolean, lat: Double?, lon: Double?, time: Long): Boolean {
        synchronized(lock) {
            val cur = _snapshot.value
            if (!inside) {
                if (cur.homeLat == null || lat == null || lon == null) {
                    // An EXIT with no point is not evidence. Ask for a fresh fix instead of leaving home.
                    note("忽略離開", "地理圍欄沒有定位點")
                    return false
                }
                val d = Geo.meters(cur.homeLat, cur.homeLon ?: 0.0, lat, lon)
                if (d <= cur.radiusMeters + QuietSnapshot.EXIT_MARGIN_M) {
                    val n = DismissedNote(System.nanoTime(), STATE_PKG, "忽略離開", "地理圍欄 · ${d.toInt()} m", "", time, "")
                    val next = (listOf(n) + cur.log).take(QuietSnapshot.LOG_LIMIT)
                    prefs.edit().putString(KEY_LOG, encodeLog(next)).commit()
                    _snapshot.value = cur.copy(log = next)
                    return false
                }
            }
            outsideStreak = 0
            val prospective = cur.copy(
                fenceInside = inside,
                geofenceInside = inside,
                fixTime = time,
                currentLat = lat ?: cur.currentLat,
                currentLon = lon ?: cur.currentLon,
            )
            logState(cur, prospective.atHomeOnly, "地理圍欄", null)
            val e = prefs.edit()
                .putBoolean(KEY_INSIDE, inside)
                .putBoolean(KEY_FENCE, inside)
                .putLong(KEY_CUR_TIME, time)
            if (lat != null && lon != null) e.putString(KEY_CUR_LAT, lat.toPref()).putString(KEY_CUR_LON, lon.toPref())
            e.commit()
            _snapshot.value = _snapshot.value.copy(
                fenceInside = inside,
                geofenceInside = inside,
                fixTime = time,
                currentLat = lat ?: cur.currentLat,
                currentLon = lon ?: cur.currentLon,
            )
            return true
        }
    }

    private var lastLogged: Boolean? = null

    /** Writes home/away changes to the Log tab (caller holds lock). */
    private fun logState(cur: QuietSnapshot, atHome: Boolean, source: String, meters: Double?) {
        if (cur.homeLat == null) return
        val known = lastLogged ?: prefs.getString(KEY_LOGGED, null)?.toBooleanStrictOrNull()
        if (known == atHome) return
        lastLogged = atHome
        prefs.edit().putString(KEY_LOGGED, atHome.toString()).apply()
        val note = DismissedNote(
            id = System.nanoTime(),
            packageName = STATE_PKG,
            appLabel = if (atHome) "到家" else "離家",
            title = "住家 · " + source + (meters?.let { " · ${it.toInt()} m" } ?: ""),
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
            prefs.edit().putBoolean(KEY_PLAY, on).commit()
            _snapshot.value = _snapshot.value.copy(playFence = on)
        }
    }

    /**
     * @param connected false only when Wi-Fi is actually off or disconnected.
     * A hidden SSID (connected, name unreadable) must not clear a known home network.
     */
    fun applyWifi(connected: Boolean, ssid: String?) {
        if (!connected) setSsid(null)
        else if (!ssid.isNullOrBlank()) setSsid(ssid)
    }

    fun setSsid(ssid: String?) {
        synchronized(lock) {
            val cur = _snapshot.value
            if (cur.currentSsid == ssid) return
            prefs.edit().putString(KEY_CUR_SSID, ssid).commit()
            val next = cur.copy(currentSsid = ssid)
            _snapshot.value = next
            next.places.forEach { p ->
                if (p.enabled && p.ssidMatch(cur.currentSsid) != p.ssidMatch(ssid)) {
                    val n = DismissedNote(System.nanoTime(), STATE_PKG, if (p.ssidMatch(ssid)) "${p.name} Wi-Fi" else "離開 Wi-Fi", "${p.name} · ${ssid ?: cur.currentSsid ?: ""}", "", System.currentTimeMillis(), "")
                    val log = (listOf(n) + _snapshot.value.log).take(QuietSnapshot.LOG_LIMIT)
                    prefs.edit().putString(KEY_LOG, encodeLog(log)).apply()
                    _snapshot.value = _snapshot.value.copy(log = log)
                }
            }
            if (cur.wifiAlsoCounts && cur.wifiMatch != next.wifiMatch) {
                val n = DismissedNote(System.nanoTime(), STATE_PKG, if (next.wifiMatch) "住家 Wi-Fi" else "離開 Wi-Fi", "住家 · " + (ssid ?: ""), "", System.currentTimeMillis(), "")
                val log = (listOf(n) + _snapshot.value.log).take(QuietSnapshot.LOG_LIMIT)
                prefs.edit().putString(KEY_LOG, encodeLog(log)).apply()
                _snapshot.value = _snapshot.value.copy(log = log)
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

    private fun readSnapshot(context: Context): QuietSnapshot {
        val selected = if (!prefs.contains(KEY_PKGS)) {
            setOf(AppCatalog.pinPackage(context))
        } else {
            // Copy immediately. Mutating the set getStringSet returns does not persist.
            prefs.getStringSet(KEY_PKGS, emptySet())?.toSet() ?: emptySet()
        }
        val locationInside = prefs.getBoolean(KEY_INSIDE, false)
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
            geofenceInside = locationInside,
            fenceInside = if (prefs.contains(KEY_FENCE)) prefs.getBoolean(KEY_FENCE, false) else locationInside,
            playFence = prefs.getBoolean(KEY_PLAY, false),
            selectedPackages = selected,
            places = Place.decode(prefs.getString(KEY_PLACES, null)),
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
        private const val KEY_FENCE = "fence_inside"
        private const val KEY_PLAY = "play_fence"
        private const val KEY_SSIDS = "home_ssids"
        private const val KEY_LOGGED = "logged_inside"
        private const val KEY_PLACES = "places"
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
