package tw.stan.homequiet;
import android.content.*; import android.location.*; import com.google.android.gms.location.*;
public class GeoReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i){
    if ("geo".equals(i.getAction())) { GeofencingEvent e = GeofencingEvent.fromIntent(i);
      if (e == null || e.hasError()) return; int t = e.getGeofenceTransition();
      if (t == Geofence.GEOFENCE_TRANSITION_ENTER) Geo.setIn(c, true, "地理圍欄");
      else if (t == Geofence.GEOFENCE_TRANSITION_EXIT) Geo.setIn(c, false, "地理圍欄");
      if (e.getTriggeringLocation() != null) Prefs.p(c).edit().putFloat("dist", Geo.distance(c, e.getTriggeringLocation())).putLong("distAt", System.currentTimeMillis()).apply();
    } else { Location l = i.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED); if (l != null) Geo.onFix(c, l); }
  }
}
