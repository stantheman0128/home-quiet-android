package tw.stan.homequiet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import tw.stan.homequiet.data.DismissedNote

@Composable
fun LogPage(
    notes: List<DismissedNote>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { SimpleDateFormat("M月d日 HH:mm", Locale.TAIWAN) }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TextLabel("紀錄", size = 20, lineHeight = 26)
                Spacer(Modifier.height(4.dp))
                TextLabel(
                    text = if (notes.isEmpty()) "尚無紀錄" else "${notes.size} 則",
                    color = Faint,
                    size = 12,
                )
            }
            if (notes.isNotEmpty()) {
                TextLabel(
                    text = "清空",
                    color = Paper,
                    size = 14,
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DotMark(lit = false)
                    Spacer(Modifier.height(16.dp))
                    TextLabel("尚無紀錄", size = 16, align = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    TextLabel(
                        text = "被清除的通知會列在這裡",
                        color = Mute,
                        size = 14,
                        lineHeight = 21,
                        align = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(notes, key = { index, note -> "${note.id}-$index" }) { _, note ->
                    LogRow(note = note, whenLabel = formatter.format(Date(note.timestamp)))
                    Hairline()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LogRow(note: DismissedNote, whenLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextLabel(note.appLabel, size = 15, modifier = Modifier.weight(1f))
            TextLabel(whenLabel, color = Faint, size = 12)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = note.title,
            color = Paper,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (note.text.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = note.text,
                color = Mute,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        TextLabel("${note.reason} · 已清除", color = Faint, size = 12)
    }
}
