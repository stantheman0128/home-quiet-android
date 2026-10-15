package tw.stan.homequiet.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A quiet place other than home (home keeps its original, battle-tested fields in [QuietSnapshot]).
 * Quiet when inside ANY enabled place. [fenceInside] is the Play geofence latch (no TTL),
 * [fixInside] is the last location decision (only trusted while the fix is fresh).
 */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radius: Int = QuietSnapshot.DEFAULT_RADIUS,
    val enabled: Boolean = true,
    val ssids: Set<String> = emptySet(),
    val fenceInside: Boolean = false,
    val fixInside: Boolean = false,
) {
    fun ssidMatch(current: String?): Boolean {
        val c = current?.let { QuietSnapshot.canonicalSsid(it) }?.takeIf { it.isNotEmpty() } ?: return false
        return ssids.any { QuietSnapshot.canonicalSsid(it) == c }
    }

    fun distanceTo(lat2: Double, lon2: Double): Double = Geo.meters(lat, lon, lat2, lon2)

    companion object {
        const val GEOFENCE_PREFIX = "place:"

        fun encode(list: List<Place>): String = JSONArray().apply {
            list.forEach { p ->
                put(
                    JSONObject().put("id", p.id).put("name", p.name).put("lat", p.lat).put("lon", p.lon)
                        .put("r", p.radius).put("on", p.enabled).put("ssids", JSONArray(p.ssids.toList()))
                        .put("fence", p.fenceInside).put("fix", p.fixInside),
                )
            }
        }.toString()

        fun decode(raw: String?): List<Place> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val a = JSONArray(raw)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    val s = o.optJSONArray("ssids")
                    Place(
                        id = o.getString("id"), name = o.optString("name", "地點"),
                        lat = o.getDouble("lat"), lon = o.getDouble("lon"),
                        radius = o.optInt("r", QuietSnapshot.DEFAULT_RADIUS),
                        enabled = o.optBoolean("on", true),
                        ssids = if (s == null) emptySet() else (0 until s.length()).map { s.getString(it) }.toSet(),
                        fenceInside = o.optBoolean("fence", false), fixInside = o.optBoolean("fix", false),
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}

/** Bearing in degrees (0 = north, clockwise) from point 1 to point 2. */
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
    val y = Math.sin(dl) * Math.cos(p2)
    val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

fun compassLabel(deg: Double): String = arrayOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")[(((deg + 22.5) % 360) / 45).toInt()]
