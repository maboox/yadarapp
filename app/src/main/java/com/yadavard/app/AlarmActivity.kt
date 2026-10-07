package com.yadavard.app

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*

/** Full-screen alert shown over the lock screen for reminders with the "alarm" style. */
class AlarmActivity : ComponentActivity() {
    private var reminderId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        AppDisplay.load(this)
        reminderId = intent.getLongExtra(Notifier.EXTRA_ID, 0)
        val reminder = Repo.get(this, reminderId)
        if (reminder == null || reminder.done || reminder.pendingAt == 0L) { finish(); return }
        // Close automatically when the reminder is handled from the notification instead.
        lifecycleScope.launch {
            Repo.changes.collect {
                val current = Repo.get(this@AlarmActivity, reminderId)
                if (current == null || current.done || current.pendingAt == 0L || current.snoozeAt > 0) finish()
            }
        }
        setContent {
            YadarTheme(darkOverride = true) {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    AlarmScreen(reminder, Prefs.snoozeMinutes(this),
                        onDone = { Repo.complete(this, reminderId); finish() },
                        onSnooze = { minutes -> Repo.snooze(this, reminderId, minutes); finish() },
                        onClose = {
                            // Stop ringing but keep the reminder visible as pending.
                            Repo.get(this, reminderId)?.let { Notifier.showAlert(this, it, it.pendingAt, ring = true, quiet = true) }
                            finish()
                        })
                }
            }
        }
    }
}

@Composable
private fun AlarmScreen(r: Reminder, defaultSnooze: Int, onDone: () -> Unit, onSnooze: (Int) -> Unit, onClose: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    val color = r.category.color()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1446), Color(0xFF3B2DB0), Color(0xFF5B4BDB))))) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(132.dp).scale(pulse).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(Dates.formatTime(r.pendingAt, r.zoneId, AppDisplay.language), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Text(Dates.formatDate(Dates.localDate(r.pendingAt, r.zoneId), AppDisplay.calendar, AppDisplay.language, withWeekday = true),
                color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
            Spacer(Modifier.height(30.dp))
            Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.25f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(r.category.icon(), null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(r.category.label(), color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(r.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 38.sp)
            if (r.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(r.note, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 4)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 30, 60).forEach { m ->
                    OutlinedButton(onClick = { onSnooze(m) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(if (m == 60) t("۱ ساعت", "1 h") else t("${n(m)} دقیقه", "$m min"), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3B2DB0))) {
                Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp))
                Text(t("انجام شد", "Done"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                TextButton(onClick = { onSnooze(defaultSnooze) }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Rounded.Snooze, null); Spacer(Modifier.width(6.dp)); Text(t("تعویق", "Snooze"))
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onClose, colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))) {
                    Text(t("قطع زنگ", "Silence"))
                }
            }
        }
    }
}
