package tw.stan.homequiet.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Permissions {
    const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
    const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
    const val BACKGROUND = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    fun read(context: Context): PermissionState {
        val fine = granted(context, FINE)
        val coarse = granted(context, COARSE)
        val background = granted(context, BACKGROUND)
        val listener = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        val power = context.getSystemService(PowerManager::class.java)
        val battery = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        val post = if (Build.VERSION.SDK_INT >= 33) {
            granted(context, POST_NOTIFICATIONS)
        } else {
            true
        }
        val locationEnabled = context.getSystemService(LocationManager::class.java)
            ?.isLocationEnabled == true
        return PermissionState(
            fine = fine,
            coarse = coarse,
            background = background,
            listener = listener,
            batteryIgnored = battery,
            postNotifications = post,
            locationEnabled = locationEnabled,
        )
    }

    fun hasFine(context: Context): Boolean = granted(context, FINE)

    private fun granted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
