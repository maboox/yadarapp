@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.yadavard.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class RepeatChoice { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

@Composable
fun EditorScreen(request: EditorRequest, onClose: () -> Unit, onSave: (Reminder) -> Unit, onDelete: (Reminder) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val original = request.original
    val source = original ?: request.draft
    val now = System.currentTimeMillis()
    val initialAt = source?.let { if (it.pendingAt > 0 && !it.repeating) it.pendingAt else it.nextAt.takeIf { n -> n > 0 } ?: it.firstAt }
        ?: Instant.ofEpochMilli(now).atZone(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
    val initialLocal = Instant.ofEpochMilli(initialAt).atZone(zone)

    var title by remember { mutableStateOf(source?.title.orEmpty()) }
    var note by remember { mutableStateOf(source?.note.orEmpty()) }
    var date by remember { mutableStateOf(initialLocal.toLocalDate()) }
    var hour by remember { mutableIntStateOf(initialLocal.hour) }
    var minute by remember { mutableIntStateOf(initialLocal.minute) }
    var unit by remember { mutableStateOf(source?.unit ?: RepeatUnit.NONE) }
    var every by remember { mutableStateOf((source?.every ?: 1).toString()) }
    var weekdays by remember { mutableIntStateOf(source?.weekdays?.takeIf { it != 0 } ?: (1 shl (initialLocal.dayOfWeek.value - 1))) }
    var monthDay by remember { mutableIntStateOf(source?.monthDay ?: 0) }
    var until by remember { mutableStateOf(source?.untilAt?.let { Dates.localDate(it, zone) }) }
    var category by remember { mutableStateOf(source?.category ?: Category.GENERAL) }
    var important by remember { mutableStateOf(source?.important ?: false) }
    var alert by remember { mutableStateOf(source?.alertStyle ?: Prefs.defaultAlert(context)) }
    var lead by remember { mutableIntStateOf(source?.leadMinutes ?: if (original == null) Prefs.defaultLead(context) else 0) }
    var nag by remember { mutableIntStateOf(source?.nagMinutes ?: 0) }
    var choice by remember {
        mutableStateOf(when {
            unit == RepeatUnit.NONE -> RepeatChoice.ONCE
            unit == RepeatUnit.HOURS || unit == RepeatUnit.AFTER_DONE_DAYS || (source?.every ?: 1) > 1 -> RepeatChoice.CUSTOM
            unit == RepeatUnit.DAYS -> RepeatChoice.DAILY
            unit == RepeatUnit.WEEKS -> RepeatChoice.WEEKLY
            unit == RepeatUnit.MONTHS -> RepeatChoice.MONTHLY
            else -> RepeatChoice.YEARLY
        })
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMore by remember { mutableStateOf(original != null && (note.isNotBlank() || lead > 0 || nag > 0 || important)) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (original == null && title.isBlank()) runCatching { titleFocus.requestFocus() } }

    val cal = AppDisplay.calendar
    val dueAt = Dates.at(date, hour, minute, zone)
    val dayOfMonth = Dates.parts(date, cal).day

    fun build(): Reminder? {
        val interval = every.toIntOrNull() ?: 0
        val base = original ?: Reminder(title = "", firstAt = dueAt)
        val draft = base.copy(
            title = title.trim(), note = note.trim(), category = category, important = important, alertStyle = alert,
            firstAt = dueAt, unit = unit, every = if (unit == RepeatUnit.NONE) 1 else interval,
            weekdays = if (unit == RepeatUnit.WEEKS) weekdays else 0,
            monthDay = if (unit == RepeatUnit.MONTHS || unit == RepeatUnit.YEARS) monthDay else 0,
            untilAt = if (unit == RepeatUnit.NONE || unit == RepeatUnit.AFTER_DONE_DAYS) null
                else until?.let { Dates.at(it, 23, 59, zone) },
            leadMinutes = lead, nagMinutes = nag, zone = zone.id, calendar = cal,
            done = false, pendingAt = 0, alertedAt = 0, snoozeAt = 0)
        var aligned = Recurrence.align(draft)
        if (aligned.repeating && aligned.nextAt <= System.currentTimeMillis()) {
            val next = Recurrence.nextAfter(aligned, System.currentTimeMillis()) ?: return null
            aligned = aligned.copy(nextAt = next)
        }
        return aligned
    }
    val preview = remember(dueAt, unit, every, weekdays, monthDay, until, cal) {
        if (unit == RepeatUnit.NONE || (every.toIntOrNull() ?: 0) !in 1..10_000) emptyList()
        else runCatching { build()?.let { Recurrence.preview(it, 4) }.orEmpty() }.getOrDefault(emptyList())
    }

    fun save() {
        val interval = every.toIntOrNull() ?: 0
        error = when {
            title.isBlank() -> t("عنوان را بنویس", "Enter a title")
            unit != RepeatUnit.NONE && interval !in 1..10_000 -> t("فاصلهٔ تکرار معتبر نیست", "Invalid repeat interval")
            unit == RepeatUnit.WEEKS && weekdays == 0 -> t("دست‌کم یک روز هفته را انتخاب کن", "Pick at least one weekday")
            (unit == RepeatUnit.NONE || unit == RepeatUnit.AFTER_DONE_DAYS) && dueAt <= System.currentTimeMillis() ->
                t("این زمان گذشته است؛ زمانی در آینده انتخاب کن", "That time has passed; pick a future time")
            until != null && unit != RepeatUnit.NONE && until!!.isBefore(date) -> t("تاریخ پایان قبل از شروع است", "End date is before the start")
            else -> null
        }
        if (error != null) return
        val r = build()
        if (r == null) { error = t("این تکرار دیگر موعدی در آینده ندارد", "This repeat has no future dates"); return }
        onSave(r)
    }

    fun setChoice(c: RepeatChoice) {
        choice = c
        when (c) {
            RepeatChoice.ONCE -> unit = RepeatUnit.NONE
            RepeatChoice.DAILY -> { unit = RepeatUnit.DAYS; every = "1" }
            RepeatChoice.WEEKLY -> { unit = RepeatUnit.WEEKS; every = "1"; if (weekdays == 0) weekdays = 1 shl (date.dayOfWeek.value - 1) }
            RepeatChoice.MONTHLY -> { unit = RepeatUnit.MONTHS; every = "1"; if (monthDay == 0) monthDay = dayOfMonth }
            RepeatChoice.YEARLY -> { unit = RepeatUnit.YEARS; every = "1" }
            RepeatChoice.CUSTOM -> if (unit == RepeatUnit.NONE) { unit = RepeatUnit.DAYS; every = "2" }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(if (original == null) t("یادآوری جدید", "New reminder") else t("ویرایش یادآوری", "Edit reminder")) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, t("بستن", "Close")) } },
                    actions = {
                        if (original != null) IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.DeleteOutline, t("حذف", "Delete"), tint = MaterialTheme.colorScheme.error)
                        }
                        Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) { Text(t("ذخیره", "Save")) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                OutlinedTextField(title, { title = it; error = null }, Modifier.fillMaxWidth().focusRequester(titleFocus),
                    placeholder = { Text(t("چه چیزی را یادت بیندازم؟", "What should I remind you about?")) },
                    textStyle = MaterialTheme.typography.titleLarge, shape = RoundedCornerShape(18.dp), maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

                // When
                Block(t("زمان", "When"), Icons.Rounded.Schedule) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigPick(Icons.Rounded.Event, Dates.formatDate(date, cal, AppDisplay.language, withWeekday = true),
                            Dates.friendlyDay(date, LocalDate.now(zone), cal, AppDisplay.language), Modifier.weight(1.6f)) { pickDate = true }
                        BigPick(Icons.Rounded.AccessTime, Dates.formatTime(hour, minute, AppDisplay.language),
                            if (dueAt > System.currentTimeMillis()) Dates.relative(dueAt, System.currentTimeMillis(), AppDisplay.language)
                            else t("گذشته", "passed"), Modifier.weight(1f)) { pickTime = true }
                    }
                    val today = LocalDate.now(zone)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickChip(t("امروز", "Today"), date == today) { date = today }
                        QuickChip(t("فردا", "Tomorrow"), date == today.plusDays(1)) { date = today.plusDays(1) }
                        QuickChip(t("هفتهٔ بعد", "Next week"), date == today.plusWeeks(1)) { date = today.plusWeeks(1) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(9 to 0, 12 to 0, 18 to 0, 21 to 0).forEach { (h, m) ->
                            QuickChip(Dates.formatTime(h, m, AppDisplay.language), hour == h && minute == m) { hour = h; minute = m }
                        }
                        QuickChip(t("+۱ ساعت", "+1 hour"), false) {
                            val t1 = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).plusHours(1)
                            date = t1.toLocalDate(); hour = t1.hour; minute = t1.minute
                        }
                    }
                }

                // Repeat
                Block(t("تکرار", "Repeat"), Icons.Rounded.Repeat) {
                    ChoiceChips(RepeatChoice.entries, choice, {
                        when (it) {
                            RepeatChoice.ONCE -> t("یک‌بار", "Once")
                            RepeatChoice.DAILY -> t("هر روز", "Daily")
                            RepeatChoice.WEEKLY -> t("هر هفته", "Weekly")
                            RepeatChoice.MONTHLY -> t("هر ماه", "Monthly")
                            RepeatChoice.YEARLY -> t("هر سال", "Yearly")
                            RepeatChoice.CUSTOM -> t("سفارشی", "Custom")
                        }
                    }, ::setChoice)
                    AnimatedVisibility(choice == RepeatChoice.CUSTOM) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(t("هر", "Every"))
                                OutlinedTextField(every, { every = Dates.asciiDigits(it, 4) }, Modifier.width(90.dp), singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp))
                                var open by remember { mutableStateOf(false) }
                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                        Text(unitName(unit), Modifier.weight(1f)); Icon(Icons.Rounded.ArrowDropDown, null)
                                    }
                                    DropdownMenu(open, { open = false }) {
                                        listOf(RepeatUnit.HOURS, RepeatUnit.DAYS, RepeatUnit.WEEKS, RepeatUnit.MONTHS, RepeatUnit.YEARS,
                                            RepeatUnit.AFTER_DONE_DAYS).forEach { u ->
                                            DropdownMenuItem(text = { Text(unitName(u)) }, onClick = {
                                                unit = u; open = false
                                                if (u == RepeatUnit.MONTHS && monthDay == 0) monthDay = dayOfMonth
                                            })
                                        }
                                    }
                                }
                            }
                            if (unit == RepeatUnit.AFTER_DONE_DAYS) Hint(t("نوبت بعدی از زمانی حساب می‌شود که «انجام شد» را بزنی؛ مثل آب دادن به گلدان.",
                                "The next one is counted from when you tap Done — great for watering plants."))
                            if (unit == RepeatUnit.HOURS) Hint(t("مناسب برای دارو یا نوشیدن آب؛ از زمان شروع حساب می‌شود.",
                                "Good for medicine or drinking water; counted from the start time."))
                        }
                    }
                    if (unit == RepeatUnit.WEEKS) {
                        val order = generateSequence(Dates.firstDayOfWeek(cal)) { it.plus(1) }.take(7).toList()
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            order.forEach { d -> DayToggle(d, weekdays and (1 shl (d.value - 1)) != 0, Modifier.weight(1f)) {
                                weekdays = weekdays xor (1 shl (d.value - 1))
                            } }
                        }
                    }
                    if (unit == RepeatUnit.MONTHS) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (cal == CalendarSystem.PERSIAN) t("روز ماه شمسی:", "Day of Persian month:") else t("روز ماه میلادی:", "Day of month:"))
                            OutlinedTextField(if (monthDay > 0) monthDay.toString() else if (monthDay == 0) dayOfMonth.toString() else "",
                                { v -> monthDay = Dates.asciiDigits(v, 2).toIntOrNull()?.coerceIn(1, 31) ?: 0 },
                                Modifier.width(80.dp), singleLine = true, enabled = monthDay != -1,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp))
                            FilterChip(monthDay == -1, { monthDay = if (monthDay == -1) dayOfMonth else -1 }, { Text(t("آخر ماه", "Last day")) })
                        }
                        Hint(t("اگر ماهی آن روز را نداشت، آخرین روز همان ماه یادآوری می‌شود.", "Shorter months use their last day."))
                    }
                    if (unit != RepeatUnit.NONE && unit != RepeatUnit.AFTER_DONE_DAYS) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("پایان:", "Ends:"))
                            Spacer(Modifier.width(8.dp))
                            FilterChip(until == null, { until = null }, { Text(t("هرگز", "Never")) })
                            Spacer(Modifier.width(8.dp))
                            FilterChip(until != null, { pickUntil = true }, {
                                Text(until?.let { t("تا ", "Until ") + Dates.formatDate(it, cal, AppDisplay.language) } ?: t("در تاریخ…", "On date…"))
                            })
                        }
                    }
                    if (preview.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(t("نوبت‌های بعدی", "Upcoming dates"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            preview.forEach {
                                Text("• " + Dates.formatDate(Dates.localDate(it, zone), cal, AppDisplay.language, withWeekday = true) +
                                    "  " + Dates.formatTime(it, zone, AppDisplay.language), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Alert
                Block(t("نحوهٔ یادآوری", "How to alert"), Icons.Rounded.NotificationsActive) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(alert == AlertStyle.NOTIFICATION, { alert = AlertStyle.NOTIFICATION },
                            SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp)) }) {
                            Text(t("اعلان", "Notification"))
                        }
                        SegmentedButton(alert == AlertStyle.ALARM, { alert = AlertStyle.ALARM },
                            SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp)) }) {
                            Text(t("زنگ تمام‌صفحه", "Full-screen alarm"))
                        }
                    }
                    Hint(if (alert == AlertStyle.ALARM) t("مثل ساعت زنگ‌دار، با صدا و صفحهٔ کامل حتی روی صفحهٔ قفل.", "Rings like an alarm clock, full screen even on the lock screen.")
                        else t("اعلان معمولی با صدا و لرزش.", "A regular notification with sound and vibration."))
                    Text(t("یادآوری زودتر", "Advance notice"), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf(0, 5, 15, 30, 60, 1440), lead, ::leadLabel, { lead = it })
                }

                TextButton(onClick = { showMore = !showMore }) {
                    Icon(if (showMore) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    Spacer(Modifier.width(4.dp)); Text(t("گزینه‌های بیشتر", "More options"))
                }
                AnimatedVisibility(showMore) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Block(t("دسته‌بندی", "Category"), Icons.AutoMirrored.Rounded.Label) {
                            ChoiceChips(Category.entries, category, { it.label() }, { category = it }, icon = { it.icon() })
                        }
                        Block(t("جزئیات", "Details"), Icons.AutoMirrored.Rounded.Notes) {
                            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
                                placeholder = { Text(t("توضیحات، آدرس، شماره تماس…", "Notes, address, phone number…")) }, shape = RoundedCornerShape(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { important = !important }.padding(vertical = 4.dp)) {
                                Icon(Icons.Rounded.Flag, null, tint = if (important) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t("مهم", "Important"))
                                    Text(t("بالای فهرست و با علامت قرمز", "Shown first with a red flag"), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(important, { important = it })
                            }
                        }
                        Block(t("تکرار هشدار تا انجام", "Keep reminding until done"), Icons.Rounded.NotificationImportant) {
                            Hint(t("اگر «انجام شد» را نزنی، هر چند دقیقه دوباره یادآوری می‌کند.", "If you don't tap Done, it alerts again every few minutes."))
                            ChoiceChips(listOf(0, 5, 10, 15, 30, 60), nag, { if (it == 0) t("خاموش", "Off") else leadLabel(it) }, { nag = it })
                        }
                    }
                }
                error?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                if (original != null && original.completedCount > 0) Hint(t("${n(original.completedCount)} بار انجام شده", "Completed ${original.completedCount} times"))
                Button(onClick = ::save, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text(t("ذخیرهٔ یادآوری", "Save reminder"), fontSize = 16.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (pickDate) DatePickerSheet(date, t("انتخاب تاریخ", "Choose date"), LocalDate.now(zone), { pickDate = false }) {
        if (monthDay > 0 && monthDay == dayOfMonth) monthDay = Dates.parts(it, cal).day
        if (unit == RepeatUnit.WEEKS && Integer.bitCount(weekdays) == 1) weekdays = 1 shl (it.dayOfWeek.value - 1)
        date = it; pickDate = false; error = null
    }
    if (pickUntil) DatePickerSheet(until ?: date.plusMonths(1), t("پایان تکرار", "End repeat"), date, { pickUntil = false }) {
        until = it; pickUntil = false
    }
    if (pickTime) TimePickerSheet(hour, minute, { pickTime = false }) { h, m -> hour = h; minute = m; pickTime = false; error = null }
    if (confirmDelete && original != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Rounded.DeleteOutline, null) },
        title = { Text(t("حذف شود؟", "Delete reminder?")) },
        text = { Text(t("«${original.title}» و اعلان‌های آینده‌اش حذف می‌شود.", "“${original.title}” and its future alerts will be removed.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(original) }) { Text(t("حذف", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("انصراف", "Cancel")) } })
}

private fun unitName(u: RepeatUnit) = when (u) {
    RepeatUnit.HOURS -> t("ساعت", "hours")
    RepeatUnit.DAYS -> t("روز", "days")
    RepeatUnit.WEEKS -> t("هفته", "weeks")
    RepeatUnit.MONTHS -> t("ماه", "months")
    RepeatUnit.YEARS -> t("سال", "years")
    RepeatUnit.AFTER_DONE_DAYS -> t("روز پس از انجام", "days after done")
    RepeatUnit.NONE -> t("یک‌بار", "once")
}

@Composable
private fun Block(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

@Composable
private fun BigPick(icon: ImageVector, value: String, caption: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, { Text(label) })
}

@Composable
private fun DayToggle(day: DayOfWeek, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.aspectRatio(1f).clip(CircleShape).background(if (on) scheme.primary else scheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(Dates.weekdayName(day, AppDisplay.language, short = true), color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

