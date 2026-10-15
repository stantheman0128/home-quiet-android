package tw.stan.homequiet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                tw.stan.homequiet.data.AppCatalog.linkGrokBot(context)
                Geofencing.register(context, force = true)
                QuietNotificationListener.rebind(context)
            }
        }
    }
}
