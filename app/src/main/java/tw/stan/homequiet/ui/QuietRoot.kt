package tw.stan.homequiet.ui

import androidx.compose.runtime.collectAsState
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import tw.stan.homequiet.data.Intents
import tw.stan.homequiet.data.Permissions

private enum class MainTab(val label: String) {
    Status("狀態"),
    Apps("應用"),
    Log("紀錄"),
}

@Composable
fun QuietRoot(vm: QuietViewModel = viewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val appsLoading by vm.appsLoading.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val fixing by vm.fixing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current

    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.onResume() }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.onResume() }
    val postLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.onResume() }

    val actions = PermissionActions(
        requestFine = {
            fineLauncher.launch(arrayOf(Permissions.FINE, Permissions.COARSE))
        },
        requestBackground = {
            if (!permissions.fine) {
                fineLauncher.launch(arrayOf(Permissions.FINE, Permissions.COARSE))
            } else {
                backgroundLauncher.launch(Permissions.BACKGROUND)
            }
        },
        requestPost = {
            if (Build.VERSION.SDK_INT >= 33) {
                postLauncher.launch(Permissions.POST_NOTIFICATIONS)
            }
        },
        openListener = { Intents.openNotificationListener(context) },
        openAppDetails = { Intents.openAppDetails(context) },
        openBattery = { Intents.openBattery(context) },
        openLocationServices = { Intents.openLocationServices(context) },
    )

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { vm.onResume() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3200)
            vm.consumeMessage()
        }
    }

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = MainTab.entries[tabIndex.coerceIn(0, MainTab.entries.lastIndex)]
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    BackHandler(enabled = snapshot.onboardingDone && tab != MainTab.Status) {
        tabIndex = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
    ) {
        val update by vm.update.collectAsState()
        update?.let { u ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !u.downloading) { vm.installUpdate() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                TextLabel("新版 ${u.version}", color = Paper, size = 13, modifier = Modifier.weight(1f))
                TextLabel(if (u.downloading) "下載中…" else "更新", color = Paper, size = 13)
            }
            Hairline()
        }
        if (message != null) {
            TextLabel(
                text = message.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                color = Paper,
                size = 13,
                lineHeight = 18,
            )
            Hairline()
        }
        if (!snapshot.onboardingDone) {
            OnboardingScreen(
                permissions = permissions,
                actions = actions,
                onFinish = vm::finishOnboarding,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    MainTab.Status -> StatusPage(
                        snapshot = snapshot,
                        permissions = permissions,
                        fixing = fixing,
                        actions = actions,
                        onOpenApps = { tabIndex = MainTab.Apps.ordinal },
                        onSetHome = vm::setHomeFromCurrent,
                        onRadius = vm::setRadius,
                        onPaused = vm::setPaused,
                        onWifi = vm::setWifiAlso,
                        onAddSsid = vm::addCurrentSsid,
                        onRemoveSsid = vm::removeSsid,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MainTab.Apps -> AppsPage(
                        apps = apps,
                        loading = appsLoading,
                        selected = snapshot.selectedPackages,
                        onToggle = vm::togglePackage,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MainTab.Log -> LogPage(
                        notes = snapshot.log,
                        onClear = vm::clearLog,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (!imeVisible) {
                QuietBottomBar(
                    selected = tab,
                    onSelect = { tabIndex = it.ordinal },
                )
            }
        }
    }
}

@Composable
private fun QuietBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Column {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
        ) {
            MainTab.entries.forEachIndexed { index, tab ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tab) }
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NavMark(tab = index, selected = tab == selected)
                    TextLabel(
                        text = tab.label,
                        color = if (tab == selected) Paper else Faint,
                        size = 12,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
