package tw.stan.homequiet.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tw.stan.homequiet.MainActivity
import tw.stan.homequiet.R
import tw.stan.homequiet.data.LocationHelper
import tw.stan.homequiet.data.Permissions
import tw.stan.homequiet.data.Presence
import tw.stan.homequiet.data.QuietSnapshot
import tw.stan.homequiet.data.QuietStore
import tw.stan.homequiet.data.WifiHelper

object MonitorController {
    fun start(context: Context) {
        if (!Permissions.hasFine(context)) return
        val app = context.applicationContext
        val intent = Intent(app, MonitorService::class.java)
        Geofencing.register(app)
        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (_: Exception) {
            // Background start limits, or location permission raced away.
        }
    }
}

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var updatesOn = false
    private var foreground = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: android.location.Location) {
            val store = QuietStore.get(this@MonitorService)
            store.setFix(location.latitude, location.longitude, location.time)
            WifiHelper.currentSsid(this@MonitorService)?.let(store::setSsid)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    private val ssidTick = object : Runnable {
        override fun run() {
            WifiHelper.currentSsid(this@MonitorService)?.let { ssid ->
                QuietStore.get(this@MonitorService).setSsid(ssid)
            }
            handler.postDelayed(this, SSID_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val store = QuietStore.get(this)
        promote(store.snapshot.value)
        scope.launch {
            store.snapshot.collect { snap ->
                if (foreground) promote(snap)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val snap = QuietStore.get(this).snapshot.value
        promote(snap)
        if (!Permissions.hasFine(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        beginUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        endUpdates()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        MonitorController.start(applicationContext)
    }

    private fun promote(snap: QuietSnapshot) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        val notification = buildNotification(snap)
        try {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            foreground = true
        } catch (_: SecurityException) {
            foreground = false
            stopSelf()
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "運作狀態", NotificationManager.IMPORTANCE_LOW).apply {
            description = "維持定位，用來判斷是否在家。不會發聲。"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(snap: QuietSnapshot): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("在家安靜")
            .setContentText(statusText(snap))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun statusText(snap: QuietSnapshot): String = when {
        snap.paused -> "已暫停，通知維持正常"
        snap.homeLat == null -> "尚未設定住家"
        snap.atHome && snap.wifiMatch && !snap.insideFence -> "目前在家（Wi-Fi），正在清除所選通知"
        snap.atHome -> "目前在家，正在清除所選通知"
        snap.presence == Presence.Stale || snap.presence == Presence.NoFix -> "正在確認你是否在家"
        else -> "目前外出，通知維持正常"
    }

    @SuppressLint("MissingPermission")
    private fun beginUpdates() {
        if (updatesOn) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        updatesOn = true
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        for (provider in providers) {
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MS,
                    UPDATE_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper(),
                )
            }
        }
        LocationHelper.lastKnown(this)?.let { fix ->
            QuietStore.get(this).setFix(fix.latitude, fix.longitude, fix.time)
        }
        WifiHelper.currentSsid(this)?.let { QuietStore.get(this).setSsid(it) }
        handler.post(ssidTick)
    }

    private fun endUpdates() {
        if (!updatesOn) return
        val manager = getSystemService(LocationManager::class.java)
        if (manager != null) runCatching { manager.removeUpdates(locationListener) }
        handler.removeCallbacks(ssidTick)
        updatesOn = false
    }

    companion object {
        private const val CHANNEL_ID = "homequiet.monitor"
        private const val NOTIF_ID = 7101
        private const val UPDATE_INTERVAL_MS = 15_000L
        private const val UPDATE_DISTANCE_M = 20f
        private const val SSID_INTERVAL_MS = 20_000L
    }
}
