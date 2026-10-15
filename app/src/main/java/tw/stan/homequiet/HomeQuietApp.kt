package tw.stan.homequiet

import android.app.Application
import tw.stan.homequiet.data.QuietStore

class HomeQuietApp : Application() {
    override fun onCreate() {
        super.onCreate()
        QuietStore.get(this)
        // v0.3.0 used a visible "運作狀態" channel; remove it.
        runCatching { getSystemService(android.app.NotificationManager::class.java)?.deleteNotificationChannel("homequiet.monitor") }
    }
}
