package tw.stan.homequiet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import tw.stan.homequiet.data.Presence
import tw.stan.homequiet.data.QuietSnapshot

class PermissionActions(
    val requestFine: () -> Unit,
    val requestBackground: () -> Unit,
    val requestPost: () -> Unit,
    val openListener: () -> Unit,
    val openAppDetails: () -> Unit,
    val openBattery: () -> Unit,
    val openLocationServices: () -> Unit,
)

fun splitDistance(meters: Double?): Pair<String, String> {
    if (meters == null || meters.isNaN()) return "—" to ""
    return if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f", meters / 1000.0) to "公里"
    } else {
        meters.roundToInt().toString() to "公尺"
    }
}

fun formatCoords(lat: Double, lon: Double): String {
    return String.format(Locale.US, "%.5f, %.5f", lat, lon)
}

fun presenceLabel(presence: Presence): String = when (presence) {
    Presence.Home -> "在家"
    Presence.Away -> "外出"
    Presence.NoFix -> "尚無定位"
    Presence.Stale -> "定位過期"
    Presence.NoHome -> "尚未設家"
}

fun presenceDetail(snapshot: QuietSnapshot): String = when (snapshot.presence) {
    Presence.Home -> snapshot.homeReason() ?: "在家"
    Presence.Away -> "在範圍外"
    Presence.NoFix -> "開啟定位後才能判斷"
    Presence.Stale -> "最後的位置太舊，先不當作在家"
    Presence.NoHome -> "把目前位置設成家，範圍就會開始算"
}

fun fixAgeHint(snapshot: QuietSnapshot, now: Long = System.currentTimeMillis()): String? {
    if (snapshot.fixTime <= 0L || snapshot.homeLat == null) return null
    val minutes = ((now - snapshot.fixTime) / 60_000L).toInt()
    if (minutes < 2) return null
    return if (minutes < 60) "${minutes} 分鐘前的位置" else "${minutes / 60} 小時前的位置"
}

@Composable
fun DotMark(modifier: Modifier = Modifier, lit: Boolean = true) {
    Canvas(modifier.size(16.dp)) {
        val radius = 1.15.dp.toPx()
        val gap = size.width / 3f
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val on = lit || (row == 1 && col == 1)
                drawCircle(
                    color = Color.White.copy(alpha = if (on) 1f else 0.28f),
                    radius = radius,
                    center = Offset(gap * (col + 0.5f), gap * (row + 0.5f)),
                )
            }
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    TextLabel(
        text = text,
        modifier = modifier,
        color = Mute,
        size = 12,
        spacing = 1.1f,
    )
}

@Composable
fun TextLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Paper,
    size: Int = 16,
    spacing: Float = 0f,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
    lineHeight: Int = size + 6,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = spacing.sp,
        textAlign = align,
    )
}

@Composable
fun LineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val background = when {
        !enabled -> Color.Transparent
        filled -> Color.White
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> Faint
        filled -> Color.Black
        else -> Color.White
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(background)
            .border(1.dp, if (enabled) Color.White else Line)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TextLabel(text, color = foreground, size = 15, weight = FontWeight.Medium)
    }
}

@Composable
fun QuietTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TextLabel(text, color = Mute, size = 14)
    }
}

@Composable
fun quietSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.Black,
    checkedTrackColor = Color.White,
    checkedBorderColor = Color.White,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color.Transparent,
    uncheckedBorderColor = LineStrong,
    disabledCheckedThumbColor = Color.Black,
    disabledCheckedTrackColor = Color.White,
    disabledUncheckedThumbColor = Mute,
    disabledUncheckedTrackColor = Color.Transparent,
    disabledUncheckedBorderColor = Line,
)

@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            TextLabel(title, size = 16)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                TextLabel(subtitle, color = Mute, size = 13, lineHeight = 18)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = quietSwitchColors(),
        )
    }
}

@Composable
fun MarkBox(ok: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .border(1.dp, Color.White)
            .background(if (ok) Color.White else Color.Transparent),
    )
}

@Composable
fun PermissionLine(
    title: String,
    detail: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkBox(ok)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            TextLabel(title, size = 15)
            Spacer(Modifier.height(2.dp))
            TextLabel(detail, color = Mute, size = 12, lineHeight = 16)
        }
    }
}

@Composable
fun QuietSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = QuietSnapshot.MIN_RADIUS,
    max: Int = QuietSnapshot.MAX_RADIUS,
    step: Int = QuietSnapshot.RADIUS_STEP,
) {
    fun snap(x: Float, width: Float): Int {
        if (width <= 0f) return value
        val fraction = (x / width).coerceIn(0f, 1f)
        val raw = min + fraction * (max - min)
        return ((raw / step).roundToInt() * step).coerceIn(min, max)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = "安靜範圍 $value 公尺" }
            .pointerInput(min, max, step) {
                detectTapGestures { offset -> onValueChange(snap(offset.x, size.width.toFloat())) }
            }
            .pointerInput(min, max, step) {
                detectHorizontalDragGestures { change, _ ->
                    onValueChange(snap(change.position.x, size.width.toFloat()))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f
            val span = (max - min).coerceAtLeast(1)
            val fraction = ((value - min).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            val thumbRadius = 7.dp.toPx()
            val x = (fraction * size.width).coerceIn(thumbRadius, size.width - thumbRadius)
            drawLine(LineStrong, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawLine(Color.White, Offset(0f, y), Offset(x, y), strokeWidth = 1.5.dp.toPx())
            drawCircle(Color.White, radius = thumbRadius, center = Offset(x, y))
            drawCircle(Color.Black, radius = 2.4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
fun HomeGlyph(
    atHome: Boolean,
    paused: Boolean,
    hasHome: Boolean,
    distanceMeters: Double?,
    radiusMeters: Int,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "glyph")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val active = hasHome && atHome && !paused
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val ring = size.minDimension * 0.36f
        val count = 48
        for (index in 0 until count) {
            val angle = (index / count.toFloat()) * (PI * 2) - PI / 2
            val lit = when {
                paused -> index % 6 == 0
                !hasHome -> index % 4 == 0
                active -> true
                else -> index % 3 == 0
            }
            val alpha = when {
                active && lit -> 0.35f + 0.65f * pulse
                lit -> 0.88f
                else -> 0.16f
            }
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = if (lit && active) 2.3.dp.toPx() else 1.5.dp.toPx(),
                center = Offset(
                    center.x + cos(angle).toFloat() * ring,
                    center.y + sin(angle).toFloat() * ring,
                ),
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = if (active) 0.35f else 0.12f),
            radius = ring,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        if (active) {
            val inner = ring * 0.62f
            val innerCount = 12
            for (index in 0 until innerCount) {
                val angle = (index / innerCount.toFloat()) * (PI * 2) - PI / 2
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f + 0.5f * pulse),
                    radius = 1.7.dp.toPx(),
                    center = Offset(
                        center.x + cos(angle).toFloat() * inner,
                        center.y + sin(angle).toFloat() * inner,
                    ),
                )
            }
        }
        if (hasHome && distanceMeters != null && radiusMeters > 0) {
            val ratio = (distanceMeters / radiusMeters.toDouble()).coerceIn(0.0, 1.28)
            if (ratio > 0.14) {
                val point = Offset(center.x, center.y - (ratio * ring).toFloat())
                drawCircle(
                    color = if (atHome && !paused) Color.White else Color(0xFF9A9A9A),
                    radius = 5.dp.toPx(),
                    center = point,
                )
            }
        }
    }
}

@Composable
fun AppGlyph(packageName: String, installed: Boolean, modifier: Modifier = Modifier) {
    if (!installed) {
        Box(modifier.size(32.dp), contentAlignment = Alignment.Center) {
            DotMark(lit = true)
        }
        return
    }
    val context = LocalContext.current
    val icon by androidx.compose.runtime.produceState(AppIcons.cached(packageName), packageName) {
        if (value == null) value = AppIcons.load(context.applicationContext, packageName)
    }
    val bmp = icon
    if (bmp == null) {
        Box(modifier.size(32.dp))
        return
    }
    androidx.compose.foundation.Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
fun RestrictedGuide(onOpenDetails: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line)
            .padding(16.dp),
    ) {
        TextLabel("若系統不讓你開", size = 15, weight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        TextLabel(
            "從檔案或瀏覽器安裝的應用，Android 13 以上會把通知存取權當成受限設定。要先解鎖，系統才准你開。",
            color = Mute,
            size = 13,
            lineHeight = 20,
        )
        Spacer(Modifier.height(12.dp))
        val steps = listOf(
            "點下面的「應用程式資訊」。",
            "點右上角選單 ⋮。",
            "點「允許受限設定」，並完成螢幕鎖定驗證。",
            "回到這裡，再開啟通知存取權。",
        )
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TextLabel(
                    text = "%02d".format(index + 1),
                    modifier = Modifier.width(32.dp),
                    color = Faint,
                    size = 12,
                )
                TextLabel(step, size = 14, lineHeight = 20)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextLabel(
            "Android 15 有時會在你開啟權限時直接跳出這個步驟。若應用程式資訊裡沒有那個選單，就直接開通知存取權，照系統提示做。",
            color = Mute,
            size = 13,
            lineHeight = 20,
        )
        Spacer(Modifier.height(14.dp))
        LineButton(text = "應用程式資訊", onClick = onOpenDetails, filled = false)
    }
}

@Composable
fun StepTicks(count: Int, index: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { step ->
            val on = step <= index
            val current = step == index
            Box(
                Modifier
                    .height(2.dp)
                    .width(if (current) 28.dp else 14.dp)
                    .background(if (on) Color.White else LineStrong),
            )
        }
    }
}

@Composable
fun NavMark(tab: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val color = Color.White.copy(alpha = if (selected) 1f else 0.35f)
    Canvas(modifier.size(18.dp)) {
        when (tab) {
            0 -> {
                drawCircle(color, radius = 5.5.dp.toPx(), style = Stroke(1.4.dp.toPx()))
                drawCircle(color, radius = 1.6.dp.toPx())
            }
            1 -> {
                val gap = size.width / 3f
                for (row in 0 until 2) {
                    for (col in 0 until 2) {
                        drawCircle(
                            color,
                            radius = 1.5.dp.toPx(),
                            center = Offset(gap * (col + 0.7f), gap * (row + 0.7f)),
                        )
                    }
                }
            }
            else -> {
                val y0 = 4.dp.toPx()
                val gap = 4.5.dp.toPx()
                repeat(3) { index ->
                    val y = y0 + gap * index
                    drawLine(color, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), strokeWidth = 1.3.dp.toPx())
                }
            }
        }
    }
}
