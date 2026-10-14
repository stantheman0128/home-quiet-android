package tw.stan.homequiet;
import android.content.*; import android.net.wifi.*; import java.util.*; import java.text.*;
public class Prefs {
  static SharedPreferences p(Context c){ return c.getSharedPreferences("hq", 0); }
  static String home(Context c){ return p(c).getString("ssid", ""); }
  static boolean paused(Context c){ return p(c).getBoolean("paused", false); }
  static Set<String> pkgs(Context c){ return new HashSet<>(p(c).getStringSet("pkgs", new HashSet<>())); }
  static void setPkgs(Context c, Set<String> s){ p(c).edit().putStringSet("pkgs", s).putBoolean("init", true).apply(); }
  @SuppressWarnings("deprecation")
  static String currentSsid(Context c){
    try { WifiInfo i = ((WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo();
      if (i == null) return null; String s = i.getSSID();
      if (s == null || s.contains("unknown ssid")) return null;
      return s.replace("\"", ""); } catch (Exception e) { return null; }
  }
  static boolean wifiMatch(Context c){ String h = home(c), s = currentSsid(c); return !h.isEmpty() && h.equals(s); }
  static boolean useWifi(Context c){ return p(c).getBoolean("usewifi", false); }
  static boolean atHome(Context c){ return p(c).getBoolean("inhome", false) || (useWifi(c) && wifiMatch(c)); }
  static void log(Context c, String line){
    String t = new SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(new Date());
    String old = p(c).getString("log", ""); String n = t + "  " + line + "\n" + old;
    if (n.length() > 4000) n = n.substring(0, 4000);
    p(c).edit().putString("log", n).apply();
  }
}
