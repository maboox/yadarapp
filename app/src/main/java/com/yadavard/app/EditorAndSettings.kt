package com.yadavard.app

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun ReminderEditor(original: Reminder?, onDismiss: () -> Unit, onSave: (Reminder) -> Unit, onDelete: (Reminder) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val initial = original?.nextAt?.takeIf { it > 0 } ?: original?.firstAt
        ?: Instant.now().atZone(zone).plusHours(1).withMinute(0).toInstant().toEpochMilli()
    val repeatCalendar = original?.calendar ?: AppDisplay.calendar
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var note by remember(original?.id) { mutableStateOf(original?.note.orEmpty()) }
    var dateTime by remember(original?.id) { mutableLongStateOf(initial) }
    var unit by remember(original?.id) { mutableStateOf(original?.unit ?: RepeatUnit.NONE) }
    var every by remember(original?.id) { mutableStateOf((original?.every ?: 1).toString()) }
    var weekdays by remember(original?.id) { mutableIntStateOf(original?.weekdays ?: 0) }
    var monthDay by remember(original?.id) { mutableStateOf((original?.monthDay?.takeIf { it > 0 }
        ?: AppDisplay.parts(initial, zone, repeatCalendar).day).toString()) }
    var lead by remember(original?.id) { mutableStateOf((original?.leadMinutes ?: 0).toString()) }
    var until by remember(original?.id) { mutableStateOf(original?.untilAt) }
    var customRepeat by remember(original?.id) { mutableStateOf(original?.unit == RepeatUnit.AFTER_DONE_DAYS || (original?.every ?: 1) != 1) }
    var advanced by remember(original?.id) { mutableStateOf((original?.leadMinutes ?: 0) > 0 || original?.untilAt != null) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val time = Instant.ofEpochMilli(dateTime).atZone(zone)
    val localDate = time.toLocalDate()

    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text(if (original == null) t("یادآوری جدید", "New reminder") else t("ویرایش یادآوری", "Edit reminder"), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(t("چی رو یادم بنداز؟", "What should I remind you?")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(t("توضیحات (اختیاری)", "Notes (optional)")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp))
                        Text(AppDisplay.date(dateTime, zone), maxLines = 1, softWrap = false,
                            overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                            style = LocalTextStyle.current.copy(textDirection = if (AppDisplay.language == AppLanguage.FA) TextDirection.Rtl else TextDirection.Ltr))
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            dateTime = localDate.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                        }, time.hour, time.minute, true).show()
                    }) {
                        Icon(Icons.Filled.AccessTime, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp))
                        Text(AppDisplay.number("%02d:%02d".format(time.hour, time.minute)))
                    }
                }
                Text(t("چند وقت یک‌بار؟", "How often?"), color = Ink, fontWeight = FontWeight.SemiBold)
                val choices = listOf(RepeatUnit.NONE to t("یک‌بار", "Once"), RepeatUnit.DAYS to t("هر روز", "Daily"),
                    RepeatUnit.WEEKS to t("هر هفته", "Weekly"), RepeatUnit.MONTHS to t("هر ماه", "Monthly"),
                    RepeatUnit.YEARS to t("هر سال", "Yearly"))
                (0..1).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (0..2).forEach { column ->
                            val index = row * 3 + column
                            val choice = choices.getOrNull(index)
                            val label = choice?.second ?: t("سفارشی", "Custom")
                            val checked = if (choice == null) customRepeat else !customRepeat && unit == choice.first
                            FilterChip(selected = checked, onClick = {
                                if (choice == null) {
                                    customRepeat = true
                                    if (unit == RepeatUnit.NONE) unit = RepeatUnit.DAYS
                                } else {
                                    customRepeat = false
                                    unit = choice.first
                                    every = "1"
                                    if (unit == RepeatUnit.WEEKS) weekdays = 1 shl (time.dayOfWeek.value - 1)
                                    if (unit == RepeatUnit.MONTHS || unit == RepeatUnit.YEARS)
                                        monthDay = AppDisplay.parts(dateTime, zone, repeatCalendar).day.toString()
                                }
                            }, modifier = Modifier.weight(1f), label = { Text(label, fontSize = 11.sp, maxLines = 1) })
                        }
                    }
                }
                if (customRepeat) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("هر", "Every"), color = Muted)
                        OutlinedTextField(every, { every = AppDisplay.numericInput(it, 4) },
                            modifier = Modifier.width(85.dp), label = { Text(t("تعداد", "Count")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { repeatMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(unitLabel(unit), maxLines = 2, fontSize = 12.sp)
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                                listOf(RepeatUnit.DAYS, RepeatUnit.WEEKS, RepeatUnit.MONTHS,
                                    RepeatUnit.YEARS, RepeatUnit.AFTER_DONE_DAYS).forEach { option ->
                                    DropdownMenuItem(text = { Text(unitLabel(option)) }, onClick = {
                                        unit = option; repeatMenu = false
                                        if (unit == RepeatUnit.WEEKS && weekdays == 0) weekdays = 1 shl (time.dayOfWeek.value - 1)
                                        if (unit == RepeatUnit.MONTHS || unit == RepeatUnit.YEARS)
                                            monthDay = AppDisplay.parts(dateTime, zone, repeatCalendar).day.toString()
                                    })
                                }
                            }
                        }
                    }
                    Text(if (unit == RepeatUnit.AFTER_DONE_DAYS) t("فاصله از زمانی حساب می‌شود که «انجام شد» را بزنی.", "Count from when you mark it done.")
                        else t("فاصله از تاریخ شروع حساب می‌شود.", "Count from the start date."), color = Muted, fontSize = 12.sp)
                }
                if (unit == RepeatUnit.WEEKS) {
                    Text(t("کدام روزهای هفته؟", "Which weekdays?"), color = Muted, fontSize = 12.sp)
                    val names = (1..7).map { AppDisplay.shortWeekday(LocalDate.of(2026, 9, 28).plusDays((it - 1).toLong())) } // ISO Mon..Sun
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        names.forEachIndexed { i, label ->
                            val checked = weekdays and (1 shl i) != 0
                            FilterChip(checked, onClick = { weekdays = weekdays xor (1 shl i) }, label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.padding(horizontal = 1.dp))
                        }
                    }
                }
                if (unit == RepeatUnit.MONTHS || unit == RepeatUnit.YEARS) {
                    OutlinedTextField(monthDay, { monthDay = AppDisplay.numericInput(it, 2) },
                        label = { Text(if (repeatCalendar == CalendarSystem.PERSIAN) t("چندم ماه شمسی؟", "Day of Persian month?") else t("چندم ماه میلادی؟", "Day of Gregorian month?")) },
                        supportingText = { Text(t("مثلاً ۲۰؛ اگر آن روز نبود، آخر ماه", "For example 20; shorter months use their last day")) }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                if (dateTime > System.currentTimeMillis() && every.toIntOrNull()?.let { it in 1..3650 } == true &&
                    (unit !in listOf(RepeatUnit.MONTHS, RepeatUnit.YEARS) || monthDay.toIntOrNull()?.let { it in 1..31 } == true) &&
                    (unit != RepeatUnit.WEEKS || weekdays != 0)) {
                    val example = runCatching {
                        val draft = Occurrences.alignFirst(Reminder(title = title.ifBlank { t("یادآوری", "Reminder") }, firstAt = dateTime,
                            unit = unit, every = every.toInt(), weekdays = weekdays,
                            monthDay = monthDay.toIntOrNull() ?: 0, untilAt = until, zone = zone.id, calendar = repeatCalendar))
                        Occurrences.previews(draft).filter { until == null || it <= until!! }
                    }.getOrDefault(emptyList())
                    if (example.isNotEmpty()) Text(t("موعدهای بعدی: ", "Next dates: ") + example.joinToString("، ") { AppDisplay.dateTime(it, zone) },
                        color = Violet, fontSize = 12.sp)
                }
                TextButton(onClick = { advanced = !advanced }) {
                    Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    Text(t("تنظیمات بیشتر", "More options"))
                }
                if (advanced) {
                    OutlinedTextField(lead, { lead = AppDisplay.numericInput(it, 6) },
                        label = { Text(t("چند دقیقه زودتر خبر بده؟ (۰ = خیر)", "Notify how many minutes early? (0 = no)")) }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    if (unit != RepeatUnit.NONE) {
                        OutlinedButton(onClick = { pickingEnd = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (until == null) t("تاریخ پایان تکرار (اختیاری)", "End date (optional)") else t("تا ", "Until ") + AppDisplay.date(until!!, zone))
                        }
                        if (until != null) TextButton(onClick = { until = null }) { Text(t("بدون تاریخ پایان", "No end date")) }
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (original != null) TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, null); Text(t("حذف یادآوری", "Delete reminder"))
                }
            }
        },
        confirmButton = { Button(onClick = {
            val interval = every.toIntOrNull() ?: 0
            val day = monthDay.toIntOrNull() ?: 0
            val notice = lead.toIntOrNull() ?: 0
            error = when {
                title.isBlank() -> t("عنوان یادآوری را بنویسید", "Enter a reminder title")
                dateTime <= System.currentTimeMillis() && original == null -> t("زمان اولین یادآوری باید در آینده باشد", "The first due date must be in the future")
                unit != RepeatUnit.NONE && interval !in 1..3650 -> t("فاصلهٔ تکرار باید بین ۱ تا ۳۶۵۰ باشد", "The interval must be between 1 and 3650")
                unit in listOf(RepeatUnit.MONTHS, RepeatUnit.YEARS) && day !in 1..31 -> t("روز ماه باید بین ۱ تا ۳۱ باشد", "The day must be between 1 and 31")
                unit == RepeatUnit.WEEKS && weekdays == 0 -> t("دست‌کم یک روز هفته را انتخاب کنید", "Select at least one weekday")
                notice !in 0..525600 -> t("زمان اعلان زودتر معتبر نیست", "Invalid advance notice")
                until != null && until!! < dateTime -> t("تاریخ پایان قبل از شروع است", "End date is before the start")
                else -> ""
            }
            if (error.isNotBlank()) return@Button
            onSave(Occurrences.alignFirst(Reminder(id = original?.id ?: 0, title = title.trim(), note = note.trim(), firstAt = dateTime,
                unit = unit, every = interval.coerceAtLeast(1), weekdays = weekdays,
                monthDay = if (unit in listOf(RepeatUnit.MONTHS, RepeatUnit.YEARS)) day else 0,
                leadMinutes = notice, untilAt = until, zone = zone.id, calendar = repeatCalendar)))
        }) { Text(t("ذخیره", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("انصراف", "Cancel")) } })

    if (pickingDate || pickingEnd) {
        var selected by remember(pickingDate, pickingEnd) { mutableStateOf(if (pickingEnd) (until?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: localDate) else localDate) }
        AlertDialog(onDismissRequest = { pickingDate = false; pickingEnd = false },
            title = { Text(if (pickingEnd) t("پایان تکرار", "End of repeat") else t("انتخاب تاریخ", "Choose date")) },
            text = { CalendarMonthGrid(selected, { selected = it }) },
            confirmButton = { TextButton(onClick = {
                if (pickingEnd) until = selected.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                else {
                    if (monthDay == AppDisplay.parts(dateTime, zone, repeatCalendar).day.toString())
                        monthDay = AppDisplay.parts(selected.atStartOfDay(zone).toInstant().toEpochMilli(), zone, repeatCalendar).day.toString()
                    dateTime = selected.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
                    if (unit == RepeatUnit.WEEKS && !customRepeat) weekdays = 1 shl (selected.dayOfWeek.value - 1)
                }
                pickingDate = false; pickingEnd = false
            }) { Text(t("انتخاب", "Select")) } },
            dismissButton = { TextButton(onClick = { pickingDate = false; pickingEnd = false }) { Text(t("انصراف", "Cancel")) } })
    }
    if (confirmDelete && original != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(t("حذف شود؟", "Delete reminder?")) }, text = { Text(t("این یادآوری و اعلان‌های آینده‌اش حذف می‌شود.", "This reminder and its future alerts will be deleted.")) },
        confirmButton = { TextButton(onClick = { onDelete(original) }) { Text(t("حذف", "Delete")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("بی‌خیال", "Keep it")) } })
}

private fun unitLabel(unit: RepeatUnit) = when (unit) {
    RepeatUnit.DAYS -> t("روز", "day(s)")
    RepeatUnit.AFTER_DONE_DAYS -> t("روز بعد از انجام", "day(s) after done")
    RepeatUnit.WEEKS -> t("هفته", "week(s)")
    RepeatUnit.MONTHS -> t("ماه", "month(s)")
    RepeatUnit.YEARS -> t("سال", "year(s)")
    else -> t("بار", "time(s)")
}

@Composable
fun SettingsPage(ai: AiSettings, count: Int, onPermission: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyText by remember { mutableStateOf("") }
    var textModel by remember { mutableStateOf(ai.textModel) }
    var audioModel by remember { mutableStateOf(ai.audioModel) }
    var hasKey by remember { mutableStateOf(ai.key() != null) }
    var selectingSpeech by remember { mutableStateOf<Boolean?>(null) }
    var modelLoading by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf("") }
    var modelList by remember { mutableStateOf<List<OpenRouter.Model>>(emptyList()) }
    var modelSearch by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    fun openModels(speech: Boolean) {
        if (keyText.isNotBlank()) { ai.saveKey(keyText); keyText = ""; hasKey = true }
        selectingSpeech = speech
        modelList = emptyList(); modelError = ""; modelSearch = ""; freeOnly = false
        modelLoading = true
        scope.launch {
            try { modelList = OpenRouter(context).models(speech) }
            catch (e: Exception) { modelError = t("دریافت فهرست مدل‌ها انجام نشد: ", "Could not load models: ") + e.message }
            finally { modelLoading = false }
        }
    }
    var notificationsAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(ReminderStore(context).all()).toByteArray()) }
            Toast.makeText(context, t("فایل پشتیبان ذخیره شد", "Backup saved"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, t("ذخیره انجام نشد: ", "Save failed: ") + e.message, Toast.LENGTH_LONG).show() }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("فایل خوانده نشد")
            val saved = Backup.importData(json, ReminderStore(context))
            ReminderAlarms.scheduleAll(context); onRefresh(); WidgetUpdater.update(context)
            Toast.makeText(context, t("$saved یادآوری وارد شد", "$saved reminders imported"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, t("بازیابی انجام نشد: ", "Import failed: ") + e.message, Toast.LENGTH_LONG).show() }
    }
    val precise = if (Build.VERSION.SDK_INT < 31) true else context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    LazyColumn(Modifier.fillMaxSize().background(Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 105.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(t("تنظیمات", "Settings"), fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item { SettingsBox(t("زبان و تقویم", "Language and calendar")) {
            Text(t("زبان برنامه", "App language"), color = Ink, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = AppDisplay.language == AppLanguage.FA,
                    onClick = { AppDisplay.setLanguage(context, AppLanguage.FA); WidgetUpdater.update(context) }, label = { Text("فارسی") })
                FilterChip(selected = AppDisplay.language == AppLanguage.EN,
                    onClick = { AppDisplay.setLanguage(context, AppLanguage.EN); WidgetUpdater.update(context) }, label = { Text("English") })
            }
            Text(t("تقویم نمایش و یادآوری‌های جدید", "Display calendar and new reminders"), color = Ink, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = AppDisplay.calendar == CalendarSystem.PERSIAN,
                    onClick = { AppDisplay.setCalendar(context, CalendarSystem.PERSIAN); WidgetUpdater.update(context) },
                    label = { Text(t("شمسی", "Persian")) })
                FilterChip(selected = AppDisplay.calendar == CalendarSystem.GREGORIAN,
                    onClick = { AppDisplay.setCalendar(context, CalendarSystem.GREGORIAN); WidgetUpdater.update(context) },
                    label = { Text(t("میلادی", "Gregorian")) })
            }
            Text(t("تکرار ماهانه و سالانهٔ یادآوری‌های قبلی با تقویم زمان ساختشان ادامه پیدا می‌کند.",
                "Existing monthly and yearly reminders keep their original repeat calendar."), color = Muted, fontSize = 12.sp)
        } }
        item { SettingsBox(t("اعلان‌های گوشی", "Phone notifications")) {
            Text(if (notificationsAllowed) t("اجازهٔ اعلان فعال است", "Notifications allowed") else t("اجازهٔ اعلان غیرفعال است", "Notifications disabled"), color = Muted)
            if (!notificationsAllowed) Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(t("درخواست اجازهٔ اعلان", "Allow notifications")) }
            Text(if (precise) t("زمان‌بندی دقیق فعال است", "Exact alarms enabled") else t("زمان‌بندی دقیق غیرفعال است؛ اعلان ممکن است دیر برسد", "Exact alarms disabled; alerts may arrive late"), color = Muted)
            if (!precise) Button(onClick = onPermission) { Text(t("فعال کردن دسترسی آلارم", "Enable exact alarms")) }
        } }
        item { SettingsBox(t("هوش مصنوعی • اختیاری", "AI • optional")) {
            Text(if (hasKey) t("کلید OpenRouter روی همین گوشی ذخیره شده", "OpenRouter key saved on this phone") else t("برای ورودی صوتی و جمله‌ای، کلید خودت را وارد کن", "Enter your key for voice and natural language"), color = Muted)
            OutlinedTextField(keyText, { keyText = it }, label = { Text(t("کلید OpenRouter", "OpenRouter key")) },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { openModels(false) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(t("انتخاب مدل تحلیل متن", "Choose text model"), fontWeight = FontWeight.SemiBold)
                    Text(textModel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = { openModels(true) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(t("انتخاب مدل تبدیل ویس به متن", "Choose speech model"), fontWeight = FontWeight.SemiBold)
                    Text(audioModel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            var manualModels by remember { mutableStateOf(false) }
            TextButton(onClick = { manualModels = !manualModels }) { Text(t("وارد کردن شناسهٔ مدل به‌صورت دستی", "Enter model IDs manually")) }
            if (manualModels) {
                OutlinedTextField(textModel, { textModel = it }, label = { Text(t("شناسهٔ مدل تحلیل متن", "Text model ID")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(audioModel, { audioModel = it }, label = { Text(t("شناسهٔ مدل تبدیل ویس", "Speech model ID")) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (keyText.isNotBlank()) { ai.saveKey(keyText); keyText = ""; hasKey = true }
                    ai.textModel = textModel; ai.audioModel = audioModel
                    Toast.makeText(context, t("تنظیمات ذخیره شد", "Settings saved"), Toast.LENGTH_SHORT).show()
                }) { Text(t("ذخیره", "Save")) }
                if (hasKey) TextButton(onClick = { ai.saveKey(""); hasKey = false }) { Text(t("حذف کلید", "Remove key")) }
            }
            Text(t("فهرست مدل‌ها از OpenRouter دریافت می‌شود. برچسب رایگان بر اساس تعرفهٔ فعلی است؛ محدودیت مصرف و موجودی حساب را در OpenRouter بررسی کن. ویس ابتدا با مدل جداگانه به متن تبدیل می‌شود.",
                "Models come from OpenRouter. Free labels reflect current pricing; check your account's limits. Voice is transcribed with a separate model."), color = Muted, fontSize = 12.sp)
        } }
        item { SettingsBox(t("پشتیبان‌گیری • $count یادآوری", "Backup • $count reminders")) {
            Text(t("فایل JSON را در جای امن نگه دار. کلید هوش مصنوعی در آن نیست.", "Keep the JSON file safe. Your AI key is not included."), color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportPicker.launch("yadar-backup.json") }) { Text(t("خروجی", "Export")) }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "text/plain")) }) { Text(t("بازیابی", "Import")) }
            }
        } }
    }
    selectingSpeech?.let { speech ->
        AlertDialog(onDismissRequest = { selectingSpeech = null }, title = { Text(if (speech) t("مدل تبدیل ویس", "Speech model") else t("مدل تحلیل متن", "Text model")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(modelSearch, { modelSearch = it }, label = { Text(t("جست‌وجوی نام یا شناسه", "Search name or ID")) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(freeOnly, onCheckedChange = { freeOnly = it }); Text(t("فقط مدل‌های رایگان", "Free models only"))
                    }
                    if (modelLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (modelError.isNotBlank()) Text(modelError, color = MaterialTheme.colorScheme.error)
                    val filtered = modelList.filter { (!freeOnly || it.free) &&
                        (modelSearch.isBlank() || it.name.contains(modelSearch, ignoreCase = true) ||
                            it.id.contains(modelSearch, ignoreCase = true)) }
                    if (!modelLoading && modelError.isBlank() && filtered.isEmpty()) Text(t("مدلی با این شرایط پیدا نشد", "No matching models"), color = Muted)
                    LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filtered.size) { index ->
                            val model = filtered[index]
                            Surface(onClick = {
                                if (speech) { audioModel = model.id; ai.audioModel = model.id }
                                else { textModel = model.id; ai.textModel = model.id }
                                selectingSpeech = null
                            }, shape = RoundedCornerShape(12.dp), color = if (model.free) Mint else Canvas) {
                                Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                    Text(model.name + if (model.free) t(" • رایگان", " • free") else "", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(model.id, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selectingSpeech = null }) { Text(t("بستن", "Close")) } })
    }
}

@Composable
private fun SettingsBox(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp)
            content()
        }
    }
}

object Backup {
    fun export(items: List<Reminder>): String = JSONObject().put("schema", 1).put("reminders", JSONArray().apply {
        items.forEach { r -> put(JSONObject().apply {
            put("title", r.title); put("note", r.note); put("firstAt", r.firstAt); put("nextAt", r.nextAt)
            put("unit", r.unit.name); put("every", r.every); put("weekdays", r.weekdays)
            put("monthDay", r.monthDay); put("calendar", r.calendar.name)
            put("leadMinutes", r.leadMinutes); put("untilAt", r.untilAt ?: JSONObject.NULL)
            put("zone", r.zone); put("done", r.done); put("lastCompletedAt", r.lastCompletedAt)
        }) }
    }).toString(2)
    fun importData(data: String, store: ReminderStore): Int {
        val root = JSONObject(data)
        require(root.getInt("schema") == 1) { t("نسخهٔ فایل پشتیبان پشتیبانی نمی‌شود", "Unsupported backup version") }
        val array = root.getJSONArray("reminders")
        require(array.length() <= 10000) { t("فایل بیش از حد بزرگ است", "Backup is too large") }
        val existing = store.all().map { it.title to it.firstAt }.toMutableSet()
        var added = 0
        for (i in 0 until array.length()) {
            val v = array.getJSONObject(i)
            val title = v.getString("title").take(180)
            val first = v.getLong("firstAt")
            if (!existing.add(title to first)) continue
            val unit = RepeatUnit.valueOf(v.getString("unit"))
            val zone = v.optString("zone", ZoneId.systemDefault().id)
            ZoneId.of(zone)
            store.save(Reminder(title = title, note = v.optString("note").take(2000), firstAt = first,
                nextAt = v.optLong("nextAt", first), unit = unit, every = v.optInt("every", 1).coerceIn(1, 3650),
                weekdays = v.optInt("weekdays"), monthDay = v.optInt("monthDay"),
                leadMinutes = v.optInt("leadMinutes"), untilAt = if (v.isNull("untilAt")) null else v.getLong("untilAt"),
                zone = zone, done = v.optBoolean("done"), lastCompletedAt = v.optLong("lastCompletedAt"),
                calendar = runCatching { CalendarSystem.valueOf(v.optString("calendar", "PERSIAN")) }.getOrDefault(CalendarSystem.PERSIAN)))
            added++
        }
        return added
    }
}
