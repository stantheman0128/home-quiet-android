package tw.stan.homequiet.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import tw.stan.homequiet.service.QuietNotificationListener

object Intents {
    fun openNotificationListener(context: Context) {
        val component = ComponentName(context, QuietNotificationListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        }
        if (tryStart(context, detail)) return
        if (tryStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return
        toastUnavailable(context)
    }

    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        if (!tryStart(context, intent)) toastUnavailable(context)
    }

    fun openBattery(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (tryStart(context, request)) return
        if (tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        toastUnavailable(context)
    }

    fun openLocationServices(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) {
            toastUnavailable(context)
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun toastUnavailable(context: Context) {
        Toast.makeText(context, "這支手機沒有對應的設定頁", Toast.LENGTH_SHORT).show()
    }
}
