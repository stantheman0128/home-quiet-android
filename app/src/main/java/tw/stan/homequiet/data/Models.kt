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
    val geofenceInside: Boolean = false,
    /** Play Services geofence registered: its events are authoritative, no fix TTL. */
    val playFence: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
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
        get() = wifiAlsoCounts && currentSsid != null && currentSsid in homeSsids

    /** True only while the latest fix is fresh and inside the saved radius. */
    val insideFence: Boolean
        get() = geofenceInside && (playFence || fixFresh())

    val atHome: Boolean
        get() = insideFence || wifiMatch

    val presence: Presence
        get() = when {
            homeLat == null || homeLon == null -> Presence.NoHome
            atHome -> Presence.Home
            currentLat == null || fixTime == 0L -> Presence.NoFix
            playFence -> Presence.Away
            !fixFresh() -> Presence.Stale
            else -> Presence.Away
        }

    fun homeReason(): String? = when {
        !atHome -> null
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
    }
}
