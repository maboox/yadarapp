@file:OptIn(ExperimentalLayoutApi::class)

package com.yadavard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Lists the assistant's proposed actions and applies them after a short countdown unless the user
 * taps Edit (opens the form for that one, the rest are applied), removes items, pauses or cancels.
 */
@Composable
fun ActionsConfirmContent(actions: List<AssistantAction>, onApply: (List<AssistantAction>) -> Unit,
                          onEdit: (AssistantAction, List<AssistantAction>) -> Unit, onCancel: () -> Unit) {
    val list = remember(actions) { actions.toMutableStateList() }
    var paused by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val needsFix = list.any { it.needsFix(now) }
    val total = (5_000L + 3_000L * (list.size - 1).coerceAtLeast(0)).coerceAtMost(15_000L)
    var remaining by remember(list.size) { mutableLongStateOf(total) }
    var finished by remember { mutableStateOf(false) }
    val destructive = list.any { it is AssistantAction.Delete }

    LaunchedEffect(list.size, paused, needsFix) {
        if (list.isEmpty()) { if (!finished) { finished = true; onCancel() }; return@LaunchedEffect }
        if (paused || needsFix) return@LaunchedEffect
        while (remaining > 0) { delay(50); remaining -= 50 }
        if (!finished) { finished = true; onApply(list.toList()) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(list, key = { i, a -> "$i-${System.identityHashCode(a)}" }) { _, a ->
                ActionCard(a, now,
                    onEdit = if (a is AssistantAction.Create || a is AssistantAction.Update) ({
                        finished = true
                        onEdit(a, list.filter { it !== a && !it.needsFix(System.currentTimeMillis()) })
                    }) else null,
                    onRemove = { list.remove(a) })
            }
        }
        if (needsFix) Text(t("زمان یکی از موارد گذشته است؛ «اصلاح» را بزن.", "One item's time has passed — tap Edit."),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        else {
            LinearProgressIndicator(progress = { 1f - remaining.toFloat() / total }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (paused) t("انجام خودکار متوقف شد", "Auto-apply paused")
                    else t("اگر «اصلاح» یا «لغو» را نزنی، تا ${n((remaining + 999) / 1000)} ثانیهٔ دیگر انجام می‌شود", "Applying in ${(remaining + 999) / 1000}s unless you edit or cancel"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = { paused = !paused }) {
                    Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) t("ادامه", "Resume") else t("مکث", "Pause"))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { finished = true; onCancel() }, modifier = Modifier.weight(1f)) { Text(t("لغو", "Cancel")) }
            Button(onClick = { finished = true; onApply(list.filter { !it.needsFix(System.currentTimeMillis()) }) },
                enabled = list.any { !it.needsFix(now) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (list.size > 1) t("انجام همه", "Apply all") else t("تأیید", "Confirm"))
            }
        }
    }
}

@Composable
private fun ActionCard(a: AssistantAction, now: Long, onEdit: (() -> Unit)?, onRemove: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val (label, labelColor, icon) = when (a) {
        is AssistantAction.Create -> Triple(t("یادآوری جدید", "New reminder"), scheme.primary, Icons.Rounded.AddCircle)
        is AssistantAction.Update -> Triple(t("ویرایش", "Edit"), scheme.tertiary, Icons.Rounded.Edit)
        is AssistantAction.Delete -> Triple(t("حذف", "Delete"), scheme.error, Icons.Rounded.DeleteOutline)
        is AssistantAction.Complete -> Triple(t("انجام شد", "Mark done"), scheme.secondary, Icons.Rounded.CheckCircle)
        is AssistantAction.Postpone -> Triple(t("تعویق", "Postpone"), scheme.tertiary, Icons.Rounded.Snooze)
    }
    val r = when (a) {
        is AssistantAction.Create -> a.draft
        is AssistantAction.Update -> a.after
        is AssistantAction.Delete -> a.target
        is AssistantAction.Complete -> a.target
        is AssistantAction.Postpone -> a.target
    }
    val zone = r.zoneId
    val color = r.category.color()
    Surface(shape = RoundedCornerShape(18.dp), color = if (a is AssistantAction.Delete) scheme.errorContainer.copy(alpha = 0.5f) else scheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = labelColor)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, t("حذف از فهرست", "Remove"), Modifier.size(18.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(r.category.icon(), null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when (a) {
                is AssistantAction.Update -> Assistant.describeChanges(a.before, a.after).forEach { Line(Icons.Rounded.ChevronLeft, it, scheme.onSurface) }
                is AssistantAction.Postpone -> Line(Icons.Rounded.Schedule, t("${leadLabel(a.minutes)} عقب‌تر", "${leadLabel(a.minutes)} later"), scheme.onSurface)
                is AssistantAction.Create -> {
                    val at = r.nextAt
                    Line(Icons.Rounded.Event, Dates.friendlyDay(Dates.localDate(at, zone), LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
                        " • " + Dates.formatTime(at, zone, AppDisplay.language) +
                        (if (at > now) " (" + Dates.relative(at, now, AppDisplay.language) + ")" else " — " + t("گذشته!", "passed!")),
                        if (a.needsFix(now)) scheme.error else scheme.onSurface)
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
                        extras.forEach { (i, l) -> Chip(i, l, scheme.tertiary) }
                    } else Text(t("بقیهٔ تنظیمات: پیش‌فرض", "Other options: defaults"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                else -> if (r.displayAt(now) > 0) Line(Icons.Rounded.Event, Dates.friendlyDay(Dates.localDate(r.displayAt(now), zone), LocalDate.now(zone),
                    AppDisplay.calendar, AppDisplay.language) + " • " + Dates.formatTime(r.displayAt(now), zone, AppDisplay.language), scheme.onSurfaceVariant)
            }
            if (onEdit != null) Row {
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
