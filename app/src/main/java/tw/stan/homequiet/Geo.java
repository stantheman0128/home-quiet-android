package tw.stan.homequiet;
import android.Manifest; import android.app.PendingIntent; import android.content.*; import android.content.pm.PackageManager;
import android.location.*; import android.os.Build;
import com.google.android.gms.common.*; import com.google.android.gms.location.*; import java.util.*;
public class Geo {
  static final String ID = "home";
  static boolean hasHome(Context c){ return Prefs.p(c).contains("lat"); }
  static double lat(Context c){ return Double.longBitsToDouble(Prefs.p(c).getLong("lat", 0)); }
  static double lng(Context c){ return Double.longBitsToDouble(Prefs.p(c).getLong("lng", 0)); }
  static int radius(Context c){ return Prefs.p(c).getInt("radius", 150); }
  static boolean ok(Context c){ return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
  static boolean playOk(Context c){ return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) == ConnectionResult.SUCCESS; }
  static PendingIntent pi(Context c, String action){
    int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    return PendingIntent.getBroadcast(c, action.hashCode(), new Intent(c, GeoReceiver.class).setAction(action), f); }
  static float distance(Context c, Location l){ float[] r = new float[1]; Location.distanceBetween(lat(c), lng(c), l.getLatitude(), l.getLongitude(), r); return r[0]; }
  /** Updates in-home state from a location fix (hysteresis 25 m on exit). */
  static void onFix(Context c, Location l){ if (!hasHome(c) || l == null) return;
    float d = distance(c, l); boolean was = Prefs.p(c).getBoolean("inhome", false);
    boolean in = was ? d <= radius(c) + 25 : d <= radius(c);
    Prefs.p(c).edit().putFloat("dist", d).putLong("distAt", System.currentTimeMillis()).apply(); setIn(c, in, "定位"); }
  static void setIn(Context c, boolean in, String src){ boolean was = Prefs.p(c).getBoolean("inhome", false);
    Prefs.p(c).edit().putBoolean("inhome", in).apply(); if (was != in) Prefs.log(c, (in ? "🏠 到家" : "🚶 離家") + "（" + src + "）"); }
  @SuppressWarnings("MissingPermission")
  static void register(Context c){ if (!hasHome(c) || !ok(c)) return;
    LocationManager lm = (LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
    try { lm.removeUpdates(pi(c, "fix")); } catch (Exception ignored) {}
    // Always: cheap passive + network updates as backup / distance display
    try { lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60_000, 20, pi(c, "fix")); } catch (Exception ignored) {}
    if (playOk(c)) {
      GeofencingClient g = LocationServices.getGeofencingClient(c);
      g.removeGeofences(Collections.singletonList(ID));
      Geofence f = new Geofence.Builder().setRequestId(ID).setCircularRegion(lat(c), lng(c), radius(c))
        .setExpirationDuration(Geofence.NEVER_EXPIRE).setNotificationResponsiveness(60_000)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT).build();
      GeofencingRequest r = new GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofence(f).build();
      g.addGeofences(r, pi(c, "geo")).addOnFailureListener(e -> Prefs.log(c, "地理圍欄註冊失敗：" + e.getMessage()));
      LocationServices.getFusedLocationProviderClient(c).getLastLocation().addOnSuccessListener(l -> onFix(c, l));
    } else {
      try { if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 120_000, 30, pi(c, "fix")); } catch (Exception ignored) {}
      try { onFix(c, lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)); } catch (Exception ignored) {}
    }
  }
}
