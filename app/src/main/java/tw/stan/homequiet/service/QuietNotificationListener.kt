package tw.stan.homequiet.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.stan.homequiet.data.AppCatalog
import tw.stan.homequiet.data.DismissedNote
import tw.stan.homequiet.data.LocationHelper
import tw.stan.homequiet.data.PackageMatch
import tw.stan.homequiet.data.QuietStore
import tw.stan.homequiet.data.WifiHelper

/**
 * Clears selected apps' notifications while quiet mode is on and any home signal
 * is valid (Play geofence, a fresh fix inside the radius, or a saved home Wi-Fi).
 * Grok Bot posts as ai.x.grok.bot; builds before 0.3.7 stored the "Grok Bot" pin as ai.x.grok.
 */
class QuietNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var bound = false
    private var sweeping = false
    private val cancelAt = HashMap<String, Long>()
    private val loggedAt = HashMap<String, Long>()
    private val labels = HashMap<String, String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        bound = true
        refreshHome()
        // Notifications posted while we were unbound are still in the shade.
        sweepActive()
        collectJob?.cancel()
        val store = QuietStore.get(this)
        collectJob = scope.launch {
            var previous = emptySet<String>()
            store.snapshot.collect { snap ->
                val key = if (snap.atHome && !snap.paused) snap.selectedPackages else emptySet()
                if (key.isNotEmpty() && key != previous) sweepActive()
                previous = key
            }
        }
        if (shouldLog("listener-connected", ongoing = true)) store.note("監聽已連線", "")
    }

    override fun onListenerDisconnected() {
        bound = false
        // The system only rebinds a listener that asks. Updates and low-memory kills land here.
        runCatching { requestRebind(ComponentName(this, QuietNotificationListener::class.java)) }
        collectJob?.cancel()
        collectJob = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        bound = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn, refresh = true)
        } catch (error: Throwable) {
            Log.e(TAG, "dismiss failed", error)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        onNotificationPosted(sbn)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        val snap = QuietStore.get(this).snapshot.value
        if (snap.paused || !snap.atHome) return
        val now = System.currentTimeMillis()
        if (now - lastRankingSweep < RANKING_SWEEP_MS) return
        lastRankingSweep = now
        sweepActive()
    }

    private fun sweepActive() {
        if (sweeping || !bound) return
        sweeping = true
        try {
            val active = runCatching { getActiveNotifications() }.getOrNull() ?: return
            for (sbn in active) {
                runCatching { handle(sbn, refresh = false) }
            }
        } finally {
            sweeping = false
        }
    }

    private fun handle(sbn: StatusBarNotification, refresh: Boolean) {
        if (sbn.packageName == packageName) return
        val store = QuietStore.get(this)
        val before = store.snapshot.value
        val wasHome = !before.paused && before.atHome
        if (refresh) refreshHome()
        val snap = store.snapshot.value
        val alias = AppCatalog.aliasLegacyGrok(this)
        var matched = PackageMatch.blocks(sbn.packageName, snap.selectedPackages, alias)
        val label = if (!matched && (alias || sbn.packageName.contains("grok", ignoreCase = true))) {
            cachedLabel(sbn.packageName).also { resolved ->
                if (!matched) matched = PackageMatch.blocks(sbn.packageName, snap.selectedPackages, alias, resolved)
            }
        } else {
            null
        }
        val home = !snap.paused && (snap.atHome || wasHome)
        if (!matched || !home) {
            if (!matched && sbn.packageName.contains("grok", ignoreCase = true) &&
                shouldLog("unsel:" + sbn.packageName, false)
            ) {
                store.note("未選取", sbn.packageName)
            } else if (matched && shouldLog("pass:" + sbn.packageName, false)) {
                val why = when {
                    snap.paused -> "安靜模式關閉"
                    else -> "判斷為外出" + (snap.distanceMeters()?.let { " · ${it.toInt()} m" } ?: "") +
                        (snap.currentSsid?.let { " · Wi-Fi $it" } ?: "")
                }
                store.note("未清除 ${label ?: sbn.packageName}", why)
            }
            return
        }
        val snoozed = suppress(sbn)
        Log.i(TAG, "cancel ${sbn.packageName} snooze=$snoozed")
        val summary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (summary || !shouldLog(sbn.key, sbn.isOngoing)) return
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val reason = when {
            snap.insideFence && snap.wifiMatch -> "在家範圍內 · 住家 Wi-Fi"
            snap.wifiMatch && !snap.insideFence -> "住家 Wi-Fi"
            snoozed -> "在家範圍內 · 已暫停"
            else -> "在家範圍內"
        }
        store.appendDismissed(
            DismissedNote(
                id = System.nanoTime(),
                packageName = sbn.packageName,
                appLabel = label ?: cachedLabel(sbn.packageName),
                title = clip(title.ifBlank { "（無標題）" }),
                text = text.trim().take(4000),
                timestamp = System.currentTimeMillis(),
                reason = reason,
            ),
        )
    }

    /**
     * @return true when the notification was still up after cancel and we snoozed it.
     * Cancel returns void even when the app reposts; the follow-up read is the check.
     */
    @Suppress("DEPRECATION")
    private fun suppress(sbn: StatusBarNotification): Boolean {
        val now = System.currentTimeMillis()
        val previous = cancelAt[sbn.key]
        cancelAt[sbn.key] = now
        trim(cancelAt, now)
        val group = sbn.notification.group
        if (!group.isNullOrEmpty()) {
            runCatching { getActiveNotifications() }.getOrNull()?.forEach { other ->
                if (other.key != sbn.key && other.packageName == sbn.packageName && other.notification.group == group) {
                    runCatching { cancelNotification(other.key) }
                }
            }
        }
        if (previous != null && now - previous < REPOST_MS) {
            snooze(sbn.key)
            return true
        }
        val cancelled = runCatching {
            cancelNotification(sbn.key)
            true
        }.getOrDefault(false)
        if (!cancelled) {
            val legacy = runCatching {
                cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                true
            }.getOrDefault(false)
            if (!legacy) {
                snooze(sbn.key)
                return true
            }
        }
        val key = sbn.key
        val pkg = sbn.packageName
        val tag = sbn.tag
        val id = sbn.id
        scope.launch {
            delay(VERIFY_MS)
            if (!bound) return@launch
            val live = runCatching { getActiveNotifications() }.getOrNull()?.any { it.key == key } == true
            if (!live) return@launch
            runCatching { cancelNotification(pkg, tag, id) }
            snooze(key)
        }
        return false
    }

    private fun snooze(key: String) {
        runCatching { snoozeNotification(key, SNOOZE_MS) }
            .onFailure { Log.w(TAG, "snooze failed $key", it) }
    }

    private fun refreshHome() {
        val store = QuietStore.get(this)
        LocationHelper.lastKnown(this)?.let { fix ->
            store.setFix(fix.latitude, fix.longitude, fix.time, "通知時定位", fix.accuracy)
        }
        val wifi = WifiHelper.read(this)
        store.applyWifi(wifi.connected, wifi.ssid)
    }

    private fun trim(map: HashMap<String, Long>, now: Long) {
        if (map.size <= 300) return
        val cutoff = now - ONGOING_LOG_GAP_MS
        map.entries.removeIf { it.value < cutoff }
    }

    private fun shouldLog(key: String, ongoing: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val window = if (ongoing) ONGOING_LOG_GAP_MS else LOG_GAP_MS
        val previous = loggedAt[key]
        if (previous != null && now - previous < window) return false
        loggedAt[key] = now
        trim(loggedAt, now)
        return true
    }

    private fun cachedLabel(packageName: String): String {
        labels[packageName]?.let { return it }
        val resolved = AppCatalog.labelOf(this, packageName)
        if (labels.size > 200) labels.clear()
        labels[packageName] = resolved
        return resolved
    }

    private fun clip(value: String): String {
        val clean = value.replace("\\s+".toRegex(), " ").trim()
        return if (clean.length <= 140) clean else clean.take(140) + "…"
    }

    companion object {
        private const val TAG = "HomeQuiet"
        private const val REPOST_MS = 3_000L
        private const val VERIFY_MS = 400L
        private const val SNOOZE_MS = 60 * 60 * 1000L
        private const val LOG_GAP_MS = 30_000L
        private const val ONGOING_LOG_GAP_MS = 5 * 60_000L
        private const val RANKING_SWEEP_MS = 5_000L

        @Volatile
        private var lastRankingSweep = 0L

        fun rebind(context: Context) {
            val cn = ComponentName(context.applicationContext, QuietNotificationListener::class.java)
            runCatching { NotificationListenerService.requestRebind(cn) }
        }
    }
}
