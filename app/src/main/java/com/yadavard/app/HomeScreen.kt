@file:OptIn(ExperimentalMaterial3Api::class)

package com.yadavard.app

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(items: List<Reminder>, now: Long, padding: PaddingValues, actions: ReminderActions,
               quickText: String, onQuickText: (String) -> Unit, permissionTick: Int, onFixPermissions: () -> Unit, onProfile: () -> Unit = {},
               onGroups: () -> Unit = {}) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showFar by rememberSaveable { mutableStateOf(false) }
    var showMissed by rememberSaveable { mutableStateOf(false) }
    var todayOnly by remember { mutableStateOf(Prefs.todayOnly(context)) }
    var archiveOpen by remember { mutableStateOf(false) }

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
                    val name = Prefs.userName(context)
                    val comma = if (name.isBlank()) "" else t("، $name", ", $name")
                    Text(when (hour) {
                        in 5..11 -> t("صبح بخیر$comma ☀️", "Good morning$comma ☀️")
                        in 12..16 -> t("ظهر بخیر$comma 🌤", "Good afternoon$comma 🌤")
                        in 17..20 -> t("عصر بخیر$comma 🌇", "Good evening$comma 🌇")
                        else -> t("شب بخیر$comma 🌙", "Good night$comma 🌙")
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Dates.formatDate(today, AppDisplay.calendar, AppDisplay.language, withWeekday = true),
                        style = MaterialTheme.typography.headlineSmall)
                    val other = if (AppDisplay.calendar == CalendarSystem.PERSIAN) CalendarSystem.GREGORIAN else CalendarSystem.PERSIAN
                    Text(Dates.formatDate(today, other, AppDisplay.language), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // The streak, when there is one; a tap opens the profile.
                    Game.state?.takeIf { it.facts.streak > 1 }?.let { g ->
                        AssistChip(onClick = onProfile, modifier = Modifier.padding(top = 4.dp),
                            label = { Text(t("🔥 ${n(g.facts.streak)} روز پشت سر هم", "🔥 ${g.facts.streak}-day streak")) })
                    }
                }
                // groups (when turned on): a small door to them; the home screen stays yours
                val groups by Groups.state.collectAsState()
                if (remember(groups.tick) { Groups.enabled(context) }) FilledTonalIconButton(onClick = onGroups) {
                    BadgedBox(badge = { if (groups.invites.isNotEmpty()) Badge { Text(n(groups.invites.size)) } }) {
                        Icon(Icons.Rounded.Groups, t("گروه‌ها", "Groups"))
                    }
                }
                FilledTonalIconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, t("جست‌وجو", "Search"))
                }
            }
        }
        // Yadimo lives across the top of the home screen
        if (rememberYadimoEnabled()) item(key = "yadimo") { HomeYadimo(items, now) }
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
        if (items.isNotEmpty()) item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // "Today only" keeps the list short: what needs attention and what is due today.
                item {
                    FilterChip(todayOnly, { todayOnly = !todayOnly; Prefs.setTodayOnly(context, todayOnly) }, { Text(t("فقط امروز", "Today only")) },
                        leadingIcon = { Icon(Icons.Rounded.Today, null, Modifier.size(16.dp)) })
                }
                if (used.size > 1 || filter != null) item { FilterChip(filter == null, { filter = null }, { Text(t("همه", "All")) }) }
                if (used.size > 1 || filter != null) items(Categories.all.map { it.key }.filter { it in used || it == filter }) { c ->
                    FilterChip(filter == c, { filter = if (filter == c) null else c }, { Text(c.catLabel()) },
                        leadingIcon = { Icon(c.catIcon(), null, Modifier.size(16.dp), tint = c.catColor()) })
                }
            }
        }
        if (items.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.NotificationsActive, t("هنوز یادآوری‌ای نداری", "No reminders yet"),
                t("بالا بنویس «فردا ساعت ۹ تماس با مامان» یا دکمهٔ «یادآوری جدید» را بزن.",
                    "Type “call mom tomorrow at 9” above or tap “New reminder”."), mascot = YadimoMood.WAVE)
        } else if (sections.isEmpty) item(key = "nothing") {
            EmptyState(Icons.Rounded.SearchOff, t("موردی پیدا نشد", "Nothing found"), t("فیلتر یا جست‌وجو را تغییر بده.", "Try another filter or search."), mascot = YadimoMood.THINK)
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
        if (todayOnly) {
            val rest = sections.tomorrow.size + sections.week.size + sections.later.size + sections.far.size
            if (sections.today.isEmpty() && sections.attention.isEmpty()) item(key = "today_empty") {
                EmptyState(Icons.Rounded.EventAvailable, t("امروز کاری نداری", "Nothing for today"),
                    t("${n(rest)} یادآوری برای روزهای بعد هست؛ «فقط امروز» را خاموش کن تا ببینی.", "$rest upcoming; turn off “Today only” to see them."))
            }
        }
        if (!todayOnly) section("tomorrow", t("فردا", "Tomorrow"), sections.tomorrow, showDate = false)
        if (!todayOnly) section("week", t("این هفته", "This week"), sections.week)
        if (!todayOnly) section("later", t("تا ۲ ماه آینده", "Next 2 months"), sections.later)
        if (!todayOnly && sections.far.isNotEmpty()) {
            // Far-away reminders (e.g. yearly birthdays) stay folded unless searching or filtering.
            val open = showFar || query.isNotBlank() || filter != null
            item(key = "h_far") {
                SectionHeader(t("دورتر", "Further ahead"), sections.far.size, MaterialTheme.colorScheme.onSurfaceVariant) {
                    TextButton(onClick = { showFar = !showFar }) { Text(if (open) t("پنهان", "Hide") else t("نمایش", "Show")) }
                }
            }
            if (open) items(sections.far, key = { "far_${it.reminder.id}_${it.at}" }) { e ->
                ReminderCard(e, now, actions, Modifier.animateItem())
            }
        }
        val dayStart = Dates.startOfDay(today, zone)
        val doneShown = if (todayOnly) sections.done.filter { it.reminder.completedAt >= dayStart } else sections.done
        val missedShown = if (todayOnly) sections.missed.filter { it.reminder.completedAt >= dayStart } else sections.missed
        if (doneShown.isNotEmpty()) {
            item(key = "h_done") {
                SectionHeader(t("انجام‌شده", "Completed"), doneShown.size, MaterialTheme.colorScheme.onSurfaceVariant) {
                    TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) t("پنهان", "Hide") else t("نمایش", "Show")) }
                }
            }
            if (showDone) items(doneShown, key = { "done_${it.reminder.id}" }) { e -> ReminderCard(e, now, actions, Modifier.animateItem()) }
        }
        if (missedShown.isNotEmpty()) {
            item(key = "h_missed") {
                SectionHeader(t("انجام‌نشده", "Not done"), missedShown.size, MaterialTheme.colorScheme.error.copy(alpha = 0.8f)) {
                    TextButton(onClick = { showMissed = !showMissed }) { Text(if (showMissed) t("پنهان", "Hide") else t("نمایش", "Show")) }
                }
            }
            if (showMissed) items(missedShown, key = { "missed_${it.reminder.id}" }) { e -> ReminderCard(e, now, actions, Modifier.animateItem()) }
        }
        // Finished items older than a week are archived out of the way.
        if (!todayOnly && sections.archived.isNotEmpty()) item(key = "archive") {
            TextButton(onClick = { archiveOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Inventory2, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(t("بایگانی (${n(sections.archived.size)})", "Archive (${sections.archived.size})"))
            }
        }
    }
    if (archiveOpen) ArchiveDialog(sections.archived, now, actions) { archiveOpen = false }
}

/** Finished reminders older than a week, kept out of the main list. */
@Composable
private fun ArchiveDialog(entries: List<Entry>, now: Long, actions: ReminderActions, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = entries.filter { query.isBlank() || it.reminder.title.contains(query.trim(), true) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, t("بستن", "Close")) }
                    Text(t("بایگانی", "Archive"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t("جست‌وجو", "Search")) },
                    shape = RoundedCornerShape(16.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { "arch_${it.reminder.id}" }) { e -> ReminderCard(e, now, actions) }
                }
            }
        }
    }
}

private class Sections(
    val attention: List<Entry>, val today: List<Entry>, val tomorrow: List<Entry>,
    val week: List<Entry>, val later: List<Entry>, val far: List<Entry>, val done: List<Entry>, val missed: List<Entry>,
    val archived: List<Entry>,
    val todayCount: Int, val weekCount: Int,
) {
    val isEmpty get() = attention.isEmpty() && today.isEmpty() && tomorrow.isEmpty() && week.isEmpty() && later.isEmpty() &&
        far.isEmpty() && done.isEmpty() && missed.isEmpty() && archived.isEmpty()
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
    val horizon = Dates.startOfDay(today.plusMonths(2), zone)
    val later = upcoming.filter { it.at in weekEnd until horizon }
    val far = upcoming.filter { it.at >= horizon }
    // Hourly reminders occur many times a day; count every occurrence today for the summary.
    val dayStart = Dates.startOfDay(today, zone)
    // Each reminder counts once, however many times it repeats in the period.
    val todayCount = active.count { it.needsAttention(now) || Recurrence.occurrencesIn(it, dayStart, tomorrowStart, 1).isNotEmpty() }
    val weekCount = active.count { Recurrence.occurrencesIn(it, now, weekEnd, 1).isNotEmpty() }
    val recent = now - 7L * 24 * 3600_000
    val finished = items.filter { it.done }.sortedByDescending { it.completedAt }
    val done = finished.filter { !it.missed && it.completedAt >= recent }.map { Entry(it, it.nextAt, false) }
    val missed = finished.filter { it.missed && it.completedAt >= recent }.map { Entry(it, it.nextAt, false) }
    val archived = finished.filter { it.completedAt < recent }.map { Entry(it, it.nextAt, false) }
    return Sections(attention, todayList, tomorrow, week, later, far, done, missed, archived, todayCount, weekCount)
}

@Composable
private fun StatCard(label: String, value: Int, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = color.copy(alpha = 0.10f), modifier = modifier) {
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
    val account by Account.profile.collectAsState()
    val useAi = remember(account != null) { Prefs.useAi(context) && Ai.ready(context) }
    // Running out of Yadimo tokens turns the card red with a top-up hint.
    val lowTokens = account?.let { !it.unlimited && it.balance <= Account.LOW_BALANCE } == true && !AiSettings(context).personal
    val custom = remember(Categories.all.size) { Categories.customPairs(context) }
    // Typing must never crash the app, whatever the parser thinks of a half-written sentence.
    val parsed = remember(text, now / 60_000, custom) {
        if (text.isBlank() || useAi) null
        else runCatching { QuickParser.parse(text, System.currentTimeMillis(), zone, AppDisplay.calendar, custom) }.getOrNull()
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
        val result = runCatching { QuickParser.parse(text, System.currentTimeMillis(), zone, AppDisplay.calendar, custom) }
            .getOrElse { QuickResult(title = text.trim(), at = null) }
        val reminder = result.toReminder(zone, AppDisplay.calendar, System.currentTimeMillis())?.let(::withDefaults)
        when {
            reminder != null && result.understood && reminder.nextAt > System.currentTimeMillis() && result.title.isNotBlank() -> {
                // Saved straight away; the snackbar offers editing.
                actions.saveDirect(reminder)
                onText("")
            }
            else -> {
                val draft = reminder ?: withDefaults(draftOn(LocalDate.now()).copy(title = result.title, category = result.category))
                actions.create(draft.copy(title = result.title))
                onText("")
            }
        }
    }

    Surface(shape = MaterialTheme.shapes.large, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.linearGradient(if (lowTokens) listOf(Color(0xFF8E1F2F), Color(0xFFC0392B), Color(0xFFE5735A))
            else heroColors())).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFFFDC83), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("یادآوری سریع", "Quick add"), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    // With Yadimo tokens the badge shows the balance, so people see what is left.
                    val badge = account?.takeIf { !AiSettings(context).personal }?.let { if (it.unlimited) "AI ∞" else "AI · " + tokensLabel(it.balance) } ?: "AI"
                    if (useAi) Text(badge, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(text, onText, Modifier.weight(1f), enabled = !busy, maxLines = 3,
                        placeholder = { Text(t("مثلاً: فردا ساعت ۹ تماس با مامان", "e.g. call mom tomorrow at 9"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White, focusedTextColor = Color(0xFF1B1A26), unfocusedTextColor = Color(0xFF1B1A26),
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent, cursorColor = heroColors()[1],
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
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = heroColors()[0])) {
                            Icon(Icons.AutoMirrored.Rounded.Send, t("ثبت", "Add"))
                        }
                    }
                }
                if (lowTokens) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Account.buyRequested.value = true }
                    .background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BatteryAlert, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("توکن‌هایت رو به اتمام است (${tokensLabel(account?.balance ?: 0.0)} مانده)؛ برای ادامهٔ دستیار شارژ کن.",
                        "You're running out of tokens (${tokensLabel(account?.balance ?: 0.0)} left); top up to keep using the assistant."),
                        color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(t("شارژ", "Top up"), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
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
            val draft = runCatching { p.toReminder(zone, AppDisplay.calendar, System.currentTimeMillis()) }.getOrNull()
            val at = draft?.nextAt ?: p.at
            PreviewChip(Icons.Rounded.Event, Dates.friendlyDay(Dates.localDate(at, zone), LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
                " " + Dates.formatTime(at, zone, AppDisplay.language), white)
            if (p.unit != RepeatUnit.NONE && draft != null) PreviewChip(Icons.Rounded.Repeat, repeatLabel(draft), white)
            if (p.category != CategoryGuess.GENERAL) PreviewChip(p.category.catIcon(), p.category.catLabel(), white)
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
