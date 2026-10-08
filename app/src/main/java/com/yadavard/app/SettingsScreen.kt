@file:OptIn(ExperimentalMaterial3Api::class)

package com.yadavard.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.media.RingtoneManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(padding: PaddingValues, count: Int, permissionTick: Int, onPermissionChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val tick = permissionTick

    var snooze by remember { mutableIntStateOf(Prefs.snoozeMinutes(context)) }
    var ring by remember { mutableIntStateOf(Prefs.ringMinutes(context)) }
    var defaultAlert by remember { mutableStateOf(Prefs.defaultAlert(context)) }
    var defaultLead by remember { mutableIntStateOf(Prefs.defaultLead(context)) }
    var reliable by remember { mutableStateOf(Prefs.reliableMode(context)) }
    var vibrate by remember { mutableStateOf(Prefs.vibrate(context)) }
    var soundTick by remember { mutableIntStateOf(0) }
    var pickingAlarmSound by remember { mutableStateOf(false) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val default = RingtoneManager.getDefaultUri(if (pickingAlarmSound) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            val value = uri?.takeIf { it != default && !RingtoneManager.isDefault(it) }?.toString()
            if (pickingAlarmSound) Prefs.setAlarmSound(context, value) else Prefs.setReminderSound(context, value)
            soundTick++
        }
    }
    fun pickSound(alarm: Boolean) {
        pickingAlarmSound = alarm
        val type = if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        soundPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (alarm) RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE else type)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(type))
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AlertSound.soundUri(context, alarm))
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, if (alarm) t("صدای زنگ هشدار", "Alarm sound") else t("صدای اعلان", "Reminder sound"))
        })
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(Repo.all(context)).toByteArray()) } }
            }
            Toast.makeText(context, if (ok.isSuccess) t("فایل پشتیبان ذخیره شد ✓", "Backup saved ✓")
                else t("ذخیره نشد: ", "Save failed: ") + ok.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("empty")
                    Backup.restore(context, json, AppDisplay.language == AppLanguage.FA)
                }
            }
            Toast.makeText(context, result.fold({ t("${n(it)} یادآوری بازیابی شد ✓", "$it reminders restored ✓") },
                { t("بازیابی نشد: ", "Restore failed: ") + it.message }), Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(t("تنظیمات", "Settings"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp)) }

        item {
            SettingsCard(t("سلامت اعلان‌ها", "Notification health"), Icons.Rounded.HealthAndSafety) {
                Text(if (allCriticalGranted(context)) t("همه‌چیز آماده است؛ یادآوری‌ها سر وقت و با صدا می‌رسند ✓", "All set — reminders arrive on time and with sound ✓")
                    else t("موارد قرمز را درست کن تا هیچ یادآوری‌ای جا نماند.", "Fix the red items so no reminder is missed."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PermissionChecklist(tick, onChanged = onPermissionChanged)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { Notifier.showTest(context) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(t("اعلان آزمایشی", "Test notification"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(onClick = {
                        Scheduler.scheduleTest(context, 10_000L, alarm = true)
                        Toast.makeText(context, t("گوشی را قفل کن؛ ۱۰ ثانیهٔ دیگر زنگ تمام‌صفحه می‌خورد.", "Lock the phone; a full-screen alarm rings in 10 seconds."),
                            Toast.LENGTH_LONG).show()
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(t("آزمون زنگ", "Test alarm"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(onClick = {
                    Scheduler.scheduleTest(context, 60_000L, alarm = false)
                    Toast.makeText(context, t("برنامه را کامل ببند؛ یک دقیقهٔ دیگر اعلان می‌رسد.", "Close the app completely; a notification arrives in one minute."),
                        Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) { Text(t("آزمون اعلان در پس‌زمینه (۱ دقیقه بعد)", "Background test (in 1 minute)")) }
            }
        }

        item {
            SettingsCard(t("صدا و لرزش", "Sound & vibration"), Icons.Rounded.MusicNote) {
                @Suppress("UNUSED_VARIABLE") val readSound = soundTick
                SoundRow(t("صدای یادآوری", "Reminder sound"), AlertSound.soundName(context, alarm = false),
                    onPick = { pickSound(false) }, onPreview = { AlertSound.playOnce(context, alarm = false) })
                SoundRow(t("صدای زنگ تمام‌صفحه", "Alarm sound"), AlertSound.soundName(context, alarm = true),
                    onPick = { pickSound(true) }, onPreview = { AlertSound.playOnce(context, alarm = true) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("لرزش", "Vibration"), Modifier.weight(1f))
                    Switch(vibrate, { vibrate = it; Prefs.setVibrate(context, it) })
                }
                Text(t("صدای یادآوری‌های معمولی از بلندی «اعلان» و زنگ تمام‌صفحه از بلندی «زنگ هشدار» گوشی پخش می‌شود. زنگ تمام‌صفحه در حالت بی‌صدا هم پخش می‌شود.",
                    "Regular reminders use the notification volume; alarms use the alarm volume and ring even in silent mode."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsCard(t("ظاهر و زبان", "Appearance"), Icons.Rounded.Palette) {
                Label(t("زبان", "Language"))
                Segmented(listOf(AppLanguage.FA to "فارسی", AppLanguage.EN to "English"), AppDisplay.language) {
                    AppDisplay.language = it; Prefs.setLanguage(context, it)
                    Notifier.ensureChannels(context); WidgetUpdater.update(context)
                }
                Label(t("تقویم", "Calendar"))
                Segmented(listOf(CalendarSystem.PERSIAN to t("شمسی", "Persian"), CalendarSystem.GREGORIAN to t("میلادی", "Gregorian")), AppDisplay.calendar) {
                    AppDisplay.calendar = it; Prefs.setCalendar(context, it); WidgetUpdater.update(context)
                }
                Label(t("پوسته", "Theme"))
                Segmented(listOf(ThemeMode.SYSTEM to t("خودکار", "System"), ThemeMode.LIGHT to t("روشن", "Light"), ThemeMode.DARK to t("تیره", "Dark")),
                    AppDisplay.theme) { AppDisplay.theme = it; Prefs.setTheme(context, it) }
            }
        }

        item {
            SettingsCard(t("پیش‌فرض‌های یادآوری", "Reminder defaults"), Icons.Rounded.Tune) {
                Label(t("مدت تعویق", "Snooze length"))
                ChoiceChips(listOf(5, 10, 15, 30, 60), snooze, ::leadLabel, { snooze = it; Prefs.setSnoozeMinutes(context, it) })
                Label(t("نحوهٔ یادآوری پیش‌فرض", "Default alert"))
                Segmented(listOf(AlertStyle.NOTIFICATION to t("اعلان", "Notification"), AlertStyle.ALARM to t("زنگ تمام‌صفحه", "Alarm")), defaultAlert) {
                    defaultAlert = it; Prefs.setDefaultAlert(context, it)
                }
                Label(t("یادآوری زودتر پیش‌فرض", "Default advance notice"))
                ChoiceChips(listOf(0, 5, 15, 30, 60), defaultLead, ::leadLabel, { defaultLead = it; Prefs.setDefaultLead(context, it) })
                Label(t("مدت زنگ هشدار", "Alarm ring duration"))
                ChoiceChips(listOf(1, 3, 5, 10), ring, ::leadLabel, { ring = it; Prefs.setRingMinutes(context, it) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("حالت حداکثر دقت", "Maximum reliability"))
                        Text(t("زمان‌بندی مانند ساعت زنگ‌دار تا سیستم یادآوری را عقب نیندازد (آیکن ساعت در نوار وضعیت دیده می‌شود).",
                            "Schedules like an alarm clock so Android never delays it (shows an alarm icon in the status bar)."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(reliable, {
                        reliable = it; Prefs.setReliableMode(context, it)
                        scope.launch(Dispatchers.IO) { Scheduler.rescheduleAll(context) }
                    })
                }
            }
        }

        item { AiCard() }

        item {
            SettingsCard(t("پشتیبان‌گیری", "Backup"), Icons.Rounded.Backup) {
                Text(t("${n(count)} یادآوری روی این گوشی ذخیره است. نسخه‌های جدید را روی همین نصب کن تا اطلاعات بماند. یادار بعد از هر تغییر یک نسخهٔ پشتیبان خودکار در پوشهٔ Download/Yadar می‌گذارد که با حذف برنامه هم پاک نمی‌شود؛ با «بازیابی» برش گردان. کلید هوش مصنوعی داخل پشتیبان نیست.",
                    "$count reminders on this phone. Install new versions over this one to keep your data. Yadar also saves an automatic backup to Download/Yadar after every change, which survives uninstalling; bring it back with Restore. Your AI key is not included."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { exportPicker.launch("yadar-backup.json") }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("گرفتن نسخه", "Export"))
                    }
                    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("بازیابی", "Restore"))
                    }
                }
            }
        }

        item {
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            Text(t("یادار نسخهٔ ${n(version)} • همهٔ داده‌ها فقط روی گوشی خودت", "Yadar $version • all data stays on your phone"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}

@Composable
private fun AiCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { AiSettings(context) }
    var hasKey by remember { mutableStateOf(ai.hasKey()) }
    var keyText by remember { mutableStateOf("") }
    var useAi by remember { mutableStateOf(Prefs.useAi(context)) }
    var model by remember { mutableStateOf(ai.model) }
    var audioModel by remember { mutableStateOf(ai.audioModel) }
    var pickingAudio by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<OpenRouter.Model>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }

    SettingsCard(t("دستیار هوشمند (اختیاری)", "Smart assistant (optional)"), Icons.Rounded.AutoAwesome) {
        Text(t("ثبت سریع بدون اینترنت هم جمله‌های فارسی و انگلیسی را می‌فهمد. با کلید OpenRouter، دستیار یادار فعال می‌شود: با صدا یا متن یادآوری بساز، ویرایش کن، حذف کن، «انجام شد» بزن یا بپرس «این هفته چی دارم؟».",
            "Quick add works offline. With an OpenRouter key the Yadar assistant can create, edit, delete and complete reminders by voice or text, and answer questions like “what do I have this week?”."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("دستیار هوشمند برای متن ثبت سریع", "Use the assistant for typed quick add"), Modifier.weight(1f))
            Switch(useAi, { useAi = it; Prefs.setUseAi(context, it) }, enabled = hasKey)
        }
        OutlinedTextField(keyText, { keyText = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (hasKey) t("کلید ذخیره شده • برای تغییر وارد کن", "Key saved • type to replace") else t("کلید OpenRouter", "OpenRouter key")) },
            visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (keyText.isNotBlank()) { ai.saveKey(keyText); keyText = ""; hasKey = true; useAi = true; Prefs.setUseAi(context, true) }
                Toast.makeText(context, t("ذخیره شد", "Saved"), Toast.LENGTH_SHORT).show()
            }) { Text(t("ذخیرهٔ کلید", "Save key")) }
            if (hasKey) TextButton(onClick = { ai.saveKey(""); hasKey = false; useAi = false; Prefs.setUseAi(context, false) }) { Text(t("حذف کلید", "Remove key")) }
        }
        if (hasKey) {
            var testing by remember { mutableStateOf(false) }
            var testResult by remember { mutableStateOf("") }
            OutlinedButton(onClick = {
                testing = true; testResult = ""
                scope.launch {
                    testResult = try { OpenRouter(context).testKey() } catch (e: Exception) { "✗ " + (e.message ?: "") }
                    testing = false
                }
            }, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(t("آزمایش کلید", "Test key"))
            }
            if (testResult.isNotBlank()) Text(testResult, style = MaterialTheme.typography.bodySmall,
                color = if (testResult.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        }
        fun load(audio: Boolean) {
            pickingAudio = audio; picking = true; loading = true; models = emptyList(); search = ""
            scope.launch {
                try { models = OpenRouter(context).models(transcription = audio) }
                catch (e: Exception) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show(); picking = false }
                finally { loading = false }
            }
        }
        OutlinedButton(onClick = { load(false) }, enabled = hasKey, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(t("مدل فهم متن", "Text model"))
                Text(model, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(onClick = { load(true) }, enabled = hasKey, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(t("مدل تبدیل صدا", "Voice model"))
                Text(audioModel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (picking) AlertDialog(onDismissRequest = { picking = false }, title = { Text(t("انتخاب مدل", "Choose model")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(t("جست‌وجو", "Search")) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(freeOnly, { freeOnly = it }); Text(t("فقط رایگان", "Free only"))
                }
                if (pickingAudio) {
                    Text(t("پیشنهادی برای فارسی:", "Recommended:"), style = MaterialTheme.typography.labelLarge)
                    RECOMMENDED_AUDIO_MODELS.forEach { id ->
                        FilterChip(audioModel == id, { audioModel = id; ai.audioModel = id; picking = false }, { Text(id) })
                    }
                    HorizontalDivider()
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val shown = models.filter { (!freeOnly || it.free) && (search.isBlank() || it.name.contains(search, true) || it.id.contains(search, true)) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { m ->
                        ListItem(headlineContent = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(m.id + if (m.free) t(" • رایگان", " • free") else "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { if (m.id == (if (pickingAudio) audioModel else model)) Icon(Icons.Rounded.Check, null) },
                            modifier = Modifier.clickable {
                                if (pickingAudio) { audioModel = m.id; ai.audioModel = m.id } else { model = m.id; ai.model = m.id }
                                picking = false
                            })
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text(t("بستن", "Close")) } })
}

@Composable
private fun SoundRow(label: String, value: String, onPick: () -> Unit, onPreview: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).clickable(onClick = onPick).padding(vertical = 4.dp)) {
            Text(label)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPreview) { Icon(Icons.Rounded.PlayArrow, t("پخش", "Play")) }
        TextButton(onClick = onPick) { Text(t("تغییر", "Change")) }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected == value, { onSelect(value) }, SegmentedButtonDefaults.itemShape(i, options.size)) {
                Text(label, maxLines = 1)
            }
        }
    }
}

