package tw.stan.homequiet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.stan.homequiet.data.AppInfo

@Composable
fun AppsPage(
    apps: List<AppInfo>,
    loading: Boolean,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val needle = query.trim()
    val shown = androidx.compose.runtime.remember(apps, needle, onlySelected, if (onlySelected) selected else null) { apps.filter { app ->
        val matchesQuery = needle.isEmpty() ||
            app.label.contains(needle, ignoreCase = true) ||
            app.packageName.contains(needle, ignoreCase = true) ||
            app.subtitle.contains(needle, ignoreCase = true)
        val matchesFilter = !onlySelected || app.packageName in selected
        matchesQuery && matchesFilter
    } }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(12.dp))
        TextLabel("要安靜的應用", size = 20, lineHeight = 26)
        Spacer(Modifier.height(8.dp))
        TextLabel(
            "在家時清除這些通知",
            color = Mute,
            size = 14,
            lineHeight = 21,
        )
        Spacer(Modifier.height(16.dp))
        TextLabel("已選 ${selected.size}", color = Faint, size = 12, spacing = 0.4f)
        Spacer(Modifier.height(14.dp))
        SearchField(
            query = query,
            onQuery = { query = it },
            onDone = { focus.clearFocus() },
        )
        Spacer(Modifier.height(16.dp))
        Row {
            FilterLabel("全部", on = !onlySelected, onClick = { onlySelected = false })
            FilterLabel("已選", on = onlySelected, onClick = { onlySelected = true })
        }
        when {
            loading && apps.isEmpty() -> {
                Spacer(Modifier.height(32.dp))
                TextLabel("正在讀取應用程式", color = Mute, size = 14)
            }
            shown.isEmpty() -> {
                Spacer(Modifier.height(32.dp))
                TextLabel("沒有符合的應用程式", color = Mute, size = 14)
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // stable keys + contentType let Compose reuse row slots while flinging
                    items(shown, key = { it.packageName }, contentType = { "app" }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            onToggle = { onToggle(app.packageName, !checkedOf(app, selected)) },
                        )
                        Hairline()
                    }
                    item(key = "footer", contentType = "footer") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun checkedOf(app: AppInfo, selected: Set<String>): Boolean = app.packageName in selected

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onDone: () -> Unit,
) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = Paper, fontSize = 16.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box(Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) TextLabel("搜尋應用程式", color = Faint, size = 16)
                    inner()
                }
                Spacer(Modifier.height(10.dp))
                Hairline()
            }
        },
    )
}

@Composable
private fun FilterLabel(label: String, on: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(end = 22.dp, bottom = 10.dp),
    ) {
        TextLabel(label, color = if (on) Paper else Mute, size = 14)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.width(16.dp).height(1.dp)) {
            if (on) {
                drawLine(
                    color = Paper,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(packageName = app.packageName, installed = app.installed)
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextLabel(app.label, color = if (checked) Paper else Mute, size = 16)
                if (app.pinned) {
                    Spacer(Modifier.width(8.dp))
                    TextLabel("預設", color = Faint, size = 11, spacing = 0.6f)
                }
            }
            Spacer(Modifier.height(2.dp))
            TextLabel(app.subtitle, color = Faint, size = 12, lineHeight = 16)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = quietSwitchColors(),
        )
    }
}
