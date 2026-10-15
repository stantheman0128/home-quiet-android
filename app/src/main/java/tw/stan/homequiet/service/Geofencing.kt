package tw.stan.homequiet.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import tw.stan.homequiet.data.Permissions
import tw.stan.homequiet.data.QuietStore

/**
 * Google Play Services geofence around home (ported from v0.2.0).
 * When Play Services is missing or registration fails, MonitorService's
 * LocationManager updates (GPS / network / passive) keep deciding presence on their own.
 */
object Geofencing {
    private const val TAG = "HomeQuiet"
    private const val ID = "home"
    private const val PREFS = "geofence"

    fun playAvailable(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /** "play" = geofence registered, "fallback" = LocationManager only, "off" = no home or no permission. */
    fun mode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", "off") ?: "off"

    private fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode).apply()
    }

    /** One-shot location: Fused getCurrentLocation, else LocationManager last known. */
    @SuppressLint("MissingPermission")
    fun seed(app: Context) {
        val store = QuietStore.get(app)
        val lastKnown = { tw.stan.homequiet.data.LocationHelper.lastKnown(app)?.let { store.setFix(it.latitude, it.longitude, it.time, "定位", it.accuracy) } }
        if (!Permissions.hasFine(app)) return
        if (!playAvailable(app)) { lastKnown(); return }
        runCatching {
            val cts = com.google.android.gms.tasks.CancellationTokenSource()
            LocationServices.getFusedLocationProviderClient(app)
                .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) store.setFix(loc.latitude, loc.longitude, System.currentTimeMillis(), "即時定位", loc.accuracy) else lastKnown()
                }
                .addOnFailureListener { lastKnown() }
        }.onFailure { lastKnown() }
    }

    private fun fallback(app: Context) {
        setMode(app, "fallback"); setPlayFence(app, false); MonitorController.startFallback(app)
    }

    private fun setPlayFence(app: Context, on: Boolean) = QuietStore.get(app).setPlayFence(on)

    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = Intent(context, GeofenceReceiver::class.java).setAction("tw.stan.homequiet.GEOFENCE")
        return PendingIntent.getBroadcast(context, 7, intent, flags)
    }

    @SuppressLint("MissingPermission")
    fun register(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val snap = QuietStore.get(app).snapshot.value
        val lat = snap.homeLat
        val lon = snap.homeLon
        if (lat == null || lon == null || !Permissions.hasFine(app)) {
            setMode(app, "off"); setPlayFence(app, false); MonitorController.stop(app); return
        }
        if (!playAvailable(app)) {
            fallback(app); return
        }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "$lat,$lon,${snap.radiusMeters}"
        // Always seed home/away from a one-shot current location, so we never wait on an ENTER
        // that won't come when the user is already inside.
        seed(app)
        if (!force && mode(app) == "play" && prefs.getString("key", null) == key) {
            MonitorController.stop(app); return // already registered for this home/radius
        }
        runCatching {
            val client = LocationServices.getGeofencingClient(app)
            val fence = Geofence.Builder()
                .setRequestId(ID)
                .setCircularRegion(lat, lon, snap.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setNotificationResponsiveness(60_000)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(fence)
                .build()
            client.removeGeofences(listOf(ID)).addOnCompleteListener {
                client.addGeofences(request, pendingIntent(app))
                    .addOnSuccessListener { prefs.edit().putString("key", key).apply(); setMode(app, "play"); setPlayFence(app, true); MonitorController.stop(app) }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "geofence add failed; LocationManager fallback", e)
                        fallback(app)
                    }
            }
        }.onFailure { fallback(app) }
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val inside = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }
        val loc = event.triggeringLocation
        QuietStore.get(context).setFenceEvent(inside, loc?.latitude, loc?.longitude, System.currentTimeMillis())
    }
}
