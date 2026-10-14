package tw.stan.homequiet;
import android.Manifest; import android.app.Activity; import android.content.*; import android.content.pm.*; import android.net.Uri;
import android.os.*; import android.provider.Settings; import android.view.*; import android.widget.*;
import com.google.android.material.button.MaterialButton; import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder; import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider; import androidx.appcompat.app.AppCompatActivity; import java.util.*;
public class MainActivity extends AppCompatActivity {
  LinearLayout root; TextView status, homeTv, geoTv, radTv, appsTv, logTv; MaterialButton bNotif, bLoc, bBg;
  int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density); }
  MaterialCardView card(String title){ MaterialCardView c = new MaterialCardView(this);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(12)); c.setLayoutParams(lp);
    c.setRadius(dp(20)); LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18), dp(16), dp(18), dp(16));
    TextView t = new TextView(this); t.setText(title); t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium); l.addView(t);
    c.addView(l); root.addView(c); return c; }
  LinearLayout body(MaterialCardView c){ return (LinearLayout)c.getChildAt(0); }
  TextView text(LinearLayout l){ TextView t = new TextView(this); t.setPadding(0, dp(6), 0, dp(6)); l.addView(t); return t; }
  MaterialButton btn(LinearLayout l, String s, View.OnClickListener o){ MaterialButton b = new MaterialButton(this); b.setText(s); b.setOnClickListener(o); l.addView(b); return b; }
  @Override protected void onCreate(Bundle s){ super.onCreate(s);
    ScrollView sv = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(40), dp(16), dp(24)); sv.addView(root);
    TextView h = new TextView(this); h.setText("在家安靜"); h.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium); h.setPadding(dp(4),0,0,dp(12)); root.addView(h);
    LinearLayout c1 = body(card("目前狀態")); status = text(c1);
    MaterialSwitch sw = new MaterialSwitch(this); sw.setText("暫停（通知照常顯示）"); sw.setChecked(Prefs.paused(this));
    sw.setOnCheckedChangeListener((v, b) -> { Prefs.p(this).edit().putBoolean("paused", b).apply(); refresh(); }); c1.addView(sw);
    LinearLayout c2 = body(card("權限設定"));
    bNotif = btn(c2, "", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    bLoc = btn(c2, "", v -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1));
    bBg = btn(c2, "", v -> { if (Build.VERSION.SDK_INT >= 30) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
      else requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2); });
    LinearLayout cg = body(card("家的位置")); geoTv = text(cg);
    btn(cg, "把目前位置設為家", v -> saveHere());
    radTv = text(cg); Slider sl = new Slider(this); sl.setValueFrom(50); sl.setValueTo(500); sl.setStepSize(25); sl.setValue(Geo.radius(this));
    sl.addOnChangeListener((x, val, user) -> { Prefs.p(this).edit().putInt("radius", (int)val).apply(); radTv.setText("範圍半徑：" + (int)val + " 公尺"); });
    sl.addOnSliderTouchListener(new Slider.OnSliderTouchListener(){ public void onStartTrackingTouch(Slider x){} public void onStopTrackingTouch(Slider x){ Geo.register(MainActivity.this); refresh(); } });
    cg.addView(sl);
    LinearLayout c3 = body(card("Wi-Fi 額外條件（選用）")); homeTv = text(c3);
    MaterialSwitch ws = new MaterialSwitch(this); ws.setText("連上家裡 Wi-Fi 也算在家"); ws.setChecked(Prefs.useWifi(this));
    ws.setOnCheckedChangeListener((v, b) -> { Prefs.p(this).edit().putBoolean("usewifi", b).apply(); refresh(); }); c3.addView(ws);
    btn(c3, "把目前的 Wi-Fi 設為家", v -> { String ss = Prefs.currentSsid(this);
      if (ss == null) { Toast.makeText(this, "讀不到 Wi-Fi 名稱：請先連上 Wi-Fi、開啟定位並授權位置", Toast.LENGTH_LONG).show(); return; }
      Prefs.p(this).edit().putString("ssid", ss).apply(); refresh(); });
    LinearLayout c4 = body(card("要靜音的 App")); appsTv = text(c4); btn(c4, "選擇 App", v -> pickApps());
    LinearLayout c5 = body(card("最近收起的通知")); logTv = text(c5);
    btn(c5, "清除紀錄", v -> { Prefs.p(this).edit().putString("log", "").apply(); refresh(); });
    setContentView(sv);
    if (!Prefs.p(this).getBoolean("init", false)) autoPickGrok();
    Geo.register(this);
  }
  List<ApplicationInfo> launchable(){ PackageManager pm = getPackageManager(); Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    Map<String, ApplicationInfo> m = new TreeMap<>(); for (ResolveInfo r : pm.queryIntentActivities(i, 0)) m.put(r.activityInfo.applicationInfo.loadLabel(pm) + "|" + r.activityInfo.packageName, r.activityInfo.applicationInfo);
    return new ArrayList<>(m.values()); }
  void autoPickGrok(){ PackageManager pm = getPackageManager(); Set<String> s = new HashSet<>();
    for (ApplicationInfo a : launchable()) { String lb = a.loadLabel(pm).toString().toLowerCase(); String pk = a.packageName.toLowerCase();
      if (lb.contains("grok bot") || lb.contains("grokbot") || pk.contains("grokbot")) s.add(a.packageName); }
    if (!s.isEmpty()) Prefs.setPkgs(this, s); }
  void pickApps(){ PackageManager pm = getPackageManager(); List<ApplicationInfo> apps = launchable(); Set<String> sel = Prefs.pkgs(this);
    String[] names = new String[apps.size()]; boolean[] chk = new boolean[apps.size()];
    for (int i = 0; i < apps.size(); i++) { names[i] = apps.get(i).loadLabel(pm) + "\n" + apps.get(i).packageName; chk[i] = sel.contains(apps.get(i).packageName); }
    new MaterialAlertDialogBuilder(this).setTitle("在家時要收起哪些 App 的通知")
      .setMultiChoiceItems(names, chk, (d, w, b) -> chk[w] = b)
      .setPositiveButton("儲存", (d, w) -> { Set<String> n = new HashSet<>(); for (int i = 0; i < chk.length; i++) if (chk[i]) n.add(apps.get(i).packageName); Prefs.setPkgs(this, n); refresh(); })
      .setNegativeButton("取消", null).show(); }
  @SuppressWarnings("MissingPermission")
  void saveHere(){ if (!Geo.ok(this)) { Toast.makeText(this, "請先允許位置權限", Toast.LENGTH_LONG).show(); return; }
    Toast.makeText(this, "定位中…", Toast.LENGTH_SHORT).show();
    if (Geo.playOk(this)) { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener(l -> setHome(l)).addOnFailureListener(e -> setHome(null)); }
    else { android.location.LocationManager lm = (android.location.LocationManager)getSystemService(LOCATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null, getMainExecutor(), this::setHome);
      else setHome(lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)); } }
  void setHome(android.location.Location l){ if (l == null) { Toast.makeText(this, "定位失敗，請確認定位已開啟後再試", Toast.LENGTH_LONG).show(); return; }
    Prefs.p(this).edit().putLong("lat", Double.doubleToRawLongBits(l.getLatitude())).putLong("lng", Double.doubleToRawLongBits(l.getLongitude())).putBoolean("inhome", true).putFloat("dist", 0f).putLong("distAt", System.currentTimeMillis()).apply();
    Prefs.log(this, "🏠 已設定家的位置（精準度約 " + (int)l.getAccuracy() + " 公尺）"); Geo.register(this); refresh(); }
  boolean notifOk(){ String f = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners"); return f != null && f.contains(getPackageName()); }
  boolean has(String p){ return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }
  void refresh(){ boolean n = notifOk(), l = has(Manifest.permission.ACCESS_FINE_LOCATION), b = has(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    bNotif.setText(n ? "✓ 通知存取已開啟" : "1. 開啟通知存取"); bLoc.setText(l ? "✓ 位置權限已允許" : "2. 允許位置（精確位置）");
    bBg.setText(b ? "✓ 背景位置已允許" : "3. 位置改成「一律允許」"); bBg.setEnabled(l);
    String cur = Prefs.currentSsid(this), home = Prefs.home(this);
    radTv.setText("範圍半徑：" + Geo.radius(this) + " 公尺");
    String g = Geo.hasHome(this) ? "已設定" : "尚未設定";
    if (Prefs.p(this).contains("distAt")) { long m = (System.currentTimeMillis() - Prefs.p(this).getLong("distAt", 0)) / 60000;
      g += "\n距離家約 " + (int)Prefs.p(this).getFloat("dist", 0) + " 公尺（" + (m < 1 ? "剛剛" : m + " 分鐘前") + "）"; }
    g += "\n偵測方式：" + (Geo.playOk(this) ? "Google 地理圍欄" : "系統定位（無 Play 服務）"); geoTv.setText(g);
    homeTv.setText("家：" + (home.isEmpty() ? "尚未設定" : home) + "\n現在：" + (cur == null ? "讀不到（未連 Wi-Fi 或缺權限）" : cur));
    PackageManager pm = getPackageManager(); StringBuilder sb = new StringBuilder();
    for (String p : Prefs.pkgs(this)) { try { sb.append("• ").append(pm.getApplicationLabel(pm.getApplicationInfo(p, 0))).append("  ").append(p).append("\n"); } catch (Exception e) { sb.append("• ").append(p).append("\n"); } }
    appsTv.setText(sb.length() == 0 ? "還沒選（找不到 Grok Bot，請手動選）" : sb.toString().trim());
    String st; if (!n || !l || !b) st = "⚠️ 權限還沒開齊"; else if (Prefs.paused(this)) st = "⏸ 已暫停"; else if (Prefs.atHome(this)) st = "🏠 在家：收起選定 App 的通知"; else if (!Geo.hasHome(this)) st = "📍 還沒設定家的位置"; else st = "🚶 不在家：通知照常";
    status.setText(st); String lg = Prefs.p(this).getString("log", ""); logTv.setText(lg.isEmpty() ? "目前沒有" : lg.trim()); }
  @Override protected void onResume(){ super.onResume(); refresh(); }
  @Override public void onRequestPermissionsResult(int r, String[] p, int[] g){ super.onRequestPermissionsResult(r, p, g); Geo.register(this); refresh(); }
}
