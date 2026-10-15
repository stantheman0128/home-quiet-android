package tw.stan.homequiet.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.os.Build
import tw.stan.homequiet.data.PermissionState

private const val STEP_COUNT = 6

@Composable
fun OnboardingScreen(
    permissions: PermissionState,
    actions: PermissionActions,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step -= 1 }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotMark(lit = true)
            Spacer(Modifier.width(10.dp))
            TextLabel("在家安靜", color = Mute, size = 13, spacing = 0.6f)
            Spacer(Modifier.weight(1f))
            TextLabel(
                text = "%02d".format(step + 1),
                color = Paper,
                size = 13,
            )
            TextLabel(" / %02d".format(STEP_COUNT), color = Faint, size = 13)
        }
        Spacer(Modifier.height(18.dp))
        StepTicks(count = STEP_COUNT, index = step)
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> WelcomeStep()
                1 -> NotificationStep(actions)
                2 -> PreciseStep()
                3 -> BackgroundStep(permissions)
                4 -> BatteryStep()
                else -> ReadyStep(permissions, actions)
            }
            Spacer(Modifier.height(24.dp))
        }
        StepActions(
            step = step,
            permissions = permissions,
            actions = actions,
            onNext = { if (step < STEP_COUNT - 1) step += 1 },
            onFinish = onFinish,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WelcomeStep() {
    TextLabel("到家就安靜", size = 26, lineHeight = 32)
    Spacer(Modifier.height(16.dp))
    TextLabel(
        "在家時自動清除所選應用的通知，出門就恢復。",
        color = Paper,
        size = 14,
        lineHeight = 22,
    )
    Spacer(Modifier.height(12.dp))
    TextLabel(
        "預設：Grok Bot。",
        color = Mute,
        size = 15,
        lineHeight = 24,
    )
}

@Composable
private fun NotificationStep(actions: PermissionActions) {
    TextLabel("允許清除通知", size = 26, lineHeight = 32)
    Spacer(Modifier.height(16.dp))
    TextLabel(
        "需要通知存取權才能清除通知。",
        color = Paper,
        size = 14,
        lineHeight = 22,
    )
    Spacer(Modifier.height(20.dp))
    RestrictedGuide(onOpenDetails = actions.openAppDetails)
}

@Composable
private fun PreciseStep() {
    TextLabel("精確位置", size = 26, lineHeight = 32)
    Spacer(Modifier.height(16.dp))
    TextLabel(
        "請選「精確」，用來判斷是否在家。",
        color = Paper,
        size = 14,
        lineHeight = 22,
    )
    Spacer(Modifier.height(12.dp))
    TextLabel(
        "室內也可用 Wi-Fi／行動網路定位。",
        color = Mute,
        size = 15,
        lineHeight = 24,
    )
}

@Composable
private fun BackgroundStep(permissions: PermissionState) {
    TextLabel("背景也要知道", size = 26, lineHeight = 32)
    Spacer(Modifier.height(16.dp))
    TextLabel(
        "請選「一律允許」，背景才能判斷。",
        color = Paper,
        size = 14,
        lineHeight = 22,
    )
    if (!permissions.fine) {
        Spacer(Modifier.height(12.dp))
        TextLabel(
            "請先允許精確位置。",
            color = Mute,
            size = 13,
            lineHeight = 20,
        )
    }
}

@Composable
private fun BatteryStep() {
    TextLabel("不要被系統睡著", size = 26, lineHeight = 32)
    Spacer(Modifier.height(16.dp))
    TextLabel(
        "關閉電池最佳化，避免背景判斷被停掉。",
        color = Paper,
        size = 14,
        lineHeight = 22,
    )
    if (Build.VERSION.SDK_INT >= 33) {
        Spacer(Modifier.height(12.dp))
        TextLabel(
            "不會常駐通知。",
            color = Mute,
            size = 13,
            lineHeight = 20,
        )
    }
}

@Composable
private fun ReadyStep(permissions: PermissionState, actions: PermissionActions) {
    TextLabel("可以開始了", size = 26, lineHeight = 32)
    Spacer(Modifier.height(16.dp))
    TextLabel(
        "到家自動安靜，出門恢復。",
        color = Paper,
        size = 14,
        lineHeight = 22,
    )
    Spacer(Modifier.height(8.dp))
    PermissionLine(
        title = "通知存取權",
        detail = if (permissions.listener) "已允許" else "尚未允許",
        ok = permissions.listener,
        onClick = actions.openListener,
    )
    PermissionLine(
        title = "精確位置",
        detail = if (permissions.fine) "已允許" else "尚未允許",
        ok = permissions.fine,
        onClick = actions.requestFine,
    )
    PermissionLine(
        title = "背景位置",
        detail = if (permissions.background) "已一律允許" else "尚未允許",
        ok = permissions.background,
        onClick = actions.requestBackground,
    )
    PermissionLine(
        title = "電池最佳化",
        detail = if (permissions.batteryIgnored) "已不受限制" else "仍可能被睡著",
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
}

@Composable
private fun StepActions(
    step: Int,
    permissions: PermissionState,
    actions: PermissionActions,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    when (step) {
        0 -> LineButton("繼續", onClick = onNext)
        1 -> if (permissions.listener) {
            LineButton("下一步", onClick = onNext)
            QuietTextButton("再看一次設定", onClick = actions.openListener)
        } else {
            LineButton("開啟通知存取權", onClick = actions.openListener)
            Spacer(Modifier.height(8.dp))
            LineButton("應用程式資訊", onClick = actions.openAppDetails, filled = false)
            QuietTextButton("稍後再說", onClick = onNext)
        }
        2 -> if (permissions.fine) {
            LineButton("下一步", onClick = onNext)
        } else {
            LineButton("允許精確位置", onClick = actions.requestFine)
            QuietTextButton("稍後再說", onClick = onNext)
        }
        3 -> {
            if (permissions.background) {
                LineButton("下一步", onClick = onNext)
            } else if (!permissions.fine) {
                LineButton("先允許精確位置", onClick = actions.requestFine)
                QuietTextButton("稍後再說", onClick = onNext)
            } else {
                LineButton("允許背景位置", onClick = actions.requestBackground)
                QuietTextButton("稍後再說", onClick = onNext)
            }
        }
        4 -> {
            if (!permissions.batteryIgnored) {
                LineButton("關閉電池最佳化", onClick = actions.openBattery)
                Spacer(Modifier.height(8.dp))
            }
            if (Build.VERSION.SDK_INT >= 33 && !permissions.postNotifications) {
                LineButton(
                    text = "允許狀態通知",
                    onClick = actions.requestPost,
                    filled = permissions.batteryIgnored,
                )
                Spacer(Modifier.height(8.dp))
            }
            val ready = permissions.batteryIgnored &&
                (Build.VERSION.SDK_INT < 33 || permissions.postNotifications)
            if (ready) {
                LineButton("下一步", onClick = onNext)
            } else {
                QuietTextButton("稍後再說", onClick = onNext)
            }
        }
        else -> LineButton("進入在家安靜", onClick = onFinish)
    }
}
