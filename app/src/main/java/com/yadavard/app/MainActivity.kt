package com.yadavard.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private val store by lazy { ReminderStore(this) }
    private val entries = mutableStateListOf<Reminder>()
    private val permissionRevision = mutableIntStateOf(0)
    private var widgetCommand by mutableStateOf<Intent?>(null)
    private var exactAllowedOnLastResume: Boolean? = null
    private fun refresh() { entries.clear(); entries.addAll(store.all()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDisplay.load(this)
        widgetCommand = intent.takeIf { it.action?.startsWith("com.yadavard.app.widget.") == true }
        refresh()
        setContent { YadavardTheme { CompositionLocalProvider(LocalLayoutDirection provides
            if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) { Screen() } } }
    }
    override fun onResume() {
        super.onResume()
        val allowed = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        if (exactAllowedOnLastResume == false && allowed) ReminderAlarms.scheduleAll(this)
        exactAllowedOnLastResume = allowed
        refresh()
        WidgetUpdater.update(this)
        permissionRevision.intValue++
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetCommand = intent
    }

    @Composable
    private fun Screen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val ai = remember { AiSettings(context) }
        val recorder = remember { VoiceRecorder(context) }
        @Suppress("UNUSED_VARIABLE") val currentPermissions = permissionRevision.intValue
        var tab by remember { mutableIntStateOf(0) }
        var selectedDashboardDate by remember { mutableStateOf(LocalDate.now()) }
        var showExactPrompt by remember { mutableStateOf(false) }
        var editor by remember { mutableStateOf<Reminder?>(null) }
        var showEditor by remember { mutableStateOf(false) }
        var review by remember { mutableStateOf<Reminder?>(null) }
        var quickText by remember { mutableStateOf("") }
        var processing by remember { mutableStateOf(false) }
        var recording by remember { mutableStateOf(false) }
        val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
                showExactPrompt = true
        }
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) try { recorder.start(); recording = true }
            catch (e: Exception) { Toast.makeText(context, t("ضبط صدا شروع نشد: ", "Could not start recording: ") + e.message, Toast.LENGTH_LONG).show() }
        }
        fun save(r: Reminder): Reminder {
            if (r.id != 0L) store.get(r.id)?.let { ReminderAlarms.cancel(context, it) }
            val saved = store.save(r)
            ReminderAlarms.schedule(context, saved)
            refresh()
            WidgetUpdater.update(context)
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
                showExactPrompt = true
            return saved
        }
        fun complete(r: Reminder) {
            ReminderAlarms.cancel(context, r)
            val now = System.currentTimeMillis()
            val changed = if (r.unit == RepeatUnit.AFTER_DONE_DAYS) {
                val next = Instant.ofEpochMilli(now).atZone(ZoneId.of(r.zone)).plusDays(r.every.toLong()).toInstant().toEpochMilli()
                r.copy(nextAt = next, lastCompletedAt = now, snoozeAt = 0)
            } else r.copy(done = r.unit == RepeatUnit.NONE, lastCompletedAt = now, snoozeAt = 0)
            save(changed)
        }
        fun process(text: String) {
            if (text.isBlank() || processing) return
            if (ai.key().isNullOrBlank()) {
                Toast.makeText(context, t("برای ثبت هوشمند، کلید OpenRouter را در تنظیمات وارد کنید", "Enter an OpenRouter key in settings to use AI"), Toast.LENGTH_LONG).show()
                tab = 2; return
            }
            scope.launch {
                processing = true
                try {
                    val result = OpenRouter(context).parse(text)
                    review = save(result)
                    quickText = ""
                } catch (e: Exception) { Toast.makeText(context, e.message ?: t("تحلیل انجام نشد", "Could not parse reminder"), Toast.LENGTH_LONG).show() }
                finally { processing = false }
            }
        }
        fun voice() {
            if (recording) {
                recording = false
                val file = recorder.stop()
                if (file == null) { Toast.makeText(context, t("ویس خیلی کوتاه بود", "Recording was too short"), Toast.LENGTH_SHORT).show(); return }
                scope.launch {
                    processing = true
                    try {
                        val transcript = OpenRouter(context).transcribe(file)
                        val result = OpenRouter(context).parse(transcript)
                        review = save(result.copy(note = t("گفته‌شده: ", "Spoken: ") + transcript + if (result.note.isBlank()) "" else "\n${result.note}"))
                    } catch (e: Exception) { file.delete(); Toast.makeText(context, e.message ?: t("پردازش ویس انجام نشد", "Could not process voice"), Toast.LENGTH_LONG).show() }
                    finally { processing = false }
                }
            } else if (ai.key().isNullOrBlank()) {
                Toast.makeText(context, t("ابتدا کلید OpenRouter را وارد کنید", "Enter your OpenRouter key first"), Toast.LENGTH_SHORT).show(); tab = 2
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                try { recorder.start(); recording = true } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: t("میکروفون در دسترس نیست", "Microphone unavailable"), Toast.LENGTH_SHORT).show()
                }
            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        LaunchedEffect(widgetCommand) {
            val command = widgetCommand ?: return@LaunchedEffect
            when (command.action) {
                WidgetCommands.ADD -> { editor = null; showEditor = true }
                WidgetCommands.VOICE -> { tab = 0; if (!recording) voice() }
                WidgetCommands.HOME -> { tab = 0; selectedDashboardDate = LocalDate.now() }
                WidgetCommands.DAY -> {
                    tab = 0
                    selectedDashboardDate = LocalDate.ofEpochDay(command.getLongExtra(WidgetCommands.EXTRA_DAY, LocalDate.now().toEpochDay()))
                }
                WidgetCommands.WEEK -> tab = 1
                WidgetCommands.EDIT -> {
                    store.get(command.getLongExtra(WidgetCommands.EXTRA_ID, 0))?.let { editor = it; showEditor = true }
                }
            }
            widgetCommand = null
        }

        Scaffold(containerColor = Canvas, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text(t("خانه", "Home")) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text(t("تقویم", "Calendar")) })
                NavigationBarItem(selected = false, onClick = { editor = null; showEditor = true }, icon = {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(Violet), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, t("افزودن", "Add"), tint = Color.White)
                    }
                }, label = { Text(t("جدید", "New")) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text(t("تنظیمات", "Settings")) })
            }
        }) { padding ->
            when (tab) {
                0 -> HomePage(entries, selectedDashboardDate, { selectedDashboardDate = it },
                    { tab = 3 }, quickText, { quickText = it }, processing, recording, ::process, ::voice,
                    { editor = it; showEditor = true }, ::complete)
                1 -> CalendarPage(entries, { editor = it; showEditor = true }, ::complete)
                3 -> NotificationCenter(entries, { tab = 0 },
                    { if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) },
                    { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    { editor = it; showEditor = true }, ::complete)
                else -> SettingsPage(ai, entries.size, onPermission = {
                    if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }, onRefresh = ::refresh)
            }
            // Each page uses the scaffold's content padding for the system and bottom navigation bars.
            Spacer(Modifier.padding(padding).height(0.dp))
        }
        if (showEditor) ReminderEditor(editor, onDismiss = { showEditor = false }, onSave = {
            save(it); showEditor = false
        }, onDelete = { r -> ReminderAlarms.cancel(context, r); store.delete(r.id); refresh(); WidgetUpdater.update(context); showEditor = false })
        review?.let { r ->
            AlertDialog(onDismissRequest = { review = null }, icon = { Icon(Icons.Filled.AutoAwesome, null, tint = Violet) },
                title = { Text(t("یادآوری ثبت شد", "Reminder saved")) }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(r.title, fontWeight = FontWeight.Bold)
                        Text(t("سه موعد آینده:", "Next three dates:"), color = Muted)
                        Occurrences.previews(r).forEach { Text("• ${AppDisplay.dateTime(it, ZoneId.of(r.zone))}") }
                        if (r.note.isNotBlank()) Text(r.note, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }, confirmButton = { TextButton(onClick = { review = null }) { Text(t("درسته", "Looks good")) } },
                dismissButton = { TextButton(onClick = { editor = r; showEditor = true; review = null }) { Text(t("اصلاح", "Edit")) } })
        }
        if (showExactPrompt) AlertDialog(onDismissRequest = { showExactPrompt = false },
            icon = { Icon(Icons.Filled.NotificationsActive, null, tint = Violet) },
            title = { Text(t("یادآوری سرِ وقت", "On-time reminders")) },
            text = { Text(t("برای اعلان دقیق، دسترسی «آلارم‌ها و یادآوری‌ها» را فعال کن. بدون آن Android ممکن است اعلان را دیر نشان دهد.",
                "Allow Alarms & reminders for on-time alerts. Without this permission, Android may deliver them late.")) },
            confirmButton = { TextButton(onClick = {
                showExactPrompt = false
                if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }) { Text(t("باز کردن تنظیمات", "Open settings")) } },
            dismissButton = { TextButton(onClick = { showExactPrompt = false; tab = 3 }) { Text(t("بعداً", "Later")) } })
    }
}

@Composable
private fun HomePage(items: List<Reminder>, selectedDate: LocalDate, onSelectDate: (LocalDate) -> Unit,
                     onBell: () -> Unit, query: String, onQuery: (String) -> Unit,
                     busy: Boolean, recording: Boolean, onParse: (String) -> Unit, onVoice: () -> Unit,
                     onEdit: (Reminder) -> Unit, onDone: (Reminder) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val active = items.filter { !it.done }.sortedBy { if (it.nextAt == 0L) Long.MIN_VALUE else it.nextAt }
    val dayItems = active.mapNotNull { r ->
        val occurrence = occurrenceOn(r, selectedDate, zone)
        occurrence?.let { if (it == 0L) r else r.copy(nextAt = it) }
    }
    val dayIds = dayItems.map { it.id }.toSet()
    val upcoming = active.filter { it.id !in dayIds && it.nextAt > 0 }
    val completed = items.filter { it.done }
    val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactAllowed = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    LazyColumn(Modifier.fillMaxSize().background(Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(t("سلام، خوش اومدی 👋", "Hello, welcome 👋"), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(t("چیزی از قلم نمی‌افته", "Stay on top of everything"), color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onBell, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(Lilac)) {
                    Icon(Icons.Filled.Notifications, t("اعلان‌ها", "Notifications"), tint = Violet)
                }
            }
        }
        item { WeekStrip(today, selectedDate, onSelectDate) }
        if (!notificationsAllowed || !exactAllowed) item {
            Surface(onClick = onBell, shape = RoundedCornerShape(18.dp), color = Peach) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFC86731))
                    Spacer(Modifier.width(8.dp))
                    Text(t("برای اعلان به‌موقع، دسترسی‌های گوشی را بررسی کن", "Check phone permissions for timely alerts"), color = Ink, fontSize = 13.sp)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = Violet, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.background(heroBrush).padding(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFFFDC83))
                            Spacer(Modifier.width(8.dp)); Text(t("یادآوری سریع", "Quick reminder"), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(t("فقط بگو چی رو و کی یادت بندازم", "Tell me what and when to remind you"), color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(value = query, onValueChange = onQuery, enabled = !busy, singleLine = true,
                            placeholder = { Text(t("مثلاً هر ۲۰ روز قسط رو یادم بنداز", "For example, remind me to pay rent every month")) },
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                            shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { onParse(query) }, enabled = !busy && query.isNotBlank()) {
                                Icon(Icons.Filled.ArrowForward, t("ثبت", "Create"), tint = Violet)
                            } })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = onVoice, enabled = !busy, colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = .18f), contentColor = Color.White)) {
                                Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, null)
                                Spacer(Modifier.width(6.dp)); Text(if (recording) t("پایان و ثبت ویس", "Stop and save voice") else t("با ویس بگو", "Use voice"))
                            }
                            if (busy) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(if (selectedDate == today) t("امروز", "Today") else t("روز انتخابی", "Selected day"), AppDisplay.number(dayItems.size), Lilac, Violet, Modifier.weight(1f))
                SummaryCard(t("در پیش رو", "Upcoming"), AppDisplay.number(upcoming.size), Peach, Color(0xFFE28F55), Modifier.weight(1f))
            }
        }
        item { SectionTitle(if (selectedDate == today) t("برای امروز", "For today") else AppDisplay.date(selectedDate.atStartOfDay(zone).toInstant().toEpochMilli(), zone),
            t("${AppDisplay.number(dayItems.size)} یادآوری", "${AppDisplay.number(dayItems.size)} reminders")) }
        if (dayItems.isEmpty()) item { EmptyCard(t("برای این روز کاری ثبت نشده ✨", "Nothing scheduled for this day ✨")) }
        items(dayItems, key = { "selected_${it.id}" }) { shown ->
            val actual = items.first { it.id == shown.id }
            ReminderCard(shown, { onEdit(actual) }, if (actual.nextAt == shown.nextAt) onDone else null)
        }
        if (upcoming.isNotEmpty()) {
            item { SectionTitle(t("بعدی‌ها", "Coming up"), t("${AppDisplay.number(upcoming.size)} یادآوری", "${AppDisplay.number(upcoming.size)} reminders")) }
            items(upcoming, key = { "future_${it.id}" }) { r -> ReminderCard(r, onEdit, onDone) }
        }
        if (completed.isNotEmpty()) {
            item { SectionTitle(t("انجام‌شده", "Completed"), t("${AppDisplay.number(completed.size)} مورد", "${AppDisplay.number(completed.size)} items")) }
            items(completed, key = { "done_${it.id}" }) { r -> ReminderCard(r, onEdit, null) }
        }
    }
}

internal fun occurrenceOn(r: Reminder, date: LocalDate, zone: ZoneId): Long? {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    if (date == LocalDate.now(zone) && r.nextAt == 0L) return 0L
    if (date == LocalDate.now(zone) && r.unit == RepeatUnit.NONE && r.nextAt in 1L until start) return r.nextAt
    if (r.nextAt in start until end) return r.nextAt
    if (r.unit in listOf(RepeatUnit.NONE, RepeatUnit.AFTER_DONE_DAYS)) return null
    return Occurrences.nextAfter(r, start - 1)?.takeIf { it in start until end && it >= r.nextAt }
}

@Composable
private fun WeekStrip(date: LocalDate, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val zone = ZoneId.systemDefault()
    val first = AppDisplay.weekStart(selected, AppDisplay.calendar)
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 4.dp) {
        Column(Modifier.padding(vertical = 7.dp, horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(selected.minusWeeks(1)) }) { Icon(Icons.Filled.ChevronRight, t("هفتهٔ قبل", "Previous week")) }
            Text(AppDisplay.date(selected.atStartOfDay(zone).toInstant().toEpochMilli(), zone),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onSelect(date) }) { Text(t("امروز", "Today"), fontSize = 12.sp) }
            IconButton(onClick = { onSelect(selected.plusWeeks(1)) }) { Icon(Icons.Filled.ChevronLeft, t("هفتهٔ بعد", "Next week")) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0L..6L).forEach { i ->
                val day = first.plusDays(i)
                val dayNumber = AppDisplay.parts(day.atStartOfDay(zone).toInstant().toEpochMilli(), zone, AppDisplay.calendar).day
                val chosen = day == selected
                Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                    .background(if (chosen) Violet else Color.Transparent).clickable { onSelect(day) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(AppDisplay.shortWeekday(day), color = if (chosen) Color.White else Muted, fontSize = 12.sp)
                    Text(AppDisplay.number(dayNumber), color = if (chosen) Color.White else Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
}

@Composable
private fun NotificationCenter(items: List<Reminder>, onBack: () -> Unit, onExact: () -> Unit,
                               onAppSettings: () -> Unit, onEdit: (Reminder) -> Unit, onDone: (Reminder) -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val readRevision = revision
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    val notificationAllowed = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactAllowed = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val active = items.filter { !it.done }.sortedBy { if (it.nextAt == 0L) Long.MIN_VALUE else it.nextAt }
    LazyColumn(Modifier.fillMaxSize().background(Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 108.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, t("بازگشت", "Back")) }
                Spacer(Modifier.width(8.dp))
                Text(t("اعلان‌ها", "Notifications"), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("آماده برای یادآوری؟", "Ready to remind you?"), fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp)
                    Text(if (notificationAllowed) t("✓ اجازهٔ اعلان فعال است", "✓ Notifications allowed") else t("● اجازهٔ اعلان غیرفعال است", "● Notifications disabled"), color = if (notificationAllowed) Violet else Color(0xFFC86731))
                    Text(if (exactAllowed) t("✓ آلارم دقیق فعال است", "✓ Exact alarms enabled") else t("● آلارم دقیق غیرفعال است؛ اعلان می‌تواند دیر برسد", "● Exact alarms disabled; alerts may be late"), color = if (exactAllowed) Violet else Color(0xFFC86731))
                    if (!notificationAllowed) Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(t("فعال‌کردن اعلان", "Enable notifications")) }
                    if (!exactAllowed) Button(onClick = onExact) { Text(t("فعال‌کردن آلارم دقیق", "Enable exact alarms")) }
                    OutlinedButton(onClick = onAppSettings) { Text(t("تنظیمات اعلان و باتری گوشی", "Phone notification and battery settings")) }
                    Text(t("اگر در پس‌زمینه اعلان دیر می‌رسد، در تنظیمات گوشی محدودیت باتری این برنامه را نیز بررسی کن.", "If alerts arrive late, check this app's battery restrictions in phone settings."), color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            OutlinedButton(onClick = {
                ReminderAlarms.scheduleTest(context)
                Toast.makeText(context, if (exactAllowed) t("با دکمهٔ خانه خارج شو؛ اعلان آزمون حدود دو دقیقهٔ دیگر می‌آید", "Go home; a test alert will appear in about two minutes")
                    else t("آزمون ثبت شد؛ بدون آلارم دقیق ممکن است دیر برسد", "Test scheduled; without exact alarms it may be late"), Toast.LENGTH_LONG).show()
            }, enabled = notificationAllowed, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AccessTime, null)
                Spacer(Modifier.width(8.dp)); Text(t("آزمون اعلان در دو دقیقه", "Test notification in two minutes"))
            }
        }
        item { SectionTitle(t("یادآوری‌های فعال", "Active reminders"), t("${AppDisplay.number(active.size)} مورد", "${AppDisplay.number(active.size)} items")) }
        if (active.isEmpty()) item { EmptyCard(t("هنوز یادآوری فعالی نداری", "No active reminders yet")) }
        items(active, key = { "notice_${it.id}" }) { r -> ReminderCard(r, onEdit, onDone) }
    }
}

@Composable
private fun SummaryCard(label: String, count: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(23.dp)).background(bg).padding(18.dp)) {
        Text(label, color = Ink, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp)); Text(count, color = fg, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(24.dp), textAlign = TextAlign.Center, color = Muted)
    }
}

@Composable
private fun ReminderCard(r: Reminder, onEdit: (Reminder) -> Unit, onDone: ((Reminder) -> Unit)?) {
    val bg = when ((r.id % 3).toInt()) { 0 -> Lilac; 1 -> Peach; else -> Mint }
    val time = if (r.nextAt == 0L) t("موعد گذشته • نیاز به پیگیری", "Overdue • needs attention") else AppDisplay.dateTime(r.nextAt, ZoneId.of(r.zone))
    Surface(onClick = { onEdit(r) }, shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(47.dp).clip(RoundedCornerShape(15.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.NotificationsActive, null, tint = Violet)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp)); Text(time, color = Muted, fontSize = 12.sp)
                if (r.unit != RepeatUnit.NONE) Text(repeatLabel(r), color = Violet, fontSize = 11.sp)
            }
            if (onDone != null) IconButton(onClick = { onDone(r) }) { Icon(Icons.Filled.CheckCircleOutline, t("انجام شد", "Done"), tint = Violet) }
        }
    }
}

private fun repeatLabel(r: Reminder): String {
    val n = AppDisplay.number(r.every)
    val calendar = if (r.calendar == CalendarSystem.PERSIAN) t("شمسی", "Persian") else t("میلادی", "Gregorian")
    val day = AppDisplay.number(r.monthDay.takeIf { it > 0 }
        ?: AppDisplay.parts(r.firstAt, ZoneId.of(r.zone), r.calendar).day)
    return when (r.unit) {
        RepeatUnit.NONE -> t("یک‌باره", "Once")
        RepeatUnit.DAYS -> t("هر $n روز", "Every $n days")
        RepeatUnit.WEEKS -> t("هر $n هفته", "Every $n weeks")
        RepeatUnit.MONTHS -> t("هر $n ماه $calendar، روز $day", "Every $n $calendar months, day $day")
        RepeatUnit.YEARS -> t("هر $n سال $calendar", "Every $n $calendar years")
        RepeatUnit.AFTER_DONE_DAYS -> t("$n روز بعد از انجام", "$n days after completion")
    }
}

@Composable
private fun CalendarPage(items: List<Reminder>, onEdit: (Reminder) -> Unit, onDone: (Reminder) -> Unit) {
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val zone = ZoneId.systemDefault()
    val selectedItems = items.filter { !it.done }.mapNotNull { r ->
        occurrenceOn(r, selected, zone)?.let { if (it == 0L) r else r.copy(nextAt = it) }
    }
    LazyColumn(Modifier.fillMaxSize().background(Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 105.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(t("تقویم من", "My calendar"), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink) }
        item { CalendarMonthGrid(selected, onSelect = { selected = it }) }
        item { SectionTitle(AppDisplay.date(selected.atStartOfDay(zone).toInstant().toEpochMilli(), zone),
            t("${AppDisplay.number(selectedItems.size)} مورد", "${AppDisplay.number(selectedItems.size)} items")) }
        if (selectedItems.isEmpty()) item { EmptyCard(t("برای این روز یادآوری ثبت نشده", "No reminders scheduled for this day")) }
        items(selectedItems) { occurrence ->
            ReminderCard(occurrence, { onEdit(items.first { it.id == occurrence.id }) },
                if (items.first { it.id == occurrence.id }.nextAt == occurrence.nextAt) onDone else null)
        }
    }
}

@Composable
fun CalendarMonthGrid(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val zone = ZoneId.systemDefault()
    val system = AppDisplay.calendar
    val current = AppDisplay.parts(selected.atStartOfDay(zone).toInstant().toEpochMilli(), zone, system)
    var year by remember(system) { mutableIntStateOf(current.year) }
    var month by remember(system) { mutableIntStateOf(current.month) }
    fun shift(delta: Int) {
        val index = year * 12 + month - 1 + delta
        year = index / 12; month = index % 12 + 1
    }
    val first = Instant.ofEpochMilli(AppDisplay.at(year, month, 1, 12, 0, zone, system)).atZone(zone).toLocalDate()
    val offset = (first.dayOfWeek.value - AppDisplay.firstDayOfWeek(system) + 7) % 7
    val length = AppDisplay.monthLength(year, month, zone, system)
    Surface(shape = RoundedCornerShape(27.dp), color = Color.White, shadowElevation = 4.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { shift(-1) }) { Icon(Icons.Filled.ChevronRight, t("ماه قبل", "Previous month")) }
                Text(AppDisplay.date(first.atStartOfDay(zone).toInstant().toEpochMilli(), zone).substringAfter(' '),
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { shift(1) }) { Icon(Icons.Filled.ChevronLeft, t("ماه بعد", "Next month")) }
            }
            Row { (0..6).forEach { index ->
                Text(AppDisplay.shortWeekday(first.minusDays(offset.toLong()).plusDays(index.toLong())),
                    Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
            } }
            val weeks = (offset + length + 6) / 7
            repeat(weeks) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = week * 7 + column - offset + 1
                        if (day in 1..length) {
                            val date = Instant.ofEpochMilli(AppDisplay.at(year, month, day, 12, 0, zone, system)).atZone(zone).toLocalDate()
                            val chosen = selected == date
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                .background(if (chosen) Violet else Color.Transparent).clickable { onSelect(date) }, contentAlignment = Alignment.Center) {
                                Text(AppDisplay.number(day), color = if (chosen) Color.White else Ink, fontSize = 13.sp)
                            }
                        } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}
