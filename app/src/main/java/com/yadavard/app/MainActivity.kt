package com.yadavard.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    private fun refresh() { entries.clear(); entries.addAll(store.all()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
        setContent { YadavardTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Screen() } } }
    }
    override fun onResume() { super.onResume(); refresh() }

    @Composable
    private fun Screen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val ai = remember { AiSettings(context) }
        val recorder = remember { VoiceRecorder(context) }
        var tab by remember { mutableIntStateOf(0) }
        var editor by remember { mutableStateOf<Reminder?>(null) }
        var showEditor by remember { mutableStateOf(false) }
        var review by remember { mutableStateOf<Reminder?>(null) }
        var quickText by remember { mutableStateOf("") }
        var processing by remember { mutableStateOf(false) }
        var recording by remember { mutableStateOf(false) }
        val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) try { recorder.start(); recording = true }
            catch (e: Exception) { Toast.makeText(context, "ضبط صدا شروع نشد: ${e.message}", Toast.LENGTH_LONG).show() }
        }
        fun save(r: Reminder): Reminder {
            if (r.id != 0L) store.get(r.id)?.let { ReminderAlarms.cancel(context, it) }
            val saved = store.save(r)
            ReminderAlarms.schedule(context, saved)
            refresh()
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                Toast.makeText(context, "برای ثبت هوشمند، کلید OpenRouter را در تنظیمات وارد کنید", Toast.LENGTH_LONG).show()
                tab = 2; return
            }
            scope.launch {
                processing = true
                try {
                    val result = OpenRouter(context).parse(text)
                    review = save(result)
                    quickText = ""
                } catch (e: Exception) { Toast.makeText(context, e.message ?: "تحلیل انجام نشد", Toast.LENGTH_LONG).show() }
                finally { processing = false }
            }
        }
        fun voice() {
            if (recording) {
                recording = false
                val file = recorder.stop()
                if (file == null) { Toast.makeText(context, "ویس خیلی کوتاه بود", Toast.LENGTH_SHORT).show(); return }
                scope.launch {
                    processing = true
                    try {
                        val transcript = OpenRouter(context).transcribe(file)
                        val result = OpenRouter(context).parse(transcript)
                        review = save(result.copy(note = "گفته‌شده: $transcript" + if (result.note.isBlank()) "" else "\n${result.note}"))
                    } catch (e: Exception) { file.delete(); Toast.makeText(context, e.message ?: "پردازش ویس انجام نشد", Toast.LENGTH_LONG).show() }
                    finally { processing = false }
                }
            } else if (ai.key().isNullOrBlank()) {
                Toast.makeText(context, "ابتدا کلید OpenRouter را وارد کنید", Toast.LENGTH_SHORT).show(); tab = 2
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                try { recorder.start(); recording = true } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "میکروفون در دسترس نیست", Toast.LENGTH_SHORT).show()
                }
            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        Scaffold(containerColor = Canvas, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("خانه") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("تقویم") })
                NavigationBarItem(selected = false, onClick = { editor = null; showEditor = true }, icon = {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(Violet), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, "افزودن", tint = Color.White)
                    }
                }, label = { Text("جدید") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("تنظیمات") })
            }
        }) { padding ->
            when (tab) {
                0 -> HomePage(entries, quickText, { quickText = it }, processing, recording, ::process, ::voice,
                    { editor = it; showEditor = true }, ::complete)
                1 -> CalendarPage(entries, { editor = it; showEditor = true }, ::complete)
                else -> SettingsPage(ai, entries.size, onPermission = {
                    if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }, onRefresh = ::refresh)
            }
            // Each page uses the scaffold's content padding for the system and bottom navigation bars.
            Spacer(Modifier.padding(padding).height(0.dp))
        }
        if (showEditor) ReminderEditor(editor, onDismiss = { showEditor = false }, onSave = {
            save(it); showEditor = false
        }, onDelete = { r -> ReminderAlarms.cancel(context, r); store.delete(r.id); refresh(); showEditor = false })
        review?.let { r ->
            AlertDialog(onDismissRequest = { review = null }, icon = { Icon(Icons.Filled.AutoAwesome, null, tint = Violet) },
                title = { Text("یادآوری ثبت شد") }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(r.title, fontWeight = FontWeight.Bold)
                        Text("سه موعد آینده:", color = Muted)
                        Occurrences.previews(r).forEach { Text("• ${PersianDates.format(it, ZoneId.of(r.zone))}") }
                        if (r.note.isNotBlank()) Text(r.note, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }, confirmButton = { TextButton(onClick = { review = null }) { Text("درسته") } },
                dismissButton = { TextButton(onClick = { editor = r; showEditor = true; review = null }) { Text("اصلاح") } })
        }
    }
}

@Composable
private fun HomePage(items: List<Reminder>, query: String, onQuery: (String) -> Unit,
                     busy: Boolean, recording: Boolean, onParse: (String) -> Unit, onVoice: () -> Unit,
                     onEdit: (Reminder) -> Unit, onDone: (Reminder) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val active = items.filter { !it.done }.sortedBy { if (it.nextAt == 0L) Long.MIN_VALUE else it.nextAt }
    val todayItems = active.filter { it.nextAt == 0L || Instant.ofEpochMilli(it.nextAt).atZone(zone).toLocalDate() <= today }
    val upcoming = active - todayItems.toSet()
    val completed = items.filter { it.done }
    LazyColumn(Modifier.fillMaxSize().background(Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("سلام، خوش اومدی 👋", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text("چیزی از قلم نمی‌افته", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(17.dp)).background(Lilac), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Notifications, null, tint = Violet)
                }
            }
        }
        item { WeekStrip(today) }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = Violet, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.background(heroBrush).padding(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFFFDC83))
                            Spacer(Modifier.width(8.dp)); Text("یادآوری سریع", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("فقط بگو چی رو و کی یادت بندازم", color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(value = query, onValueChange = onQuery, enabled = !busy, singleLine = true,
                            placeholder = { Text("مثلاً هر ۲۰ روز قسط رو یادم بنداز") },
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                            shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { onParse(query) }, enabled = !busy && query.isNotBlank()) {
                                Icon(Icons.Filled.ArrowForward, "ثبت", tint = Violet)
                            } })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = onVoice, enabled = !busy, colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = .18f), contentColor = Color.White)) {
                                Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, null)
                                Spacer(Modifier.width(6.dp)); Text(if (recording) "پایان و ثبت ویس" else "با ویس بگو")
                            }
                            if (busy) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("امروز", todayItems.size.toString(), Lilac, Violet, Modifier.weight(1f))
                SummaryCard("در پیش رو", upcoming.size.toString(), Peach, Color(0xFFE28F55), Modifier.weight(1f))
            }
        }
        item { SectionTitle("برای امروز", "${todayItems.size} یادآوری") }
        if (todayItems.isEmpty()) item { EmptyCard("امروز کاری جا نمونده ✨") }
        items(todayItems, key = { "today_${it.id}" }) { r -> ReminderCard(r, onEdit, onDone) }
        if (upcoming.isNotEmpty()) {
            item { SectionTitle("بعدی‌ها", "${upcoming.size} یادآوری") }
            items(upcoming, key = { "future_${it.id}" }) { r -> ReminderCard(r, onEdit, onDone) }
        }
        if (completed.isNotEmpty()) {
            item { SectionTitle("انجام‌شده", "${completed.size} مورد") }
            items(completed, key = { "done_${it.id}" }) { r -> ReminderCard(r, onEdit, null) }
        }
    }
}

@Composable
private fun WeekStrip(date: LocalDate) {
    val zone = ZoneId.systemDefault()
    val names = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0L..6L).forEach { i ->
                val day = date.plusDays(i)
                val persian = PersianDates.fromMillis(day.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
                val weekday = (day.dayOfWeek.value + 1) % 7
                Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                    .background(if (i == 0L) Violet else Color.Transparent).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(names[weekday], color = if (i == 0L) Color.White else Muted, fontSize = 12.sp)
                    Text("${persian.day}", color = if (i == 0L) Color.White else Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
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
    val time = if (r.nextAt == 0L) "موعد گذشته • نیاز به پیگیری" else PersianDates.format(r.nextAt, ZoneId.of(r.zone))
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
            if (onDone != null) IconButton(onClick = { onDone(r) }) { Icon(Icons.Filled.CheckCircleOutline, "انجام شد", tint = Violet) }
        }
    }
}

private fun repeatLabel(r: Reminder) = when (r.unit) {
    RepeatUnit.NONE -> "یک‌باره"
    RepeatUnit.DAYS -> "هر ${r.every} روز"
    RepeatUnit.WEEKS -> "هر ${r.every} هفته"
    RepeatUnit.MONTHS -> "هر ${r.every} ماه، روز ${r.monthDay.takeIf { it > 0 } ?: PersianDates.fromMillis(r.firstAt, ZoneId.of(r.zone)).day}"
    RepeatUnit.YEARS -> "هر ${r.every} سال"
    RepeatUnit.AFTER_DONE_DAYS -> "${r.every} روز بعد از انجام"
}

@Composable
private fun CalendarPage(items: List<Reminder>, onEdit: (Reminder) -> Unit, onDone: (Reminder) -> Unit) {
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val zone = ZoneId.systemDefault()
    val startOfDay = selected.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfDay = selected.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val selectedItems = items.filter { !it.done }.mapNotNull { r ->
        val occurrence = if (r.nextAt in startOfDay until endOfDay) r.nextAt
            else if (r.unit !in listOf(RepeatUnit.NONE, RepeatUnit.AFTER_DONE_DAYS))
                Occurrences.nextAfter(r, startOfDay - 1) else null
        occurrence?.takeIf { it in startOfDay until endOfDay && it >= r.nextAt }?.let { r.copy(nextAt = it) }
    }
    LazyColumn(Modifier.fillMaxSize().background(Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 105.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("تقویم من", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink) }
        item { PersianMonthGrid(selected, onSelect = { selected = it }) }
        item { SectionTitle(PersianDates.format(selected.atStartOfDay(zone).toInstant().toEpochMilli(), zone).substringBefore(" •"), "${selectedItems.size} مورد") }
        if (selectedItems.isEmpty()) item { EmptyCard("برای این روز یادآوری ثبت نشده") }
        items(selectedItems) { occurrence ->
            ReminderCard(occurrence, { onEdit(items.first { it.id == occurrence.id }) },
                if (items.first { it.id == occurrence.id }.nextAt == occurrence.nextAt) onDone else null)
        }
    }
}

@Composable
fun PersianMonthGrid(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val zone = ZoneId.systemDefault()
    val current = PersianDates.fromMillis(selected.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
    var year by remember { mutableIntStateOf(current.year) }
    var month by remember { mutableIntStateOf(current.month) }
    fun shift(delta: Int) {
        val index = year * 12 + month - 1 + delta
        year = index / 12; month = index % 12 + 1
    }
    val first = Instant.ofEpochMilli(PersianDates.at(year, month, 1, 12, 0, zone)).atZone(zone).toLocalDate()
    val offset = (first.dayOfWeek.value + 1) % 7
    val length = PersianDates.monthLength(year, month, zone)
    Surface(shape = RoundedCornerShape(27.dp), color = Color.White, shadowElevation = 4.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { shift(-1) }) { Icon(Icons.Filled.ChevronRight, "ماه قبل") }
                Text("${PersianDates.monthName(month)} $year",
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { shift(1) }) { Icon(Icons.Filled.ChevronLeft, "ماه بعد") }
            }
            Row { listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { day ->
                Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
            } }
            val weeks = (offset + length + 6) / 7
            repeat(weeks) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = week * 7 + column - offset + 1
                        if (day in 1..length) {
                            val date = Instant.ofEpochMilli(PersianDates.at(year, month, day, 12, 0, zone)).atZone(zone).toLocalDate()
                            val chosen = selected == date
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                .background(if (chosen) Violet else Color.Transparent).clickable { onSelect(date) }, contentAlignment = Alignment.Center) {
                                Text("$day", color = if (chosen) Color.White else Ink, fontSize = 13.sp)
                            }
                        } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}
