@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.yadavard.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** A reminder occurrence as shown in a list. */
data class Entry(val reminder: Reminder, val at: Long, val attention: Boolean)

@Composable
fun ReminderCard(entry: Entry, now: Long, actions: ReminderActions, modifier: Modifier = Modifier, showDate: Boolean = true) {
    val r = entry.reminder
    val current by rememberUpdatedState(r)
    val currentActions by rememberUpdatedState(actions)
    val density = LocalDensity.current
    // Armed only after the card was held past the threshold for a moment, so quick accidental swipes do nothing.
    var ready by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Plain (non-saveable) state: a restored item must not come back already "swiped".
    val state = remember(r.id) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, density,
            confirmValueChange = { value ->
                if (ready) when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> if (!current.done) currentActions.miss(current)
                    SwipeToDismissBoxValue.EndToStart -> confirmDelete = true
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                false // always spring back; the list updates by itself
            },
            positionalThreshold = { distance -> distance * 0.45f })
    }
    LaunchedEffect(state.targetValue) {
        ready = false
        if (state.targetValue != SwipeToDismissBoxValue.Settled) { delay(400); ready = true }
    }
    SwipeToDismissBox(state, modifier = modifier, enableDismissFromStartToEnd = !r.done,
        backgroundContent = {
            val direction = state.dismissDirection
            val past = state.targetValue != SwipeToDismissBoxValue.Settled
            val done = direction == SwipeToDismissBoxValue.StartToEnd
            // "done" here is the swipe toward "not done" (the tick button already marks done).
            val base = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            val color by animateColorAsState(when {
                direction == SwipeToDismissBoxValue.Settled -> Color.Transparent
                ready -> base
                past -> base.copy(alpha = 0.75f)
                else -> base.copy(alpha = 0.35f)
            }, label = "swipe")
            val scale by animateFloatAsState(if (ready) 1.25f else 1f, label = "icon")
            // The label sits on the side the card uncovers. Swipe directions follow the physical drag, so in
            // right-to-left layouts the uncovered side is the opposite of the layout's start/end.
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(color).padding(horizontal = 20.dp),
                contentAlignment = if (done != rtl) Alignment.CenterStart else Alignment.CenterEnd) {
                if (direction != SwipeToDismissBoxValue.Settled) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (done) Icons.Rounded.EventBusy else Icons.Rounded.DeleteForever, null, tint = Color.White,
                        modifier = Modifier.size(26.dp).scale(scale))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (done) t("انجام نشد", "Not done") else t("حذف", "Delete"), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(when {
                            ready -> t("رها کن", "Release")
                            past -> t("کمی نگه دار…", "Hold…")
                            else -> t("بیشتر بکش", "Keep swiping")
                        }, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }
        }) {
        ReminderCardBody(entry, now, actions, showDate)
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Rounded.DeleteOutline, null) },
        title = { Text(t("حذف شود؟", "Delete reminder?")) },
        text = { Text(t("«${r.title}» حذف می‌شود. بعد از حذف هم تا چند ثانیه می‌توانی برش گردانی.",
            "“${r.title}” will be deleted. You can still undo for a few seconds.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; actions.delete(r) }) {
            Text(t("حذف", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("نگه دار", "Keep")) } })
}

@Composable
private fun ReminderCardBody(entry: Entry, now: Long, actions: ReminderActions, showDate: Boolean) {
    val r = entry.reminder
    val color = r.category.catColor()
    val zone = r.zoneId
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerLow, tonalElevation = 0.dp, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.combinedClickable(onClick = { actions.open(r) }, onLongClick = { menu = true })
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(r.category.catIcon(), null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.important) {
                        Icon(Icons.Rounded.Flag, null, tint = scheme.error, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    }
                    Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (r.done) TextDecoration.LineThrough else null,
                        color = if (r.done) scheme.onSurfaceVariant else scheme.onSurface)
                }
                Spacer(Modifier.height(3.dp))
                val timeColor = if (entry.attention) scheme.error else scheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (entry.attention) Icons.Rounded.ErrorOutline else Icons.Rounded.Schedule, null, tint = timeColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    val today = LocalDate.now(zone)
                    val date = Dates.localDate(entry.at, zone)
                    val text = buildString {
                        if (r.missed) {
                            append(t("انجام نشد ", "Not done "))
                            if (r.completedAt > 0) append(Dates.relative(r.completedAt, now, AppDisplay.language))
                        } else if (r.done) {
                            append(t("انجام شد ", "Done "))
                            if (r.completedAt > 0) append(Dates.relative(r.completedAt, now, AppDisplay.language))
                        } else {
                            if (showDate || date != today) append(Dates.friendlyDay(date, today, AppDisplay.calendar, AppDisplay.language)).append(" ")
                            append(Dates.formatTime(entry.at, zone, AppDisplay.language))
                            if (entry.at - now in -86_400_000L..86_400_000L || entry.attention) append(" · ").append(Dates.relative(entry.at, now, AppDisplay.language))
                        }
                    }
                    Text(text, style = MaterialTheme.typography.bodySmall, color = timeColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (entry.attention && !r.done) {
                    Spacer(Modifier.height(6.dp))
                    // Quick way to record that this one passed without being done (kept for statistics).
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, scheme.error.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { actions.miss(r) }.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EventBusy, null, tint = scheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("انجام نشد", "Not done"), color = scheme.error, fontSize = 12.sp)
                    }
                }
                if (r.unit != RepeatUnit.NONE || r.alertStyle == AlertStyle.ALARM || r.snoozeAt > 0) {
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (r.unit != RepeatUnit.NONE) MiniTag(Icons.Rounded.Repeat, repeatLabel(r), color)
                        if (r.alertStyle == AlertStyle.ALARM) MiniTag(Icons.Rounded.Alarm, t("زنگ", "Alarm"), scheme.tertiary)
                        if (r.snoozeAt > now) MiniTag(Icons.Rounded.Snooze, Dates.formatTime(r.snoozeAt, zone, AppDisplay.language), scheme.primary)
                    }
                }
            }
            if (!r.done) {
                IconButton(onClick = { actions.complete(r) }) {
                    Box(Modifier.size(26.dp).clip(CircleShape).border(2.dp, color.copy(alpha = 0.7f), CircleShape))
                }
            } else if (r.missed) {
                IconButton(onClick = { actions.open(r) }) { Icon(Icons.Rounded.EventBusy, null, tint = scheme.error) }
            } else {
                IconButton(onClick = { actions.open(r) }) { Icon(Icons.Rounded.CheckCircle, null, tint = scheme.secondary) }
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                if (!r.done) DropdownMenuItem(text = { Text(t("انجام شد", "Mark done")) }, leadingIcon = { Icon(Icons.Rounded.Check, null) },
                    onClick = { menu = false; actions.complete(r) })
                if (!r.done) DropdownMenuItem(text = { Text(t("انجام نشد (ثبت در آمار)", "Not done (keep in stats)")) },
                    leadingIcon = { Icon(Icons.Rounded.EventBusy, null) }, onClick = { menu = false; actions.miss(r) })
                if (!r.done && entry.attention) listOf(10, 60).forEach { m ->
                    DropdownMenuItem(text = { Text(if (m == 60) t("یک ساعت بعد یادم بنداز", "Remind in 1 hour") else t("${n(m)} دقیقه بعد", "Remind in $m min")) },
                        leadingIcon = { Icon(Icons.Rounded.Snooze, null) }, onClick = { menu = false; actions.snooze(r, m) })
                }
                if (!r.done && r.repeating) DropdownMenuItem(text = { Text(t("رد کردن این نوبت", "Skip this one")) },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) }, onClick = { menu = false; actions.skip(r) })
                DropdownMenuItem(text = { Text(t("اشتراک‌گذاری (پک)", "Share as pack")) }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    onClick = { menu = false; actions.share(r) })
                DropdownMenuItem(text = { Text(t("ویرایش", "Edit")) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menu = false; actions.open(r) })
                DropdownMenuItem(text = { Text(t("حذف", "Delete")) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                    onClick = { menu = false; actions.delete(r) })
            }
        }
    }
}

@Composable
fun MiniTag(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null, color: Color = MaterialTheme.colorScheme.onSurface, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text(n(count), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Month grid for the selected display calendar. [marks] gives up to three dot colours per day.
 * The month shown follows [month] (any date inside it).
 */
@Composable
fun MonthGrid(month: LocalDate, selected: LocalDate?, onSelect: (LocalDate) -> Unit, onMonthChange: (LocalDate) -> Unit,
              marks: Map<LocalDate, List<Color>> = emptyMap(), minDate: LocalDate? = null,
              importantDays: Set<LocalDate> = emptySet()) {
    val cal = AppDisplay.calendar
    val lang = AppDisplay.language
    val first = Dates.monthStart(month, cal)
    val p = Dates.parts(first, cal)
    val length = Dates.monthLength(p.year, p.month, cal)
    val startDay = Dates.firstDayOfWeek(cal)
    val offset = ((first.dayOfWeek.value - startDay.value) + 7) % 7
    val today = LocalDate.now()
    val scheme = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(Dates.shiftMonth(first, -1, cal)) }) {
                Icon(if (rtl) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft, t("ماه قبل", "Previous month"))
            }
            Text(Dates.monthTitle(first, cal, lang), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onMonthChange(Dates.shiftMonth(first, 1, cal)) }) {
                Icon(if (rtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight, t("ماه بعد", "Next month"))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            (0..6).forEach { i ->
                val day = startDay.plus(i.toLong())
                Text(Dates.weekdayName(day, lang, short = true), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (cal == CalendarSystem.PERSIAN && day == DayOfWeek.FRIDAY) scheme.error else scheme.onSurfaceVariant)
            }
        }
        val rows = (offset + length + 6) / 7
        repeat(rows) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNumber = week * 7 + col - offset + 1
                    if (dayNumber !in 1..length) { Spacer(Modifier.weight(1f).height(48.dp)); return@repeat }
                    val date = first.plusDays((dayNumber - 1).toLong())
                    val isSelected = date == selected
                    val isToday = date == today
                    val disabled = minDate != null && date.isBefore(minDate)
                    val holiday = cal == CalendarSystem.PERSIAN && date.dayOfWeek == DayOfWeek.FRIDAY
                    val ring = date in importantDays
                    Column(Modifier.weight(1f).height(48.dp).padding(2.dp).clip(RoundedCornerShape(14.dp))
                        .background(when { isSelected -> scheme.primary; isToday -> scheme.primaryContainer; else -> Color.Transparent })
                        .then(if (ring) Modifier.border(2.dp, scheme.error, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(enabled = !disabled) { onSelect(date) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(n(dayNumber), fontSize = 14.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> scheme.onPrimary
                                disabled -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
                                holiday -> scheme.error
                                isToday -> scheme.onPrimaryContainer
                                else -> scheme.onSurface
                            })
                        val dots = marks[date].orEmpty()
                        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            dots.take(3).forEach { c ->
                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) scheme.onPrimary else c))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DatePickerSheet(initial: LocalDate, title: String, minDate: LocalDate? = null, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    var month by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().widthIn(max = 460.dp),
        title = { Text(title) },
        text = {
            Column {
                Text(Dates.formatDate(selected, AppDisplay.calendar, AppDisplay.language, withWeekday = true),
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                MonthGrid(month, selected, { selected = it }, { month = it }, minDate = minDate)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text(t("تأیید", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("انصراف", "Cancel")) } })
}

@Composable
fun TimePickerSheet(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, is24Hour = true)
    var keyboard by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(t("انتخاب ساعت", "Choose time")) },
        text = {
            // The clock dial is drawn left-to-right in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (keyboard) TimeInput(state) else TimePicker(state)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text(t("تأیید", "OK")) } },
        dismissButton = {
            Row {
                IconButton(onClick = { keyboard = !keyboard }) {
                    Icon(if (keyboard) Icons.Rounded.Schedule else Icons.Rounded.Keyboard, t("تغییر حالت", "Switch input"))
                }
                TextButton(onClick = onDismiss) { Text(t("انصراف", "Cancel")) }
            }
        })
}

@Composable
fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Chip row that wraps across lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, icon: ((T) -> ImageVector)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) },
                leadingIcon = icon?.let { f -> { Icon(f(option), null, Modifier.size(18.dp)) } })
        }
    }
}

fun zoneNow(): ZoneId = ZoneId.systemDefault()
