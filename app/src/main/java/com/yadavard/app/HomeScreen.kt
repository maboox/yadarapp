@file:OptIn(ExperimentalMaterial3Api::class)

package com.yadavard.app

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(items: List<Reminder>, now: Long, padding: PaddingValues, actions: ReminderActions,
               quickText: String, onQuickText: (String) -> Unit, permissionTick: Int, onFixPermissions: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    val visible = remember(items, query, filter) {
        items.filter { r ->
            (filter == null || r.category == filter) &&
                (query.isBlank() || r.title.contains(query.trim(), true) || r.note.contains(query.trim(), true))
        }
    }
    val sections = remember(visible, now) { buildSections(visible, now, zone) }
    @Suppress("UNUSED_VARIABLE") val tick = permissionTick
    val notificationsOk = Notifier.allowed(context)
    val exactOk = Scheduler.canExact(context)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFixPermissions() }

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    val hour = Instant.ofEpochMilli(now).atZone(zone).hour
                    Text(when (hour) {
                        in 5..11 -> t("صبح بخیر ☀️", "Good morning ☀️")
                        in 12..16 -> t("ظهر بخیر 🌤", "Good afternoon 🌤")
                        in 17..20 -> t("عصر بخیر 🌇", "Good evening 🌇")
                        else -> t("شب بخیر 🌙", "Good night 🌙")
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Dates.formatDate(today, AppDisplay.calendar, AppDisplay.language, withWeekday = true),
                        style = MaterialTheme.typography.headlineSmall)
                    val other = if (AppDisplay.calendar == CalendarSystem.PERSIAN) CalendarSystem.GREGORIAN else CalendarSystem.PERSIAN
                    Text(Dates.formatDate(today, other, AppDisplay.language), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, t("جست‌وجو", "Search"))
                }
            }
        }
        if (searching) item(key = "search") {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(t("جست‌وجو در یادآوری‌ها", "Search reminders")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp))
        }
        if (!notificationsOk || !exactOk) item(key = "warning") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!notificationsOk && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else onFixPermissions()
                }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (!notificationsOk) t("اعلان‌ها خاموش است", "Notifications are off") else t("آلارم دقیق غیرفعال است", "Exact alarms are off"),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(t("برای اینکه یادآوری‌ها سر وقت برسند، لمس کن و فعالش کن.", "Tap to fix so reminders arrive on time."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        item(key = "quick") { QuickAddCard(quickText, onQuickText, now, actions) }
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(t("امروز", "Today"), sections.todayCount, Icons.Rounded.Today, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatCard(t("نیاز به توجه", "Attention"), sections.attention.size, Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                StatCard(t("۷ روز آینده", "Next 7 days"), sections.weekCount, Icons.Rounded.DateRange, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        val used = items.map { it.category }.toSet()
        if (used.size > 1 || filter != null) item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text(t("همه", "All")) }) }
                items(Category.entries.filter { it in used || it == filter }) { c ->
                    FilterChip(filter == c, { filter = if (filter == c) null else c }, { Text(c.label()) },
                        leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp), tint = c.color()) })
                }
            }
        }
        if (items.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.NotificationsActive, t("هنوز یادآوری‌ای نداری", "No reminders yet"),
                t("بالا بنویس «فردا ساعت ۹ تماس با مامان» یا دکمهٔ «یادآوری جدید» را بزن.",
                    "Type “call mom tomorrow at 9” above or tap “New reminder”."))
        } else if (sections.isEmpty) item(key = "nothing") {
            EmptyState(Icons.Rounded.SearchOff, t("موردی پیدا نشد", "Nothing found"), t("فیلتر یا جست‌وجو را تغییر بده.", "Try another filter or search."))
        }
        fun section(key: String, title: String, entries: List<Entry>, color: Color? = null, showDate: Boolean = true) {
            if (entries.isEmpty()) return
            item(key = "h_$key") { SectionHeader(title, entries.size, color ?: MaterialTheme.colorScheme.onSurface) }
            items(entries, key = { "${key}_${it.reminder.id}_${it.at}" }) { e ->
                ReminderCard(e, now, actions, Modifier.animateItem(), showDate = showDate)
            }
        }
        section("attention", t("نیاز به توجه", "Needs attention"), sections.attention, Color(0xFFE5484D))
        section("today", t("امروز", "Today"), sections.today, showDate = false)
        section("tomorrow", t("فردا", "Tomorrow"), sections.tomorrow, showDate = false)
        section("week", t("این هفته", "This week"), sections.week)
        section("later", t("بعدتر", "Later"), sections.later)
        if (sections.done.isNotEmpty()) {
            item(key = "h_done") {
                SectionHeader(t("انجام‌شده", "Completed"), sections.done.size, MaterialTheme.colorScheme.onSurfaceVariant) {
                    TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) t("پنهان", "Hide") else t("نمایش", "Show")) }
                }
            }
            if (showDone) items(sections.done, key = { "done_${it.reminder.id}" }) { e -> ReminderCard(e, now, actions, Modifier.animateItem()) }
        }
    }
}

private class Sections(
    val attention: List<Entry>, val today: List<Entry>, val tomorrow: List<Entry>,
    val week: List<Entry>, val later: List<Entry>, val done: List<Entry>, val todayCount: Int, val weekCount: Int,
) {
    val isEmpty get() = attention.isEmpty() && today.isEmpty() && tomorrow.isEmpty() && week.isEmpty() && later.isEmpty() && done.isEmpty()
}

private fun buildSections(items: List<Reminder>, now: Long, zone: ZoneId): Sections {
    val today = LocalDate.now(zone)
    val tomorrowStart = Dates.startOfDay(today.plusDays(1), zone)
    val dayAfterStart = Dates.startOfDay(today.plusDays(2), zone)
    val weekEnd = Dates.startOfDay(today.plusDays(7), zone)
    val active = items.filter { !it.done }
    val attention = active.filter { it.needsAttention(now) }
        .map { Entry(it, it.displayAt(now), true) }
        .sortedWith(compareByDescending<Entry> { it.reminder.important }.thenBy { it.at })
    val upcoming = active.filter { it.nextAt > 0 && it.nextAt != it.pendingAt && !(it.needsAttention(now) && !it.repeating) }
        .map { Entry(it, it.nextAt, false) }
        .sortedWith(compareBy<Entry> { it.at }.thenByDescending { it.reminder.important })
    // Repeating reminders that also need attention show their pending entry above and the next one here.
    val todayList = upcoming.filter { it.at < tomorrowStart }
    val tomorrow = upcoming.filter { it.at in tomorrowStart until dayAfterStart }
    val week = upcoming.filter { it.at in dayAfterStart until weekEnd }
    val later = upcoming.filter { it.at >= weekEnd }
    // Hourly reminders occur many times a day; count every occurrence today for the summary.
    val dayStart = Dates.startOfDay(today, zone)
    val todayCount = active.sumOf { Recurrence.occurrencesIn(it, dayStart, tomorrowStart, 48).size }
    val weekCount = active.sumOf { Recurrence.occurrencesIn(it, now, weekEnd, 200).size }
    val done = items.filter { it.done }.sortedByDescending { it.completedAt }.take(50).map { Entry(it, it.nextAt, false) }
    return Sections(attention, todayList, tomorrow, week, later, done, todayCount, weekCount)
}

@Composable
private fun StatCard(label: String, value: Int, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.10f), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(n(value), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickAddCard(text: String, onText: (String) -> Unit, now: Long, actions: ReminderActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val zone = ZoneId.systemDefault()
    var busy by remember { mutableStateOf(false) }
    val useAi = remember { Prefs.useAi(context) && AiSettings(context).hasKey() }
    val parsed = remember(text, now / 60_000) {
        if (text.isBlank()) null else QuickParser.parse(text, System.currentTimeMillis(), zone, AppDisplay.calendar)
    }
    fun withDefaults(r: Reminder) = r.copy(alertStyle = if (r.alertStyle == AlertStyle.ALARM) AlertStyle.ALARM else Prefs.defaultAlert(context),
        leadMinutes = if (r.leadMinutes > 0) r.leadMinutes else Prefs.defaultLead(context))

    fun submit() {
        if (text.isBlank() || busy) return
        focus.clearFocus()
        if (useAi) {
            busy = true
            scope.launch {
                try {
                    // The assistant handles typed requests too: create, edit, delete, questions…
                    actions.assistant(text)
                    onText("")
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: t("تحلیل انجام نشد", "Could not understand"), Toast.LENGTH_LONG).show()
                } finally { busy = false }
            }
            return
        }
        val result = QuickParser.parse(text, System.currentTimeMillis(), zone, AppDisplay.calendar)
        val reminder = result.toReminder(zone, AppDisplay.calendar, System.currentTimeMillis())?.let(::withDefaults)
        when {
            reminder != null && result.understood && reminder.nextAt > System.currentTimeMillis() && result.title.isNotBlank() -> {
                // Saved straight away; the snackbar offers editing.
                actions.saveDirect(reminder)
                onText("")
            }
            else -> {
                val draft = reminder ?: withDefaults(draftOn(LocalDate.now()).copy(title = result.title))
                actions.create(draft.copy(title = result.title))
                onText("")
            }
        }
    }

    Surface(shape = RoundedCornerShape(26.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF3B2DB0), Color(0xFF5B4BDB), Color(0xFF8F7BFF)))).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFFFDC83), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("یادآوری سریع", "Quick add"), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (useAi) Text("AI", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(text, onText, Modifier.weight(1f), enabled = !busy, maxLines = 3,
                        placeholder = { Text(t("مثلاً: فردا ساعت ۹ تماس با مامان", "e.g. call mom tomorrow at 9"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White, focusedTextColor = Color(0xFF1B1A26), unfocusedTextColor = Color(0xFF1B1A26),
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent, cursorColor = Color(0xFF5B4BDB),
                            focusedPlaceholderColor = Color(0xFF8A879C), unfocusedPlaceholderColor = Color(0xFF8A879C)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }))
                    Spacer(Modifier.width(8.dp))
                    if (busy) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
                    } else if (text.isBlank()) {
                        FilledIconButton(onClick = actions.voice, modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)) {
                            Icon(Icons.Rounded.Mic, t("گفتن با صدا", "Speak"))
                        }
                    } else {
                        FilledIconButton(onClick = ::submit, modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF3B2DB0))) {
                            Icon(Icons.AutoMirrored.Rounded.Send, t("ثبت", "Add"))
                        }
                    }
                }
                AnimatedVisibility(parsed != null && !useAi) {
                    val p = parsed
                    if (p != null) ParsePreview(p, zone)
                }
            }
        }
    }
}

@Composable
private fun ParsePreview(p: QuickResult, zone: ZoneId) {
    val white = Color.White
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (p.at != null && p.understood) {
            val draft = p.toReminder(zone, AppDisplay.calendar, System.currentTimeMillis())
            val at = draft?.nextAt ?: p.at
            PreviewChip(Icons.Rounded.Event, Dates.friendlyDay(Dates.localDate(at, zone), LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
                " " + Dates.formatTime(at, zone, AppDisplay.language), white)
            if (p.unit != RepeatUnit.NONE && draft != null) PreviewChip(Icons.Rounded.Repeat, repeatLabel(draft), white)
        } else {
            Icon(Icons.Rounded.EditCalendar, null, tint = white.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            Text(t("زمانی پیدا نشد؛ با ثبت، فرم کامل باز می‌شود", "No time found; the full form will open"),
                color = white.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PreviewChip(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
