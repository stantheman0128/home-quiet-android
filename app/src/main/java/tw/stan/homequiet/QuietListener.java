package tw.stan.homequiet;
import android.service.notification.*; import android.app.Notification;
public class QuietListener extends NotificationListenerService {
  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    if (Prefs.paused(this) || !Prefs.pkgs(this).contains(sbn.getPackageName())) return;
    if (!Prefs.atHome(this)) return;
    if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) { cancelNotification(sbn.getKey()); return; }
    cancelNotification(sbn.getKey());
    CharSequence title = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
    Prefs.log(this, "已收起 " + sbn.getPackageName() + (title != null ? "：" + title : ""));
  }
}
