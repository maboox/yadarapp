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

@Composable
fun ReminderEditor(original: Reminder?, onDismiss: () -> Unit, onSave: (Reminder) -> Unit, onDelete: (Reminder) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val initial = original?.nextAt?.takeIf { it > 0 } ?: original?.firstAt
        ?: Instant.now().atZone(zone).plusHours(1).withMinute(0).toInstant().toEpochMilli()
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var note by remember(original?.id) { mutableStateOf(original?.note.orEmpty()) }
    var dateTime by remember(original?.id) { mutableLongStateOf(initial) }
    var unit by remember(original?.id) { mutableStateOf(original?.unit ?: RepeatUnit.NONE) }
    var every by remember(original?.id) { mutableStateOf((original?.every ?: 1).toString()) }
    var weekdays by remember(original?.id) { mutableIntStateOf(original?.weekdays ?: 0) }
    var monthDay by remember(original?.id) { mutableStateOf((original?.monthDay?.takeIf { it > 0 }
        ?: PersianDates.fromMillis(initial, zone).day).toString()) }
    var lead by remember(original?.id) { mutableStateOf((original?.leadMinutes ?: 0).toString()) }
    var until by remember(original?.id) { mutableStateOf(original?.untilAt) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val time = Instant.ofEpochMilli(dateTime).atZone(zone)
    val localDate = time.toLocalDate()

    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text(if (original == null) "یادآوری جدید" else "ویرایش یادآوری", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("چی رو یادم بنداز؟") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp))
                        Text(PersianDates.format(dateTime, zone).substringBefore(" •"), maxLines = 1, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            dateTime = localDate.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                        }, time.hour, time.minute, true).show()
                    }) {
                        Icon(Icons.Filled.AccessTime, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp))
                        Text("%02d:%02d".format(time.hour, time.minute))
                    }
                }
                Box {
                    OutlinedButton(onClick = { repeatMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Repeat, null); Spacer(Modifier.width(8.dp)); Text("تکرار: ${repeatUnitLabel(unit)}")
                    }
                    DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                        RepeatUnit.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(repeatUnitLabel(option)) }, onClick = { unit = option; repeatMenu = false })
                        }
                    }
                }
                if (unit != RepeatUnit.NONE) {
                    OutlinedTextField(every, { every = it.filter(Char::isDigit).take(4) },
                        label = { Text("هر چند ${unitLabel(unit)}؟") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                if (unit == RepeatUnit.WEEKS) {
                    Text("روزهای هفته", color = Muted, fontSize = 12.sp)
                    val names = listOf("د", "س", "چ", "پ", "ج", "ش", "ی") // ISO Mon..Sun
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        names.forEachIndexed { i, label ->
                            val checked = weekdays and (1 shl i) != 0
                            FilterChip(checked, onClick = { weekdays = weekdays xor (1 shl i) }, label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.padding(horizontal = 1.dp))
                        }
                    }
                }
                if (unit == RepeatUnit.MONTHS || unit == RepeatUnit.YEARS) {
                    OutlinedTextField(monthDay, { monthDay = it.filter(Char::isDigit).take(2) },
                        label = { Text("روز ماه شمسی (اگر وجود نداشت، آخر ماه)") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                OutlinedTextField(lead, { lead = it.filter(Char::isDigit).take(6) },
                    label = { Text("چند دقیقه زودتر هم خبر بده؟ (۰ = نه)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (unit != RepeatUnit.NONE) {
                    OutlinedButton(onClick = { pickingEnd = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (until == null) "تاریخ پایان تکرار (اختیاری)" else "تا ${PersianDates.format(until!!, zone).substringBefore(" •")}")
                    }
                    if (until != null) TextButton(onClick = { until = null }) { Text("بدون تاریخ پایان") }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (original != null) TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, null); Text("حذف یادآوری")
                }
            }
        },
        confirmButton = { Button(onClick = {
            val interval = every.toIntOrNull() ?: 0
            val day = monthDay.toIntOrNull() ?: 0
            val notice = lead.toIntOrNull() ?: 0
            error = when {
                title.isBlank() -> "عنوان یادآوری را بنویسید"
                dateTime <= System.currentTimeMillis() && original == null -> "زمان اولین یادآوری باید در آینده باشد"
                unit != RepeatUnit.NONE && interval !in 1..3650 -> "فاصلهٔ تکرار باید بین ۱ تا ۳۶۵۰ باشد"
                unit in listOf(RepeatUnit.MONTHS, RepeatUnit.YEARS) && day !in 1..31 -> "روز ماه باید بین ۱ تا ۳۱ باشد"
                unit == RepeatUnit.WEEKS && weekdays == 0 -> "دست‌کم یک روز هفته را انتخاب کنید"
                notice !in 0..525600 -> "زمان اعلان زودتر معتبر نیست"
                until != null && until!! < dateTime -> "تاریخ پایان قبل از شروع است"
                else -> ""
            }
            if (error.isNotBlank()) return@Button
            val start = if (unit == RepeatUnit.WEEKS) {
                val firstDay = time.dayOfWeek.value
                val offset = (0..6).first { shift -> weekdays and (1 shl ((firstDay + shift - 1) % 7)) != 0 }
                time.plusDays(offset.toLong()).toInstant().toEpochMilli()
            } else if (unit == RepeatUnit.MONTHS || unit == RepeatUnit.YEARS) {
                val p = PersianDates.fromMillis(dateTime, zone)
                var y = p.year; var m = p.month
                if (p.day > day) {
                    m++; if (m > 12) { m = 1; y++ }
                }
                PersianDates.at(y, m, day.coerceAtMost(PersianDates.monthLength(y, m, zone)), time.hour, time.minute, zone)
            } else dateTime
            onSave(Reminder(id = original?.id ?: 0, title = title.trim(), note = note.trim(), firstAt = start,
                nextAt = start, unit = unit, every = interval.coerceAtLeast(1), weekdays = weekdays,
                monthDay = if (unit in listOf(RepeatUnit.MONTHS, RepeatUnit.YEARS)) day else 0,
                leadMinutes = notice, untilAt = until, zone = zone.id))
        }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })

    if (pickingDate || pickingEnd) {
        var selected by remember(pickingDate, pickingEnd) { mutableStateOf(if (pickingEnd) (until?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: localDate) else localDate) }
        AlertDialog(onDismissRequest = { pickingDate = false; pickingEnd = false },
            title = { Text(if (pickingEnd) "پایان تکرار" else "انتخاب تاریخ شمسی") },
            text = { PersianMonthGrid(selected, { selected = it }) },
            confirmButton = { TextButton(onClick = {
                if (pickingEnd) until = selected.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                else dateTime = selected.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
                pickingDate = false; pickingEnd = false
            }) { Text("انتخاب") } },
            dismissButton = { TextButton(onClick = { pickingDate = false; pickingEnd = false }) { Text("انصراف") } })
    }
    if (confirmDelete && original != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("حذف شود؟") }, text = { Text("این یادآوری و اعلان‌های آینده‌اش حذف می‌شود.") },
        confirmButton = { TextButton(onClick = { onDelete(original) }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("بی‌خیال") } })
}

private fun repeatUnitLabel(unit: RepeatUnit) = when (unit) {
    RepeatUnit.NONE -> "فقط یک‌بار"; RepeatUnit.DAYS -> "هر چند روز"; RepeatUnit.WEEKS -> "هر چند هفته"
    RepeatUnit.MONTHS -> "هر چند ماه شمسی"; RepeatUnit.YEARS -> "هر چند سال شمسی"
    RepeatUnit.AFTER_DONE_DAYS -> "چند روز بعد از انجام"
}
private fun unitLabel(unit: RepeatUnit) = when (unit) {
    RepeatUnit.DAYS, RepeatUnit.AFTER_DONE_DAYS -> "روز"; RepeatUnit.WEEKS -> "هفته"
    RepeatUnit.MONTHS -> "ماه"; RepeatUnit.YEARS -> "سال"; else -> "بار"
}

@Composable
fun SettingsPage(ai: AiSettings, count: Int, onPermission: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var keyText by remember { mutableStateOf("") }
    var textModel by remember { mutableStateOf(ai.textModel) }
    var audioModel by remember { mutableStateOf(ai.audioModel) }
    var hasKey by remember { mutableStateOf(ai.key() != null) }
    var notificationsAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(ReminderStore(context).all()).toByteArray()) }
            Toast.makeText(context, "فایل پشتیبان ذخیره شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "ذخیره انجام نشد: ${e.message}", Toast.LENGTH_LONG).show() }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("فایل خوانده نشد")
            val saved = Backup.importData(json, ReminderStore(context))
            ReminderAlarms.scheduleAll(context); onRefresh()
            Toast.makeText(context, "$saved یادآوری وارد شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "بازیابی انجام نشد: ${e.message}", Toast.LENGTH_LONG).show() }
    }
    val precise = if (Build.VERSION.SDK_INT < 31) true else context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    LazyColumn(Modifier.fillMaxSize().background(Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 105.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("تنظیمات", fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item { SettingsBox("اعلان‌های گوشی") {
            Text(if (notificationsAllowed) "اجازهٔ اعلان فعال است" else "اجازهٔ اعلان غیرفعال است", color = Muted)
            if (!notificationsAllowed) Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("درخواست اجازهٔ اعلان") }
            Text(if (precise) "زمان‌بندی دقیق فعال است" else "زمان‌بندی دقیق غیرفعال است؛ اعلان ممکن است دیر برسد", color = Muted)
            if (!precise) Button(onClick = onPermission) { Text("فعال کردن دسترسی آلارم") }
        } }
        item { SettingsBox("هوش مصنوعی • اختیاری") {
            Text(if (hasKey) "کلید OpenRouter روی همین گوشی ذخیره شده" else "برای ورودی صوتی و جمله‌ای، کلید خودت را وارد کن", color = Muted)
            OutlinedTextField(keyText, { keyText = it }, label = { Text("کلید OpenRouter") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(textModel, { textModel = it }, label = { Text("مدل تحلیل متن") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(audioModel, { audioModel = it }, label = { Text("مدل تبدیل صدا به متن") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (keyText.isNotBlank()) { ai.saveKey(keyText); keyText = ""; hasKey = true }
                    ai.textModel = textModel; ai.audioModel = audioModel
                    Toast.makeText(context, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
                }) { Text("ذخیره") }
                if (hasKey) TextButton(onClick = { ai.saveKey(""); hasKey = false }) { Text("حذف کلید") }
            }
            Text("برای تحلیل، متن یا ویس به سرویس انتخابی ارسال می‌شود. هزینه تابع مدل و مصرف حساب شماست.", color = Muted, fontSize = 12.sp)
        } }
        item { SettingsBox("پشتیبان‌گیری • $count یادآوری") {
            Text("فایل JSON را در جای امن نگه دار. کلید هوش مصنوعی در آن نیست.", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportPicker.launch("yadar-backup.json") }) { Text("خروجی") }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "text/plain")) }) { Text("بازیابی") }
            }
        } }
        item { SettingsBox("اتصال تلگرام") {
            Text("اتصال حساب و ربات به سرویس همگام‌سازی نیاز دارد. این نسخهٔ آفلاین هنوز اتصال تلگرام ندارد.", color = Muted)
        } }
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
            put("monthDay", r.monthDay); put("leadMinutes", r.leadMinutes); put("untilAt", r.untilAt ?: JSONObject.NULL)
            put("zone", r.zone); put("done", r.done); put("lastCompletedAt", r.lastCompletedAt)
        }) }
    }).toString(2)
    fun importData(data: String, store: ReminderStore): Int {
        val root = JSONObject(data)
        require(root.getInt("schema") == 1) { "نسخهٔ فایل پشتیبان پشتیبانی نمی‌شود" }
        val array = root.getJSONArray("reminders")
        require(array.length() <= 10000) { "فایل بیش از حد بزرگ است" }
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
                zone = zone, done = v.optBoolean("done"), lastCompletedAt = v.optLong("lastCompletedAt")))
            added++
        }
        return added
    }
}
