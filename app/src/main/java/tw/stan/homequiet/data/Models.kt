package tw.stan.homequiet.data

enum class Presence {
    Home,
    Away,
    NoFix,
    Stale,
    NoHome,
}

data class DismissedNote(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val reason: String,
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val subtitle: String,
    val installed: Boolean,
    val pinned: Boolean,
)

data class PermissionState(
    val fine: Boolean = false,
    val coarse: Boolean = false,
    val background: Boolean = false,
    val listener: Boolean = false,
    val batteryIgnored: Boolean = false,
    val postNotifications: Boolean = false,
    val locationEnabled: Boolean = false,
)

data class QuietSnapshot(
    val onboardingDone: Boolean = false,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val radiusMeters: Int = DEFAULT_RADIUS,
    val paused: Boolean = false,
    val wifiAlsoCounts: Boolean = false,
    val homeSsid: String? = null,
    val homeSsids: Set<String> = emptySet(),
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val fixTime: Long = 0L,
    val currentSsid: String? = null,
    /** Location latch: last accepted GPS / network fix was inside the radius. */
    val geofenceInside: Boolean = false,
    /**
     * Play geofence latch. Null only for hand-built snapshots; then [geofenceInside]
     * stands in so older tests and callers keep working. A GPS fix must not clear this.
     */
    val fenceInside: Boolean? = null,
    /** Play Services geofence registered: its events are authoritative, no fix TTL. */
    val playFence: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    /** Extra quiet places (work, custom). Home stays in the fields above. */
    val places: List<Place> = emptyList(),
    val log: List<DismissedNote> = emptyList(),
) {
    fun distanceMeters(): Double? {
        val lat1 = homeLat ?: return null
        val lon1 = homeLon ?: return null
        val lat2 = currentLat ?: return null
        val lon2 = currentLon ?: return null
        return Geo.meters(lat1, lon1, lat2, lon2)
    }

    fun fixFresh(now: Long = System.currentTimeMillis()): Boolean {
        return fixTime > 0L && now - fixTime <= FIX_TTL_MS
    }

    val wifiMatch: Boolean
        get() {
            if (!wifiAlsoCounts) return false
            val current = currentSsid?.let { canonicalSsid(it) } ?: return false
            if (current.isEmpty()) return false
            return homeSsids.any { canonicalSsid(it) == current }
        }

    /**
     * Any one valid signal is enough: the Play geofence latch (no fix TTL),
     * a fresh location fix inside the radius, or a saved home Wi-Fi.
     * Signals are independent so a drifted GPS fix cannot erase the geofence.
     */
    val insideFence: Boolean
        get() {
            val fenceOn = fenceInside ?: geofenceInside
            val fence = playFence && fenceOn
            val fix = geofenceInside && fixFresh()
            return fence || fix
        }

    /** Home only (original v0.3.7 logic, unchanged). */
    val atHomeOnly: Boolean
        get() = insideFence || wifiMatch

    fun placeInside(p: Place): Boolean =
        p.enabled && (p.fenceInside || (p.fixInside && fixFresh()) || p.ssidMatch(currentSsid))

    /** Name of the place that makes us quiet right now, or null. Home wins. */
    val activePlace: String?
        get() = if (atHomeOnly) HOME_NAME else places.firstOrNull { placeInside(it) }?.name

    /** Quiet when inside ANY enabled place (home or extra). Kept as `atHome` so callers stay unchanged. */
    val atHome: Boolean
        get() = activePlace != null

    val presence: Presence
        get() = when {
            atHome -> Presence.Home
            (homeLat == null || homeLon == null) && places.none { it.enabled } -> Presence.NoHome
            currentLat == null || fixTime == 0L -> Presence.NoFix
            playFence -> Presence.Away
            !fixFresh() -> Presence.Stale
            else -> Presence.Away
        }

    fun homeReason(): String? = when {
        !atHome -> null
        !atHomeOnly -> places.firstOrNull { placeInside(it) }?.let { p ->
            if (p.ssidMatch(currentSsid) && !p.fenceInside && !p.fixInside) "${p.name} · Wi-Fi" else "在${p.name}範圍內"
        }
        insideFence && wifiMatch -> "在範圍內，也連上住家 Wi-Fi"
        wifiMatch -> "連上住家 Wi-Fi"
        insideFence -> "在住家範圍內"
        else -> null
    }

    companion object {
        const val FIX_TTL_MS = 20 * 60 * 1000L
        const val MIN_RADIUS = 50
        const val MAX_RADIUS = 500
        const val RADIUS_STEP = 10
        const val DEFAULT_RADIUS = 120
        const val EXIT_HYSTERESIS_M = 20.0
        const val EXIT_MARGIN_M = 60.0
        const val LOG_LIMIT = 100
        const val HOME_NAME = "住家"

        fun canonicalSsid(value: String): String {
            var v = value.trim()
            if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length - 1).trim()
            }
            return v
        }
    }
}
