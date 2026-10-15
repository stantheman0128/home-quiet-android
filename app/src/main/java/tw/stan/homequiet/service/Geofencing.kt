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

    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = Intent(context, GeofenceReceiver::class.java).setAction("tw.stan.homequiet.GEOFENCE")
        return PendingIntent.getBroadcast(context, 7, intent, flags)
    }

    @SuppressLint("MissingPermission")
    fun register(context: Context) {
        val app = context.applicationContext
        val snap = QuietStore.get(app).snapshot.value
        val lat = snap.homeLat
        val lon = snap.homeLon
        if (lat == null || lon == null || !Permissions.hasFine(app)) {
            setMode(app, "off"); return
        }
        if (!playAvailable(app)) {
            setMode(app, "fallback"); return
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
                    .addOnSuccessListener { setMode(app, "play") }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "geofence add failed; LocationManager fallback", e)
                        setMode(app, "fallback")
                    }
            }
        }.onFailure { setMode(app, "fallback") }
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
