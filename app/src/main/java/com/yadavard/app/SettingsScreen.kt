@file:OptIn(ExperimentalMaterial3Api::class)

package com.yadavard.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
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
    val notifications = Notifier.allowed(context)
    val exact = Scheduler.canExact(context)
    val fullScreen = Notifier.canFullScreen(context)
    val battery = Notifier.ignoringBattery(context)
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionChanged() }

    fun open(intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    val appNotificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    var snooze by remember { mutableIntStateOf(Prefs.snoozeMinutes(context)) }
    var ring by remember { mutableIntStateOf(Prefs.ringMinutes(context)) }
    var defaultAlert by remember { mutableStateOf(Prefs.defaultAlert(context)) }
    var defaultLead by remember { mutableIntStateOf(Prefs.defaultLead(context)) }
    var reliable by remember { mutableStateOf(Prefs.reliableMode(context)) }

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
                val allGood = notifications && exact && fullScreen && battery
                Text(if (allGood) t("همه‌چیز آماده است؛ یادآوری‌ها سر وقت می‌رسند ✓", "All set — reminders will arrive on time ✓")
                    else t("موارد قرمز را درست کن تا هیچ یادآوری‌ای جا نماند.", "Fix the red items so no reminder is missed."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HealthRow(t("اجازهٔ اعلان", "Notification permission"), notifications, t("فعال کن", "Allow")) {
                    if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else open(appNotificationSettings)
                }
                HealthRow(t("آلارم دقیق", "Exact alarms"), exact, t("فعال کن", "Allow")) {
                    if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
                HealthRow(t("زنگ تمام‌صفحه", "Full-screen alarms"), fullScreen, t("فعال کن", "Allow")) {
                    if (Build.VERSION.SDK_INT >= 34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }
                HealthRow(t("بدون محدودیت باتری", "Unrestricted battery"), battery, t("فعال کن", "Allow")) {
                    @SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    open(intent)
                }
                Text(t("در گوشی‌های شیائومی، سامسونگ، هواوی و… در تنظیمات برنامه «اجرای خودکار / Autostart» را هم روشن کن.",
                    "On Xiaomi, Samsung, Huawei, etc. also enable “Autostart” for this app in system settings."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { Notifier.showTest(context) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(t("اعلان آزمایشی", "Test now"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = {
                        Scheduler.scheduleTest(context, 60_000L)
                        Toast.makeText(context, t("برنامه را ببند؛ یک دقیقهٔ دیگر اعلان می‌رسد.", "Close the app; a notification arrives in one minute."),
                            Toast.LENGTH_LONG).show()
                    }, modifier = Modifier.weight(1f)) { Text(t("آزمون ۱ دقیقه بعد", "Test in 1 min"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                TextButton(onClick = { open(appNotificationSettings) }) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(t("صدا و لرزش اعلان‌ها", "Notification sound & vibration"))
                }
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
                Text(t("${n(count)} یادآوری روی این گوشی ذخیره است. فایل پشتیبان را جای امن نگه دار؛ کلید هوش مصنوعی داخل آن نیست.",
                    "$count reminders on this phone. Keep the backup file safe; your AI key is not included."),
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
    var models by remember { mutableStateOf<List<OpenRouter.Model>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }

    SettingsCard(t("هوش مصنوعی (اختیاری)", "AI (optional)"), Icons.Rounded.AutoAwesome) {
        Text(t("ثبت سریع بدون اینترنت هم جمله‌های فارسی و انگلیسی را می‌فهمد. با کلید OpenRouter می‌توانی جمله‌های پیچیده‌تر را هم با هوش مصنوعی تحلیل کنی.",
            "Quick add understands Persian and English offline. Add an OpenRouter key to parse complex sentences with AI."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("استفاده از هوش مصنوعی در ثبت سریع", "Use AI for quick add"), Modifier.weight(1f))
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
        OutlinedButton(onClick = {
            picking = true; loading = true
            scope.launch {
                try { models = OpenRouter(context).models() }
                catch (e: Exception) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show(); picking = false }
                finally { loading = false }
            }
        }, enabled = hasKey, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(t("مدل", "Model"))
                Text(model, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val shown = models.filter { (!freeOnly || it.free) && (search.isBlank() || it.name.contains(search, true) || it.id.contains(search, true)) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { m ->
                        ListItem(headlineContent = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(m.id + if (m.free) t(" • رایگان", " • free") else "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { if (m.id == model) Icon(Icons.Rounded.Check, null) },
                            modifier = Modifier.clickable { model = m.id; ai.model = m.id; picking = false })
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text(t("بستن", "Close")) } })
}

@Composable
private fun HealthRow(label: String, ok: Boolean, fix: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null,
            tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f))
        if (!ok) FilledTonalButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(fix) }
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

