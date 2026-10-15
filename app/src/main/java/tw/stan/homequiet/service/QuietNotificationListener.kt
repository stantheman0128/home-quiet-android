package tw.stan.homequiet.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tw.stan.homequiet.data.AppCatalog
import tw.stan.homequiet.data.DismissedNote
import tw.stan.homequiet.data.LocationHelper
import tw.stan.homequiet.data.QuietStore
import tw.stan.homequiet.data.WifiHelper

/**
 * Clears notifications from the selected apps while the device is inside the
 * home geofence, or on the remembered home Wi-Fi when that option is on.
 * Leaving home leaves notifications alone.
 */
class QuietNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var connected = false
    private var sweeping = false
    private val cancelAt = HashMap<String, Long>()
    private val loggedAt = HashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        collectJob?.cancel()
        val store = QuietStore.get(this)
        collectJob = scope.launch {
            var previous: Set<String> = emptySet()
            store.snapshot.collect { snap ->
                val key = if (snap.atHome && !snap.paused) snap.selectedPackages else emptySet()
                if (key.isNotEmpty() && key != previous) sweep()
                previous = key
            }
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        // Ask the system to bind us again (happens after updates / memory pressure).
        runCatching { requestRebind(android.content.ComponentName(this, QuietNotificationListener::class.java)) }
        collectJob?.cancel()
        collectJob = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        connected = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        try {
            handle(sbn)
        } catch (error: Throwable) {
            Log.e(TAG, "dismiss failed", error)
        }
    }

    private fun sweep() {
        if (sweeping || !connected) return
        sweeping = true
        try {
            val active = runCatching { activeNotifications }.getOrNull() ?: return
            for (sbn in active) {
                runCatching { handle(sbn) }
            }
        } finally {
            sweeping = false
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        refreshSignals()
        val store = QuietStore.get(this)
        val snap = store.snapshot.value
        if (sbn.packageName !in snap.selectedPackages) {
            if (sbn.packageName.contains("grok", true) && shouldLog("unsel:" + sbn.packageName, false))
                store.note("未選取", sbn.packageName)
            return
        }
        if (snap.paused || !snap.atHome) {
            // Visible decision so flakiness can be diagnosed from the Log tab.
            if (shouldLog("pass:" + sbn.packageName, false)) {
                val why = when {
                    snap.paused -> "安靜模式關閉"
                    else -> "判斷為外出" + (snap.distanceMeters()?.let { " · ${it.toInt()} m" } ?: "") +
                        (snap.currentSsid?.let { " · Wi-Fi $it" } ?: "")
                }
                store.note("未清除 ${AppCatalog.labelOf(this, sbn.packageName)}", why)
            }
            return
        }
        // Always cancel, including rapid updates to the same key and group summaries.
        tooSoon(sbn.key)
        runCatching { cancelNotification(sbn.key) }
        Log.i(TAG, "cancel ${sbn.packageName}")
        val summary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (summary || !shouldLog(sbn.key, sbn.isOngoing)) return
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val reason = when {
            snap.insideFence && snap.wifiMatch -> "在家範圍內 · 住家 Wi-Fi"
            snap.wifiMatch && !snap.insideFence -> "住家 Wi-Fi"
            else -> "在家範圍內"
        }
        store.appendDismissed(
            DismissedNote(
                id = System.nanoTime(),
                packageName = sbn.packageName,
                appLabel = AppCatalog.labelOf(this, sbn.packageName),
                title = clip(title.ifBlank { "（無標題）" }),
                text = text.trim().take(4000),
                timestamp = System.currentTimeMillis(),
                reason = reason,
            ),
        )
    }

    private fun refreshSignals() {
        val store = QuietStore.get(this)
        LocationHelper.lastKnown(this)?.let { fix ->
            store.setFix(fix.latitude, fix.longitude, fix.time, "通知時定位", fix.accuracy)
        }
        store.setSsid(WifiHelper.currentSsid(this))
    }

    private fun tooSoon(key: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = cancelAt[key]
        if (previous != null && now - previous < CANCEL_GAP_MS) return true
        cancelAt[key] = now
        if (cancelAt.size > 300) {
            val cutoff = now - 60_000L
            cancelAt.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    private fun shouldLog(key: String, ongoing: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val window = if (ongoing) ONGOING_LOG_GAP_MS else LOG_GAP_MS
        val previous = loggedAt[key]
        if (previous != null && now - previous < window) return false
        loggedAt[key] = now
        if (loggedAt.size > 300) {
            val cutoff = now - ONGOING_LOG_GAP_MS
            loggedAt.entries.removeIf { it.value < cutoff }
        }
        return true
    }

    private fun clip(value: String): String {
        val clean = value.replace("\\s+".toRegex(), " ").trim()
        return if (clean.length <= 140) clean else clean.take(140) + "…"
    }

    companion object {
        private const val TAG = "HomeQuiet"
        private const val CANCEL_GAP_MS = 2_000L
        private const val LOG_GAP_MS = 30_000L
        private const val ONGOING_LOG_GAP_MS = 5 * 60_000L
    }
}
