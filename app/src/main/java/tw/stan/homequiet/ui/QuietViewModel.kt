package tw.stan.homequiet.ui

import android.app.Application
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tw.stan.homequiet.data.AppCatalog
import tw.stan.homequiet.data.AppInfo
import tw.stan.homequiet.data.LocationHelper
import tw.stan.homequiet.data.PackageMatch
import tw.stan.homequiet.data.PermissionState
import tw.stan.homequiet.data.Permissions
import tw.stan.homequiet.data.QuietStore
import tw.stan.homequiet.data.Updater
import tw.stan.homequiet.data.WifiHelper
import tw.stan.homequiet.service.MonitorController
import tw.stan.homequiet.service.QuietNotificationListener

class QuietViewModel(application: Application) : AndroidViewModel(application) {
    private val store = QuietStore.get(application)

    val snapshot = store.snapshot

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _appsLoading = MutableStateFlow(true)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _fixing = MutableStateFlow(false)
    val fixing: StateFlow<Boolean> = _fixing.asStateFlow()

    private var appsJob: Job? = null
    private var fenceJob: Job? = null

    fun consumeMessage() {
        _message.value = null
    }

    fun onResume() {
        val app = getApplication<Application>()
        AppCatalog.linkGrokBot(app)
        _permissions.value = Permissions.read(app)
        refreshSignals()
        refreshApps()
        MonitorController.start(app)
        if (_permissions.value.listener) QuietNotificationListener.rebind(app)
        viewModelScope.launch {
            val pending = Updater.continuePending(app)
            if (!pending.isNullOrBlank()) _message.value = pending
            Updater.check(app)
        }
    }

    fun refreshApps() {
        appsJob?.cancel()
        appsJob = viewModelScope.launch(Dispatchers.Default) {
            val loaded = AppCatalog.load(getApplication())
            _apps.value = loaded
            _appsLoading.value = false
            autoSelectGrok(loaded)
            AppIcons.prefetch(getApplication(), loaded.take(24).filter { it.installed }.map { it.packageName })
        }
    }

    fun setPaused(paused: Boolean) {
        store.setPaused(paused)
    }

    fun setRadius(meters: Int) {
        store.setRadius(meters)
        fenceJob?.cancel()
        fenceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            tw.stan.homequiet.service.Geofencing.register(getApplication())
        }
    }

    fun setWifiAlso(enabled: Boolean) {
        val app = getApplication<Application>()
        val current = store.snapshot.value
        var captured: String? = null
        var missing = false
        if (enabled && current.homeSsid.isNullOrBlank()) {
            val ssid = WifiHelper.currentSsid(app)
            if (ssid.isNullOrBlank()) missing = true else captured = ssid
        }
        store.setWifiAlso(enabled, captured)
        if (missing) {
            _message.value = "目前讀不到 Wi-Fi 名稱。仍會用定位判斷。"
        }
    }

    fun addCurrentSsid() {
        val ssid = WifiHelper.currentSsid(getApplication()) ?: store.snapshot.value.currentSsid ?: return
        store.addSsid(ssid)
        if (!store.snapshot.value.wifiAlsoCounts) store.setWifiAlso(true, null)
    }

    fun removeSsid(ssid: String) = store.removeSsid(ssid)

    val update = Updater.state

    val updateResult = Updater.result
    val updateChecking = Updater.checking
    val updateError = Updater.error
    val updatePrompts = Updater.prompts

    fun showMessage(text: String) {
        _message.value = text
    }

    fun checkUpdateNow() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            Updater.check(app, force = true)
            val err = Updater.error.value
            _message.value = when {
                !err.isNullOrBlank() -> err
                Updater.state.value != null -> null
                else -> "已是最新版本"
            }
        }
    }

    fun onUpdateClicked(context: android.content.Context) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Updater.markAwaitingPermission(context)
            val intent = Updater.unknownSourcesIntent(context)
            val opened = runCatching { context.startActivity(intent) }.isSuccess ||
                runCatching {
                    context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
            _message.value = if (opened) {
                "請允許「安裝未知的應用程式」，回到這裡後會繼續下載。"
            } else {
                "無法開啟安裝權限設定。請到系統設定允許此 App 安裝未知的應用程式。"
            }
            return
        }
        val ready = Updater.state.value?.filePath?.let { java.io.File(it) }
        if (ready != null && ready.isFile) {
            val ok = runCatching { context.startActivity(Updater.installIntent(context, ready)) }.isSuccess
            if (!ok) _message.value = "無法開啟安裝程式"
            return
        }
        viewModelScope.launch {
            val err = Updater.downloadAndInstall(context.applicationContext)
            if (!err.isNullOrBlank()) _message.value = err
        }
    }

    /** Once per install/upgrade: select every installed app whose package or label contains "grok". */
    private fun autoSelectGrok(apps: List<AppInfo>) {
        val prefs = getApplication<Application>().getSharedPreferences("migrations", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("grok_autoselect_v1", false)) return
        val grok = apps.filter { it.installed && (it.packageName.contains("grok", true) || it.label.contains("grok", true)) }
            .map { it.packageName }
        if (grok.isNotEmpty()) {
            store.setSelected(store.snapshot.value.selectedPackages + grok)
            store.note("自動選取", grok.joinToString())
        }
        prefs.edit().putBoolean("grok_autoselect_v1", true).apply()
    }

    fun togglePackage(packageName: String, selected: Boolean) {
        val app = getApplication<Application>()
        val next = store.snapshot.value.selectedPackages.toMutableSet()
        if (selected) next.add(packageName) else next.remove(packageName)
        if (PackageMatch.isGrokBot(packageName)) {
            // The user's tap is authoritative, including turning Grok Bot back on.
            AppCatalog.markGrokLinked(app)
            if (selected) next.add(PackageMatch.BOT) else next.remove(PackageMatch.BOT)
            if (!AppCatalog.isPackageInstalled(app, PackageMatch.LEGACY)) next.remove(PackageMatch.LEGACY)
        }
        store.setSelected(next)
    }

    fun finishOnboarding() {
        store.setOnboardingDone()
        MonitorController.start(getApplication())
    }

    fun clearLog() {
        store.clearLog()
        _message.value = "已清空紀錄"
    }

    fun setHomeFromCurrent() {
        if (_fixing.value) return
        viewModelScope.launch {
            _fixing.value = true
            try {
                val app = getApplication<Application>()
                val perms = Permissions.read(app)
                _permissions.value = perms
                if (!perms.fine) {
                    _message.value = "需要精確位置，才能把這裡設為家。"
                    return@launch
                }
                val manager = app.getSystemService(LocationManager::class.java)
                if (manager == null || !manager.isLocationEnabled) {
                    _message.value = "手機的定位服務是關的。"
                    return@launch
                }
                val fix = LocationHelper.fetchFresh(app)
                if (fix == null) {
                    _message.value = "還沒取得定位。請確認定位已開啟，再到訊號好一點的地方試一次。"
                    return@launch
                }
                val ssid = WifiHelper.currentSsid(app)
                store.setHome(fix.latitude, fix.longitude, ssid, fix.time)
                tw.stan.homequiet.service.Geofencing.register(app)
                _message.value = if (ssid.isNullOrBlank()) {
                    "已把目前位置設為家。還沒讀到 Wi-Fi 名稱。"
                } else {
                    "已把目前位置設為家"
                }
            } finally {
                _fixing.value = false
            }
        }
    }

    private fun refreshSignals() {
        val app = getApplication<Application>()
        LocationHelper.lastKnown(app)?.let { fix ->
            store.setFix(fix.latitude, fix.longitude, fix.time, "定位", fix.accuracy)
        }
        val wifi = WifiHelper.read(app)
        store.applyWifi(wifi.connected, wifi.ssid)
    }
}
