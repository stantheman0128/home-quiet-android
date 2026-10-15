package tw.stan.homequiet.data

/** Pure home/away decision for one location fix. Null means "ignore this fix". */
object HomeSignals {
    /** A fix this far outside, with a tight accuracy, is not indoor drift. */
    const val HARD_EXIT_M = 2_000.0

    fun locationInside(
        wasInside: Boolean,
        distanceMeters: Double,
        accuracyMeters: Float,
        radiusMeters: Int,
    ): Boolean? {
        if (accuracyMeters > 1_500f) return null
        val poor = accuracyMeters > maxOf(radiusMeters * 2f, 300f)
        if (poor) {
            // A coarse point centered inside the radius is a positive signal only.
            return if (distanceMeters <= radiusMeters) true else null
        }
        val inside = if (wasInside) {
            distanceMeters - accuracyMeters <= radiusMeters + QuietSnapshot.EXIT_MARGIN_M
        } else {
            distanceMeters <= radiusMeters.toDouble()
        }
        return inside
    }

    fun hardExit(distanceMeters: Double, accuracyMeters: Float): Boolean {
        if (accuracyMeters <= 0f || accuracyMeters > 100f) return false
        return distanceMeters - accuracyMeters > HARD_EXIT_M
    }
}
