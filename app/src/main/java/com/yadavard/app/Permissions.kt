package com.yadavard.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object Phone {
    val isXiaomi: Boolean get() = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

    fun open(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) { }
        }
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** MIUI's own permission page (lock screen, pop-ups in background, autostart). */
    fun miuiPermissions(context: Context) = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        putExtra("extra_pkgname", context.packageName)
    }

    fun miuiAutostart() = Intent().setComponent(ComponentName("com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity"))
}

data class PermItem(val title: String, val hint: String, val ok: Boolean?, val action: String, val fix: () -> Unit)

/**
 * Everything a reminder needs to arrive on time and be heard. [ok] = null means "cannot be detected,
 * please check manually" (for example MIUI-only switches).
 */
@Composable
fun PermissionChecklist(tick: Int, onChanged: () -> Unit, compact: Boolean = false) {
    val context = LocalContext.current
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    @Suppress("UNUSED_VARIABLE") val read = tick
    val fix = t("فعال کن", "Fix")
    val items = buildList {
        add(PermItem(t("اجازهٔ اعلان", "Notifications"), t("بدون این، هیچ یادآوری نمایش داده نمی‌شود.", "Without it no reminder can be shown."),
            Notifier.allowed(context), fix) {
            if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Phone.open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        })
        add(PermItem(t("آلارم دقیق", "Exact alarms"), t("یادآوری دقیقاً سر ساعت می‌رسد.", "Reminders fire exactly on time."),
            Scheduler.canExact(context), fix) {
            if (Build.VERSION.SDK_INT >= 31) Phone.open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        })
        add(PermItem(t("زنگ تمام‌صفحه", "Full-screen alarms"), t("زنگ روی صفحهٔ قفل هم نمایش داده می‌شود.", "Alarms appear even on the lock screen."),
            Notifier.canFullScreen(context), fix) {
            if (Build.VERSION.SDK_INT >= 34) Phone.open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        })
        add(PermItem(t("بدون محدودیت باتری", "No battery restriction"), t("سیستم برنامه را در پس‌زمینه نمی‌بندد.", "The system won't stop the app in the background."),
            Notifier.ignoringBattery(context), fix) {
            @SuppressLint("BatteryLife")
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            Phone.open(context, request)
        })
        add(PermItem(t("صدای زنگ هشدار", "Alarm volume"), t("صدای «زنگ هشدار» گوشی صفر نباشد.", "The phone's alarm volume must not be zero."),
            AlertSound.volumeOk(context, alarm = true), fix) { Phone.open(context, Intent(Settings.ACTION_SOUND_SETTINGS)) })
        add(PermItem(t("صدای اعلان", "Notification volume"), t("صدای اعلان گوشی صفر نباشد.", "The phone's notification volume must not be zero."),
            AlertSound.volumeOk(context, alarm = false), fix) { Phone.open(context, Intent(Settings.ACTION_SOUND_SETTINGS)) })
        if (Phone.isXiaomi) {
            add(PermItem(t("اجرای خودکار (شیائومی)", "Autostart (Xiaomi)"), t("در فهرست، «یادار» را روشن کن.", "Turn on Yadar in the list."),
                null, t("باز کن", "Open")) { Phone.open(context, Phone.miuiAutostart(), Phone.appDetails(context)) })
            add(PermItem(t("نمایش روی صفحهٔ قفل و پنجرهٔ بازشو (شیائومی)", "Lock screen & pop-ups (Xiaomi)"),
                t("«نمایش روی صفحهٔ قفل» و «نمایش پنجرهٔ بازشو در پس‌زمینه» را مجاز کن.", "Allow “Show on Lock screen” and “Display pop-up windows while running in the background”."),
                null, t("باز کن", "Open")) { Phone.open(context, Phone.miuiPermissions(context), Phone.appDetails(context)) })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(when (item.ok) { true -> Icons.Rounded.CheckCircle; false -> Icons.Rounded.Cancel; null -> Icons.Rounded.Info }, null,
                    tint = when (item.ok) { true -> MaterialTheme.colorScheme.secondary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.tertiary })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    if (item.ok != true) Text(item.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.ok != true) {
                    Spacer(Modifier.width(6.dp))
                    FilledTonalButton(onClick = item.fix, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(item.action) }
                }
            }
        }
    }
}

fun allCriticalGranted(context: Context): Boolean = Notifier.allowed(context) && Scheduler.canExact(context) &&
    Notifier.canFullScreen(context) && Notifier.ignoringBattery(context) &&
    AlertSound.volumeOk(context, true) && AlertSound.volumeOk(context, false)
