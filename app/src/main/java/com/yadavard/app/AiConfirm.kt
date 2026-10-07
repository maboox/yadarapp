@file:OptIn(ExperimentalLayoutApi::class)

package com.yadavard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.time.LocalDate

/** A draft whose one-time due moment already passed cannot be saved without fixing it. */
private fun Reminder.invalid(now: Long) = !repeating && nextAt <= now

/**
 * Shows what the AI understood and saves it automatically after a short countdown unless the user
 * taps Edit (opens the full form for that reminder; the others are saved) or Cancel.
 */
@Composable
fun DraftConfirmContent(transcript: String?, drafts: List<Reminder>, onSave: (List<Reminder>) -> Unit,
                        onEdit: (Reminder, List<Reminder>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val list = remember(drafts) { drafts.toMutableStateList() }
    var paused by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val hasInvalid = list.any { it.invalid(now) }
    val total = (5_000L + 3_000L * (list.size - 1).coerceAtLeast(0)).coerceAtMost(15_000L)
    var remaining by remember(list.size) { mutableLongStateOf(total) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(list.size, paused, hasInvalid) {
        if (list.isEmpty()) { onCancel(); return@LaunchedEffect }
        if (paused || hasInvalid) return@LaunchedEffect
        while (remaining > 0) { delay(50); remaining -= 50 }
        if (!finished) { finished = true; onSave(list.toList()) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!transcript.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                Icon(Icons.Rounded.RecordVoiceOver, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("«$transcript»", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(if (list.size > 1) t("${n(list.size)} یادآوری تشخیص داده شد:", "${list.size} reminders found:")
            else t("این یادآوری ساخته می‌شود:", "This reminder will be created:"), style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { System.identityHashCode(it) }) { r ->
                DraftCard(r, now, context,
                    onEdit = {
                        finished = true
                        onEdit(r, list.filter { it !== r && !it.invalid(System.currentTimeMillis()) })
                    },
                    onRemove = { list.remove(r) })
            }
        }
        if (hasInvalid) Text(t("زمان یکی از یادآوری‌ها گذشته است؛ «اصلاح» را بزن.", "One reminder's time has passed — tap Edit."),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        else {
            LinearProgressIndicator(progress = { 1f - remaining.toFloat() / total }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (paused) t("ذخیرهٔ خودکار متوقف شد", "Auto-save paused")
                    else t("اگر «اصلاح» را نزنی، تا ${n((remaining + 999) / 1000)} ثانیهٔ دیگر ذخیره می‌شود", "Saving in ${(remaining + 999) / 1000}s unless you tap Edit"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = { paused = !paused }) {
                    Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) t("ادامه", "Resume") else t("مکث", "Pause"))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { finished = true; onCancel() }, modifier = Modifier.weight(1f)) { Text(t("لغو", "Cancel")) }
            Button(onClick = { finished = true; onSave(list.filter { !it.invalid(System.currentTimeMillis()) }) },
                enabled = list.any { !it.invalid(now) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (list.size > 1) t("ذخیرهٔ همه", "Save all") else t("ذخیره", "Save"))
            }
        }
    }
}

@Composable
private fun DraftCard(r: Reminder, now: Long, context: android.content.Context, onEdit: () -> Unit, onRemove: () -> Unit) {
    val zone = r.zoneId
    val scheme = MaterialTheme.colorScheme
    val color = r.category.color()
    val invalid = r.invalid(now)
    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(r.category.icon(), null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, t("حذف", "Remove"), Modifier.size(18.dp)) }
            }
            val at = r.nextAt
            Line(Icons.Rounded.Event, Dates.friendlyDay(Dates.localDate(at, zone), LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
                " • " + Dates.formatTime(at, zone, AppDisplay.language) +
                (if (at > now) " (" + Dates.relative(at, now, AppDisplay.language) + ")" else " — " + t("گذشته!", "passed!")),
                if (invalid) scheme.error else scheme.onSurface)
            if (r.unit != RepeatUnit.NONE) Line(Icons.Rounded.Repeat, repeatLabel(r) +
                (r.untilAt?.let { t(" تا ", " until ") + Dates.formatDate(Dates.localDate(it, zone), AppDisplay.calendar, AppDisplay.language) } ?: ""),
                scheme.onSurface)
            if (r.note.isNotBlank()) Line(Icons.AutoMirrored.Rounded.Notes, r.note, scheme.onSurfaceVariant)
            // Highlight everything that differs from the user's defaults so mistakes are easy to spot.
            val extras = buildList {
                if (r.alertStyle != Prefs.defaultAlert(context)) add(if (r.alertStyle == AlertStyle.ALARM) Icons.Rounded.Alarm to t("زنگ تمام‌صفحه", "Full-screen alarm")
                    else Icons.Rounded.Notifications to t("فقط اعلان", "Notification only"))
                if (r.important) add(Icons.Rounded.Flag to t("مهم", "Important"))
                if (r.leadMinutes != Prefs.defaultLead(context)) add(Icons.Rounded.NotificationImportant to
                    (if (r.leadMinutes == 0) t("بدون یادآوری زودتر", "No advance notice") else t("${leadLabel(r.leadMinutes)} زودتر خبر می‌دهد", "${leadLabel(r.leadMinutes)} early")))
                if (r.nagMinutes > 0) add(Icons.Rounded.Replay to t("تا انجام، هر ${leadLabel(r.nagMinutes)}", "Until done, every ${leadLabel(r.nagMinutes)}"))
                if (r.category != Category.GENERAL) add(r.category.icon() to r.category.label())
            }
            if (extras.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                extras.forEach { (icon, label) -> Chip(icon, label, scheme.tertiary) }
            } else Text(t("بقیهٔ تنظیمات: پیش‌فرض", "Other options: defaults"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Row {
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("اصلاح", "Edit"))
                }
            }
        }
    }
}

@Composable
private fun Line(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color.copy(alpha = 0.8f))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Stand-alone dialog for AI results of typed quick add. */
@Composable
fun DraftConfirmDialog(transcript: String?, drafts: List<Reminder>, onSave: (List<Reminder>) -> Unit,
                       onEdit: (Reminder, List<Reminder>) -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(16.dp).fillMaxWidth().widthIn(max = 480.dp)) {
            Box(Modifier.padding(18.dp)) { DraftConfirmContent(transcript, drafts, onSave, onEdit, onCancel) }
        }
    }
}
