package tw.stan.homequiet.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import tw.stan.homequiet.data.PermissionState
import tw.stan.homequiet.data.QuietSnapshot

@Composable
fun StatusPage(
    snapshot: QuietSnapshot,
    permissions: PermissionState,
    fixing: Boolean,
    actions: PermissionActions,
    onOpenApps: () -> Unit,
    onSetHome: () -> Unit,
    onRadius: (Int) -> Unit,
    onPaused: (Boolean) -> Unit,
    onWifi: (Boolean) -> Unit,
    onAddSsid: () -> Unit = {},
    onRemoveSsid: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showRestricted by rememberSaveable { mutableStateOf(false) }
    val distance = snapshot.distanceMeters()
    val (number, unit) = splitDistance(if (snapshot.homeLat == null) null else distance)
    val context = LocalContext.current
    val version = androidx.compose.runtime.remember {
        runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull().orEmpty()
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotMark(lit = snapshot.atHome && !snapshot.paused)
            Spacer(Modifier.width(10.dp))
            TextLabel("在家安靜", color = Mute, size = 13, spacing = 0.6f)
            Spacer(Modifier.weight(1f))
            TextLabel(
                text = "暫停",
                color = if (snapshot.paused) Paper else Mute,
                size = 13,
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = snapshot.paused,
                onCheckedChange = onPaused,
                colors = quietSwitchColors(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            HomeGlyph(
                atHome = snapshot.atHome,
                paused = snapshot.paused,
                hasHome = snapshot.homeLat != null,
                distanceMeters = distance,
                radiusMeters = snapshot.radiusMeters,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextLabel(
                    text = number,
                    size = 48,
                    lineHeight = 52,
                    weight = FontWeight.Light,
                    spacing = -1f,
                    align = TextAlign.Center,
                )
                if (unit.isNotEmpty()) {
                    TextLabel(unit, color = Mute, size = 13, align = TextAlign.Center)
                }
            }
        }

        TextLabel(
            text = presenceLabel(snapshot.presence),
            modifier = Modifier.fillMaxWidth(),
            size = 22,
            lineHeight = 28,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        TextLabel(
            text = presenceDetail(snapshot),
            modifier = Modifier.fillMaxWidth(),
            color = Mute,
            size = 14,
            align = TextAlign.Center,
        )
        if (snapshot.paused) {
            Spacer(Modifier.height(6.dp))
            TextLabel(
                text = "已暫停",
                modifier = Modifier.fillMaxWidth(),
                color = Paper,
                size = 13,
                align = TextAlign.Center,
            )
        }
        fixAgeHint(snapshot)?.let { hint ->
            Spacer(Modifier.height(4.dp))
            TextLabel(
                text = hint,
                modifier = Modifier.fillMaxWidth(),
                color = Faint,
                size = 12,
                align = TextAlign.Center,
            )
        }
        if (!permissions.locationEnabled) {
            Spacer(Modifier.height(10.dp))
            QuietTextButton("定位已關閉，點此開啟", onClick = actions.openLocationServices)
        }

        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(20.dp))
        SectionLabel("住家")
        Spacer(Modifier.height(10.dp))
        if (snapshot.homeLat != null && snapshot.homeLon != null) {
            TextLabel(formatCoords(snapshot.homeLat, snapshot.homeLon), size = 16)
            Spacer(Modifier.height(4.dp))
            TextLabel(
                text = if (snapshot.homeSsids.isEmpty()) "未設定住家 Wi-Fi" else "住家 Wi-Fi · ${snapshot.homeSsids.size}",
                color = Mute,
                size = 13,
            )
        } else {
            TextLabel("還沒有住家座標", color = Mute, size = 15)
        }
        Spacer(Modifier.height(16.dp))
        LineButton(
            text = if (fixing) "正在定位…" else "把目前位置設為家",
            onClick = onSetHome,
            enabled = !fixing,
        )
        Spacer(Modifier.height(10.dp))
        TextLabel(
            "GPS、Wi-Fi、行動網路都可定位",
            color = Faint,
            size = 12,
            lineHeight = 18,
        )

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("安靜範圍")
            Spacer(Modifier.weight(1f))
            TextLabel("${snapshot.radiusMeters} 公尺", size = 14)
        }
        QuietSlider(value = snapshot.radiusMeters, onValueChange = onRadius)
        Row {
            TextLabel("50", color = Faint, size = 12)
            Spacer(Modifier.weight(1f))
            TextLabel("500", color = Faint, size = 12)
        }

        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = "住家 Wi-Fi 也算在家",
            subtitle = "連上任一個即可",
            checked = snapshot.wifiAlsoCounts,
            onCheckedChange = onWifi,
        )
        snapshot.homeSsids.sorted().forEach { ssid ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextLabel(ssid, size = 14, color = if (ssid == snapshot.currentSsid) Paper else Mute, modifier = Modifier.weight(1f))
                QuietTextButton("移除", onClick = { onRemoveSsid(ssid) })
            }
        }
        val cur = snapshot.currentSsid
        if (cur != null && cur !in snapshot.homeSsids) {
            QuietTextButton("＋ 加入 $cur", onClick = onAddSsid)
        } else if (cur == null) {
            TextLabel("未連上 Wi-Fi", color = Faint, size = 12)
        }

        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("安靜對象")
            Spacer(Modifier.weight(1f))
            TextLabel(
                text = "${snapshot.selectedPackages.size} 個",
                size = 14,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LineButton("選擇應用程式", onClick = onOpenApps, filled = false)

        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(20.dp))
        SectionLabel("權限")
        Spacer(Modifier.height(4.dp))
        PermissionLine(
            title = "通知存取權",
            detail = if (permissions.listener) "已允許" else "尚未允許，點一下設定",
            ok = permissions.listener,
            onClick = actions.openListener,
        )
        PermissionLine(
            title = "精確位置",
            detail = when {
                permissions.fine -> "已允許"
                permissions.coarse -> "只有大約位置，請改成精確"
                else -> "尚未允許，點一下設定"
            },
            ok = permissions.fine,
            onClick = actions.requestFine,
        )
        PermissionLine(
            title = "背景位置",
            detail = if (permissions.background) "已一律允許" else "尚未允許，離開應用後可能無法判斷",
            ok = permissions.background,
            onClick = actions.requestBackground,
        )
        PermissionLine(
            title = "電池最佳化",
            detail = if (permissions.batteryIgnored) "已不受限制" else "仍可能被系統睡著",
            ok = permissions.batteryIgnored,
            onClick = actions.openBattery,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionLine(
                title = "狀態通知",
                detail = if (permissions.postNotifications) "已允許" else "尚未允許",
                ok = permissions.postNotifications,
                onClick = actions.requestPost,
            )
        }
        PermissionLine(
            title = "定位服務",
            detail = if (permissions.locationEnabled) "已開啟" else "已關閉",
            ok = permissions.locationEnabled,
            onClick = actions.openLocationServices,
        )
        QuietTextButton(
            text = if (showRestricted) "收合受限設定說明" else "受限設定怎麼開",
            onClick = { showRestricted = !showRestricted },
        )
        if (showRestricted) {
            RestrictedGuide(onOpenDetails = actions.openAppDetails)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(18.dp))
        TextLabel(
            text = if (version.isBlank()) "在家安靜" else "在家安靜  $version",
            modifier = Modifier.fillMaxWidth(),
            color = Faint,
            size = 12,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
    }
}
