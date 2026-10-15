package tw.stan.homequiet.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

val Acid = androidx.compose.ui.graphics.Color(0xFFD4FF3A)

/** Nearest quiet target (home or an enabled extra place) from the current point. */
data class Target(val name: String, val lat: Double, val lon: Double, val radius: Int, val distance: Double?, val bearing: Double?)

fun nearestTarget(s: QuietSnapshot): Target? {
    val list = buildList {
        if (s.homeLat != null && s.homeLon != null) add(Triple(QuietSnapshot.HOME_NAME, s.homeLat to s.homeLon, s.radiusMeters))
        s.places.filter { it.enabled }.forEach { add(Triple(it.name, it.lat to it.lon, it.radius)) }
    }
    if (list.isEmpty()) return null
    val cl = s.currentLat; val co = s.currentLon
    val withD = list.map { (n, ll, r) ->
        val d = if (cl != null && co != null) tw.stan.homequiet.data.Geo.meters(ll.first, ll.second, cl, co) else null
        val b = if (cl != null && co != null) tw.stan.homequiet.data.bearingDeg(cl, co, ll.first, ll.second) else null
        Target(n, ll.first, ll.second, r, d, b)
    }
    return withD.minByOrNull { it.distance ?: Double.MAX_VALUE }
}

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
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val target = nearestTarget(snapshot)
    val active = snapshot.activePlace
    val on = !snapshot.paused
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        // Wordmark: current place name, or the app name.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextLabel(active ?: "在家安靜", size = 15, weight = FontWeight.Bold, spacing = 0.4f)
            Spacer(Modifier.weight(1f))
            TextLabel(presenceLabel(snapshot.presence).let { if (active != null) "在場" else it }, color = if (active != null) Acid else Mute, size = 12)
        }
        Spacer(Modifier.height(8.dp))
        Radar(
            inside = active != null,
            quiet = active != null && on,
            target = target,
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
        TextLabel(
            text = when {
                active != null -> "在「$active」"
                target?.distance != null -> "離「${target.name}」${splitDistance(target.distance).let { it.first + " " + it.second }}"
                target == null -> "尚未設定地點"
                else -> "等待定位"
            },
            size = 16,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        TextLabel(
            text = listOfNotNull(
                if (active == null && target?.bearing != null) "往${tw.stan.homequiet.data.compassLabel(target.bearing)}" else null,
                target?.let { "範圍 ${it.radius} m" },
                fixAgeHint(snapshot),
            ).joinToString("  ·  "),
            color = Mute, size = 12, align = TextAlign.Center,
        )
        if (!permissions.locationEnabled) {
            Spacer(Modifier.height(8.dp))
            QuietTextButton("定位已關閉，點此開啟", onClick = actions.openLocationServices)
        }

        Spacer(Modifier.height(22.dp))
        HeroToggle(on = on, active = active != null, onToggle = { onPaused(on) })

        Spacer(Modifier.height(24.dp))
        Hairline()
        // Compact Wi-Fi line; editing lives in Settings.
        val ssid = snapshot.currentSsid
        val saved = snapshot.homeSsids.size + snapshot.places.sumOf { it.ssids.size }
        val matched = snapshot.wifiMatch || snapshot.places.any { it.enabled && it.ssidMatch(ssid) }
        CompactRow(
            title = "Wi-Fi",
            value = (ssid?.let { if (matched) "$it ✓" else it } ?: "未連線") + "  ·  已存 $saved",
            onClick = onOpenSettings,
        )
        Hairline()
        CompactRow(title = "安靜對象", value = "${snapshot.selectedPackages.size} 個", onClick = onOpenApps)
        Hairline()
        CompactRow(title = "地點", value = "${1 + snapshot.places.size} 個", onClick = onOpenSettings)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CompactRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().androidx_clickable(onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextLabel(title, color = Mute, size = 13)
        Spacer(Modifier.weight(1f))
        TextLabel(value, size = 14)
        TextLabel("  ›", color = Faint, size = 14)
    }
}

private fun Modifier.androidx_clickable(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))

/** Big, central ON/OFF switch: the hero of the status page. */
@Composable
fun HeroToggle(on: Boolean, active: Boolean, onToggle: () -> Unit) {
    val knob by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, label = "knob")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(220.dp)
                .height(84.dp)
                .clip(CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp))
                .background(if (on) Acid else androidx.compose.ui.graphics.Color(0xFF1C1C1C))
                .clickable(onClick = onToggle)
                .padding(8.dp),
        ) {
            val travel = 220.dp - 16.dp - 96.dp
            Box(
                Modifier
                    .padding(start = travel * knob)
                    .width(96.dp)
                    .fillMaxHeight()
                    .clip(CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
                    .background(if (on) Ink else androidx.compose.ui.graphics.Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center,
            ) {
                TextLabel(if (on) "ON" else "OFF", color = if (on) Acid else Mute, size = 20, weight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(10.dp))
        TextLabel(
            when {
                !on -> "安靜模式已關閉"
                active -> "安靜模式 · 正在清除通知"
                else -> "安靜模式 · 到達地點時自動清除"
            },
            color = if (on) Paper else Mute, size = 13,
        )
    }
}

/** Animated radar: sweep + pulsing rings; inside = solid core, away = bearing arrow toward nearest place (north-up). */
@Composable
fun Radar(inside: Boolean, quiet: Boolean, target: Target?, modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4200, easing = androidx.compose.animation.core.LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearEasing)), label = "pulse")
    val accent = if (quiet) Acid else Paper
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f - 12.dp.toPx()
            // concentric rings
            for (k in 1..4) drawCircle(Line, radius = r * k / 4f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            // cross hairs
            drawLine(Line, androidx.compose.ui.geometry.Offset(c.x - r, c.y), androidx.compose.ui.geometry.Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(Line, androidx.compose.ui.geometry.Offset(c.x, c.y - r), androidx.compose.ui.geometry.Offset(c.x, c.y + r), 1.dp.toPx())
            // pulse
            drawCircle(accent.copy(alpha = (1f - pulse) * 0.5f), radius = r * (0.25f + 0.75f * pulse), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            rotate(sweep, c) {
            // sweep wedge
                drawArc(
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent, 0.12f to accent.copy(alpha = 0.28f), 0.121f to androidx.compose.ui.graphics.Color.Transparent, center = c,
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    alpha = 1f,
                )
                drawLine(accent.copy(alpha = 0.7f), c, androidx.compose.ui.geometry.Offset(c.x + r, c.y), 1.5.dp.toPx())
            }
            // north tick
            drawLine(Mute, androidx.compose.ui.geometry.Offset(c.x, c.y - r - 8.dp.toPx()), androidx.compose.ui.geometry.Offset(c.x, c.y - r + 6.dp.toPx()), 2.dp.toPx())
            if (inside) {
                // solid diamond core
                val s = 16.dp.toPx()
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(c.x, c.y - s); lineTo(c.x + s, c.y); lineTo(c.x, c.y + s); lineTo(c.x - s, c.y); close() }
                drawPath(p, accent)
            } else if (target?.bearing != null && target.distance != null) {
                // place marker at a distance-scaled position, arrow from centre toward it
                val rad = Math.toRadians(target.bearing - 90.0)
                val frac = (kotlin.math.ln(1.0 + target.distance / target.radius) / kotlin.math.ln(1.0 + 50.0)).coerceIn(0.25, 1.0).toFloat()
                val px = c.x + (r * frac) * kotlin.math.cos(rad).toFloat(); val py = c.y + (r * frac) * kotlin.math.sin(rad).toFloat()
                drawLine(accent, c, androidx.compose.ui.geometry.Offset(px, py), 2.dp.toPx())
                val head = androidx.compose.ui.graphics.Path().apply {
                    val a = 8.dp.toPx()
                    moveTo(px + a * kotlin.math.cos(rad).toFloat(), py + a * kotlin.math.sin(rad).toFloat())
                    lineTo(px + a * kotlin.math.cos(rad + 2.4).toFloat(), py + a * kotlin.math.sin(rad + 2.4).toFloat())
                    lineTo(px + a * kotlin.math.cos(rad - 2.4).toFloat(), py + a * kotlin.math.sin(rad - 2.4).toFloat())
                    close()
                }
                drawPath(head, accent)
                drawCircle(Paper, radius = 4.dp.toPx(), center = c)
            } else {
                drawCircle(Mute, radius = 4.dp.toPx(), center = c)
            }
        }
        if (!inside && target?.distance != null) {
            val (n, u) = splitDistance(target.distance)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 70.dp)) {
                TextLabel(n, size = 30, weight = FontWeight.Bold)
                TextLabel(u, color = Mute, size = 11)
            }
        }
    }
}

@Composable
fun PlacesSection(
    snapshot: QuietSnapshot,
    fixing: Boolean,
    onSetHome: () -> Unit,
    onRadius: (Int) -> Unit,
    onWifi: (Boolean) -> Unit,
    onAddSsid: () -> Unit,
    onRemoveSsid: (String) -> Unit,
    onAddPlace: (String) -> Unit,
    onPlaceEnabled: (String, Boolean) -> Unit,
    onPlaceRadius: (String, Int) -> Unit,
    onPlaceRemove: (String) -> Unit,
    onPlaceBindSsid: (String) -> Unit,
    onPlaceUnbindSsid: (String, String) -> Unit,
) {
    SectionLabel("地點 · 在任一處都會安靜")
    Spacer(Modifier.height(10.dp))
    // Home (original settings, migrated as-is)
    TextLabel("住家", size = 15, weight = FontWeight.Bold)
    TextLabel(if (snapshot.homeLat != null) formatCoords(snapshot.homeLat, snapshot.homeLon ?: 0.0) else "尚未設定", color = Mute, size = 12)
    Spacer(Modifier.height(8.dp))
    LineButton(if (fixing) "正在定位…" else "把目前位置設為家", onClick = onSetHome, enabled = !fixing, filled = false)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextLabel("範圍", color = Mute, size = 12); Spacer(Modifier.weight(1f)); TextLabel("${snapshot.radiusMeters} m", size = 13)
    }
    QuietSlider(value = snapshot.radiusMeters, onValueChange = onRadius)
    ToggleRow(title = "住家 Wi-Fi 也算", subtitle = null, checked = snapshot.wifiAlsoCounts, onCheckedChange = onWifi)
    snapshot.homeSsids.sorted().forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextLabel(s, size = 13, color = if (s == snapshot.currentSsid) Paper else Mute, modifier = Modifier.weight(1f))
            QuietTextButton("移除", onClick = { onRemoveSsid(s) })
        }
    }
    val cur = snapshot.currentSsid
    if (cur != null && cur !in snapshot.homeSsids) QuietTextButton("＋ 加入 $cur", onClick = onAddSsid)

    snapshot.places.forEach { p ->
        Spacer(Modifier.height(16.dp)); Hairline(); Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextLabel(p.name, size = 15, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = p.enabled, onCheckedChange = { onPlaceEnabled(p.id, it) }, colors = quietSwitchColors())
        }
        TextLabel(formatCoords(p.lat, p.lon), color = Mute, size = 12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextLabel("範圍", color = Mute, size = 12); Spacer(Modifier.weight(1f)); TextLabel("${p.radius} m", size = 13)
        }
        QuietSlider(value = p.radius, onValueChange = { onPlaceRadius(p.id, it) })
        p.ssids.sorted().forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextLabel("Wi-Fi $s", size = 13, color = Mute, modifier = Modifier.weight(1f))
                QuietTextButton("移除", onClick = { onPlaceUnbindSsid(p.id, s) })
            }
        }
        Row {
            if (cur != null && cur !in p.ssids) QuietTextButton("＋ 綁定 $cur", onClick = { onPlaceBindSsid(p.id) })
            Spacer(Modifier.weight(1f))
            QuietTextButton("刪除", onClick = { onPlaceRemove(p.id) })
        }
    }

    Spacer(Modifier.height(16.dp)); Hairline(); Spacer(Modifier.height(12.dp))
    var name by rememberSaveable { mutableStateOf("") }
    TextLabel("用目前位置新增", color = Mute, size = 12)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (snapshot.places.none { it.name == "公司" }) {
            QuietTextButton("＋ 公司", onClick = { onAddPlace("公司") })
            Spacer(Modifier.width(12.dp))
        }
        androidx.compose.foundation.text.BasicTextField(
            value = name, onValueChange = { name = it.take(20) }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Paper, fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Paper),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            decorationBox = { inner -> Box { if (name.isEmpty()) TextLabel("自訂名稱", color = Faint, size = 14); inner() } },
        )
        QuietTextButton(if (fixing) "定位中…" else "新增", onClick = { if (name.isNotBlank()) { onAddPlace(name); name = "" } })
    }
}

@Composable
fun SettingsPage(
    permissions: PermissionState,
    actions: PermissionActions,
    updateResult: String? = null,
    updateChecking: Boolean = false,
    updateError: String? = null,
    onCheckUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    places: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showRestricted by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = androidx.compose.runtime.remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            TextLabel("設定", size = 20, lineHeight = 26, modifier = Modifier.weight(1f))
            TextLabel("v$version", color = Mute, size = 13)
        }
        Spacer(Modifier.height(20.dp))
        if (places != null) {
            places()
            Spacer(Modifier.height(24.dp))
        }
        SectionLabel("更新")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextLabel(
                when {
                    updateChecking -> "檢查中…"
                    updateResult == null -> ""
                    updateResult == "" -> "已是最新"
                    updateResult == "error" -> if (updateError.isNullOrBlank()) "無法連線" else "檢查失敗"
                    else -> "有新版 $updateResult"
                },
                color = Mute, size = 13,
            )
        }
        if (!updateError.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            TextLabel(updateError, color = Faint, size = 12, lineHeight = 18)
        }
        Spacer(Modifier.height(10.dp))
        if (!updateResult.isNullOrEmpty() && updateResult != "error") {
            LineButton("下載並安裝 $updateResult", onClick = onInstallUpdate)
        } else {
            LineButton("檢查更新", onClick = onCheckUpdate, filled = false, enabled = !updateChecking)
        }
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

        Spacer(Modifier.height(28.dp))
    }
}
