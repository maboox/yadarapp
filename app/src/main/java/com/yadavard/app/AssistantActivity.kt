package com.yadavard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Transparent screen that shows only the assistant on top of the home screen. Opened by the 1×1 voice widget,
 * the quick widget's mic button and the app-icon shortcut, so the user never has to open the full app.
 */
class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDisplay.load(this)
        setContent {
            YadarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) { Host() }
            }
        }
    }

    private fun openEditor(original: Reminder?, draft: Reminder) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = WidgetCommands.EDIT_DRAFT
            putExtra(WidgetCommands.EXTRA_DRAFT, Backup.toJson(draft).toString())
            putExtra(WidgetCommands.EXTRA_ID, original?.id ?: 0L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }

    /** Offline path: understand the text locally, save it if clear, otherwise open the form. */
    private fun handleText(text: String) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val cal = Prefs.calendar(this)
        val result = QuickParser.parse(text, now, zone, cal)
        val r = result.toReminder(zone, cal, now)?.let {
            it.copy(alertStyle = if (it.alertStyle == AlertStyle.ALARM) AlertStyle.ALARM else Prefs.defaultAlert(this),
                leadMinutes = Prefs.defaultLead(this))
        }
        if (r != null && result.understood && r.nextAt > now && result.title.isNotBlank()) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { Repo.save(this@AssistantActivity, r) }
                Toast.makeText(this@AssistantActivity, t("ثبت شد: ", "Saved: ") + r.title + " • " +
                    Dates.formatDateTime(r.nextAt, zone, cal, AppDisplay.language, withYear = false), Toast.LENGTH_LONG).show()
                finish()
            }
        } else openEditor(null, r ?: Reminder(title = result.title, firstAt = Dates.at(LocalDate.now(zone).plusDays(1), 9, 0, zone),
            zone = zone.id, calendar = cal, alertStyle = Prefs.defaultAlert(this)))
    }

    @Composable
    private fun Host() {
        val hasKey = remember { AiSettings(this).hasKey() }
        var open by remember { mutableStateOf(false) }
        val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text.isNullOrBlank()) finish() else handleText(text)
        }
        fun google() {
            try { speech.launch(speechIntent(this)) } catch (_: Exception) {
                Toast.makeText(this, t("تشخیص گفتار در دسترس نیست. در تنظیمات یادار کلید OpenRouter را وارد کن.",
                    "Speech recognition is unavailable. Add an OpenRouter key in Yadar settings."), Toast.LENGTH_LONG).show()
                finish()
            }
        }
        val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) open = true else { Toast.makeText(this, t("اجازهٔ میکروفون لازم است.", "Microphone permission is needed."), Toast.LENGTH_LONG).show(); finish() }
        }
        LaunchedEffect(Unit) {
            when {
                !hasKey -> google()
                ContextCompat.checkSelfPermission(this@AssistantActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> open = true
                else -> mic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (open) AssistantDialog(initialText = null,
            onDismiss = { finish() },
            onApply = { list ->
                open = false
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { Assistant.apply(this@AssistantActivity, list) }
                    Toast.makeText(this@AssistantActivity, Assistant.summary(list), Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            onEdit = { action, others ->
                open = false
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { Assistant.apply(this@AssistantActivity, others) }
                    when (action) {
                        is AssistantAction.Update -> openEditor(action.before, action.after)
                        is AssistantAction.Create -> openEditor(null, action.draft)
                        else -> finish()
                    }
                }
            },
            onText = { text -> open = false; handleText(text) },
            onGoogle = { open = false; google() })
    }
}
